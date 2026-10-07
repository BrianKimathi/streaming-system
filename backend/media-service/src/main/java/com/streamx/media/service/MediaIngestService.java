package com.streamx.media.service;

import com.streamx.media.config.MediaProperties;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.domain.UploadSession;
import com.streamx.media.hls.HlsTranscoderService;
import com.streamx.media.imports.ImportFailedException;
import com.streamx.media.imports.VideoLinkDownloader;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.repository.UploadPartRepository;
import com.streamx.media.repository.UploadSessionRepository;
import com.streamx.media.storage.ObjectKeys;
import com.streamx.media.storage.ObjectStat;
import com.streamx.media.storage.ObjectStorage;
import com.streamx.media.storage.ScratchSpace;
import com.streamx.media.upload.Filenames;
import com.streamx.media.upload.UploadPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Gets a video's original into storage (assembling uploaded parts or downloading a link), then queues the transcode. */
@Service
public class MediaIngestService {

    private static final Logger log = LoggerFactory.getLogger(MediaIngestService.class);

    private final MediaAssetRepository assetRepository;
    private final UploadSessionRepository sessionRepository;
    private final UploadPartRepository partRepository;
    private final ObjectStorage storage;
    private final ScratchSpace scratch;
    private final VideoLinkDownloader downloader;
    private final HlsTranscoderService transcoder;
    private final AssetStatusUpdater statusUpdater;
    private final MediaProperties properties;

    public MediaIngestService(MediaAssetRepository assetRepository,
                              UploadSessionRepository sessionRepository,
                              UploadPartRepository partRepository,
                              ObjectStorage storage,
                              ScratchSpace scratch,
                              VideoLinkDownloader downloader,
                              HlsTranscoderService transcoder,
                              AssetStatusUpdater statusUpdater,
                              MediaProperties properties) {
        this.assetRepository = assetRepository;
        this.sessionRepository = sessionRepository;
        this.partRepository = partRepository;
        this.storage = storage;
        this.scratch = scratch;
        this.downloader = downloader;
        this.transcoder = transcoder;
        this.statusUpdater = statusUpdater;
        this.properties = properties;
    }

    /** Composes the upload's parts into originals/{contentId}/{filename}; safe to re-run after a restart. */
    @Async("ingestExecutor")
    public void assembleUpload(UUID assetId) {
        MediaAsset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null || asset.getStatus() != MediaProcessingStatus.PROCESSING || asset.getPendingUploadId() == null) {
            return;
        }
        UUID uploadId = asset.getPendingUploadId();
        String target = asset.getStoragePath();
        try {
            UploadSession session = sessionRepository.findById(uploadId)
                    .orElseThrow(() -> new IllegalStateException("The upload session no longer exists; upload the video again"));
            List<String> partKeys = new ArrayList<>(session.getTotalParts());
            for (int part = 1; part <= session.getTotalParts(); part++) {
                partKeys.add(ObjectKeys.uploadPart(uploadId, part));
            }
            Map<String, Long> stored = new HashMap<>();
            for (ObjectStat stat : storage.list(ObjectKeys.uploadPrefix(uploadId))) {
                stored.put(stat.key(), stat.size());
            }

            if (stored.keySet().containsAll(partKeys)) {
                storage.compose(target, partKeys, session.getContentType());
            }
            Optional<ObjectStat> assembled = storage.stat(target);
            if (assembled.isEmpty() || assembled.get().size() != session.getSizeBytes()) {
                throw new IllegalStateException("The uploaded parts are incomplete; upload the video again");
            }

            storage.deletePrefix(ObjectKeys.uploadPrefix(uploadId));
            partRepository.deleteByUploadId(uploadId);
            deleteOtherOriginals(asset.getContentId(), target);
            assetRepository.clearPendingUpload(assetId);
            log.info("Assembled {} parts into {} for contentId {}", session.getTotalParts(), target, asset.getContentId());
            transcoder.transcode(assetId);
        } catch (Exception e) {
            log.error("Could not assemble upload {} for asset {}", uploadId, assetId, e);
            statusUpdater.markFailed(assetId, "Could not assemble the uploaded video: " + e.getMessage());
        }
    }

    /** Downloads the asset's sourceUrl (status UPLOADING) into storage, then hands it to the transcoder. */
    @Async("ingestExecutor")
    public void importFromLink(UUID assetId) {
        MediaAsset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null || asset.getStatus() != MediaProcessingStatus.UPLOADING || asset.getSourceUrl() == null) {
            return;
        }
        Path workDir = null;
        try {
            workDir = scratch.freshDirectory("import", assetId);
            Path download = workDir.resolve("download");
            VideoLinkDownloader.DownloadResult result =
                    downloader.download(asset.getSourceUrl(), download, properties.maxVideoBytes());

            String filename = importedFilename(result);
            String key = ObjectKeys.original(asset.getContentId(), filename);
            storage.uploadFile(key, download, result.contentType().startsWith("video/") ? result.contentType() : null);
            deleteOtherOriginals(asset.getContentId(), key);

            MediaAsset current = assetRepository.findById(assetId).orElse(null);
            if (current == null || current.getStatus() != MediaProcessingStatus.UPLOADING) {
                return;
            }
            current.setOriginalFilename(filename);
            current.setStoragePath(key);
            current.setFileSizeBytes(result.bytes());
            current.setStatus(MediaProcessingStatus.PROCESSING);
            current.setProgressPercent(null);
            current.setFailureReason(null);
            assetRepository.save(current);
            log.info("Imported {} ({} bytes) for contentId {}", result.finalUrl(), result.bytes(), asset.getContentId());
            transcoder.transcode(assetId);
        } catch (ImportFailedException e) {
            log.warn("Import for asset {} failed: {}", assetId, e.getMessage());
            statusUpdater.markFailed(assetId, e.getMessage());
        } catch (Exception e) {
            log.error("Import for asset {} failed", assetId, e);
            statusUpdater.markFailed(assetId, "Import failed: " + e.getMessage());
        } finally {
            scratch.deleteQuietly(workDir);
        }
    }

    private void deleteOtherOriginals(UUID contentId, String keep) {
        for (ObjectStat stat : storage.list(ObjectKeys.originalsPrefix(contentId))) {
            if (!stat.key().equals(keep)) {
                storage.delete(stat.key());
            }
        }
    }

    static String importedFilename(VideoLinkDownloader.DownloadResult result) {
        String name = Filenames.sanitize(result.suggestedFilename(), "video.mp4");
        if (UploadPolicy.VIDEO_EXTENSIONS.contains(Filenames.extension(name))) {
            return name;
        }
        String extension = switch (result.contentType()) {
            case "video/webm" -> "webm";
            case "video/quicktime" -> "mov";
            case "video/x-matroska" -> "mkv";
            case "video/x-msvideo" -> "avi";
            case "video/mpeg" -> "mpg";
            case "video/mp2t" -> "ts";
            default -> "mp4";
        };
        return name + "." + extension;
    }
}
