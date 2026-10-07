package com.streamx.media.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.common.security.JwtUtils;
import com.streamx.media.config.MediaProperties;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.domain.UploadSession;
import com.streamx.media.domain.UploadSessionStatus;
import com.streamx.media.dto.ImportRequest;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.dto.MediaStatusResponse;
import com.streamx.media.dto.PreviewResponse;
import com.streamx.media.exception.ConflictException;
import com.streamx.media.exception.StorageUnavailableException;
import com.streamx.media.hls.HlsTranscoderService;
import com.streamx.media.imports.SsrfGuard;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.repository.UploadSessionRepository;
import com.streamx.media.storage.ObjectKeys;
import com.streamx.media.storage.ObjectStat;
import com.streamx.media.storage.ObjectStorage;
import com.streamx.media.storage.StorageReadyEvent;
import com.streamx.media.upload.Filenames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final Pattern HLS_FILENAME = Pattern.compile("^[A-Za-z0-9_.-]+\\.(m3u8|ts)$");
    static final Duration PREVIEW_TTL = Duration.ofHours(2);

    public record HlsObject(String key, ObjectStat stat) {
    }

    private final MediaAssetRepository repository;
    private final UploadSessionRepository sessionRepository;
    private final ObjectStorage storage;
    private final HlsTranscoderService transcoder;
    private final MediaIngestService ingest;
    private final SsrfGuard ssrfGuard;
    private final JwtUtils jwtUtils;
    private final MediaProperties properties;

    public MediaService(MediaAssetRepository repository,
                        UploadSessionRepository sessionRepository,
                        ObjectStorage storage,
                        HlsTranscoderService transcoder,
                        MediaIngestService ingest,
                        SsrfGuard ssrfGuard,
                        JwtUtils jwtUtils,
                        MediaProperties properties) {
        this.repository = repository;
        this.sessionRepository = sessionRepository;
        this.storage = storage;
        this.transcoder = transcoder;
        this.ingest = ingest;
        this.ssrfGuard = ssrfGuard;
        this.jwtUtils = jwtUtils;
        this.properties = properties;
    }

    // ---------------------------------------------------------------- ingest entry points

    /** Called by the upload service inside its transaction once all parts of a VIDEO session are verified. */
    @Transactional
    public MediaAsset startVideoFromUpload(UploadSession session) {
        MediaAsset asset = repository.findByContentId(session.getContentId()).orElseGet(MediaAsset::new);
        if (asset.getId() != null && AssetStatusUpdater.isBusy(asset)) {
            throw new ConflictException("A video for this title is already being processed");
        }
        session.setStatus(UploadSessionStatus.COMPLETED);
        sessionRepository.save(session);

        asset.setContentId(session.getContentId());
        asset.setOriginalFilename(session.getFilename());
        asset.setStoragePath(ObjectKeys.original(session.getContentId(), session.getFilename()));
        asset.setFileSizeBytes(session.getSizeBytes());
        asset.setSourceUrl(null);
        asset.setFailureReason(null);
        asset.setProgressPercent(null);
        asset.setPendingUploadId(session.getId());
        asset.setStatus(MediaProcessingStatus.PROCESSING);
        MediaAsset saved = repository.save(asset);
        UUID assetId = saved.getId();
        AfterCommit.run(() -> ingest.assembleUpload(assetId));
        log.info("Upload {} completed for contentId {}; assembling {} parts", session.getId(),
                session.getContentId(), session.getTotalParts());
        return saved;
    }

    @Transactional
    public MediaAssetResponse startImport(ImportRequest request) {
        UUID contentId = parseContentId(request.contentId());
        String url = ssrfGuard.checkUrl(request.url()).toString();
        requireStorage();

        MediaAsset asset = repository.findByContentId(contentId).orElseGet(MediaAsset::new);
        if (asset.getId() != null && AssetStatusUpdater.isBusy(asset)) {
            throw new ConflictException("A video for this title is already being processed");
        }
        asset.setContentId(contentId);
        asset.setSourceUrl(url);
        asset.setOriginalFilename(Filenames.sanitize(lastPathSegment(url), "video.mp4"));
        asset.setStoragePath(null);
        asset.setFileSizeBytes(null);
        asset.setFailureReason(null);
        asset.setProgressPercent(null);
        asset.setPendingUploadId(null);
        asset.setStatus(MediaProcessingStatus.UPLOADING);
        MediaAsset saved = repository.save(asset);
        UUID assetId = saved.getId();
        AfterCommit.run(() -> ingest.importFromLink(assetId));
        log.info("Queued import of {} for contentId {}", url, contentId);
        return mapToResponse(saved);
    }

    @Transactional
    public MediaAssetResponse retryTranscode(UUID assetId) {
        MediaAsset asset = repository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found: " + assetId));
        if (AssetStatusUpdater.isBusy(asset)) {
            throw new ConflictException("Asset is already being processed");
        }
        requireStorage();
        asset.setFailureReason(null);
        asset.setProgressPercent(null);

        boolean originalStored = asset.getStoragePath() != null && storage.stat(asset.getStoragePath()).isPresent();
        Runnable job;
        if (asset.getPendingUploadId() != null) {
            asset.setStatus(MediaProcessingStatus.PROCESSING);
            job = () -> ingest.assembleUpload(assetId);
        } else if (originalStored) {
            asset.setStatus(MediaProcessingStatus.PROCESSING);
            job = () -> transcoder.transcode(assetId);
        } else if (asset.getSourceUrl() != null) {
            asset.setStatus(MediaProcessingStatus.UPLOADING);
            job = () -> ingest.importFromLink(assetId);
        } else {
            throw new BadRequestException("Source file is no longer available; upload the video again");
        }
        MediaAsset saved = repository.save(asset);
        AfterCommit.run(job);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteAsset(UUID assetId) {
        MediaAsset asset = repository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found: " + assetId));
        if (AssetStatusUpdater.isBusy(asset)) {
            throw new ConflictException("The video is still being processed; wait for it to finish before deleting it");
        }
        requireStorage();
        storage.deletePrefix(ObjectKeys.originalsPrefix(asset.getContentId()));
        storage.deletePrefix(ObjectKeys.hlsPrefix(asset.getContentId()));
        if (asset.getPendingUploadId() != null) {
            storage.deletePrefix(ObjectKeys.uploadPrefix(asset.getPendingUploadId()));
        }
        repository.delete(asset);
        log.info("Deleted media asset {} (contentId {})", assetId, asset.getContentId());
    }

    /** Re-queues work interrupted by a restart once storage is reachable again. */
    @EventListener(StorageReadyEvent.class)
    public void resumeInterruptedWork() {
        List<MediaAsset> pending = repository.findByStatusIn(
                List.of(MediaProcessingStatus.UPLOADING, MediaProcessingStatus.PROCESSING));
        for (MediaAsset asset : pending) {
            try {
                if (asset.getStatus() == MediaProcessingStatus.UPLOADING) {
                    if (asset.getSourceUrl() != null) {
                        log.info("Resuming interrupted import for contentId {}", asset.getContentId());
                        ingest.importFromLink(asset.getId());
                    } else {
                        markInterrupted(asset);
                    }
                } else if (asset.getPendingUploadId() != null) {
                    log.info("Resuming assembly of upload for contentId {}", asset.getContentId());
                    ingest.assembleUpload(asset.getId());
                } else if (asset.getStoragePath() != null && storage.stat(asset.getStoragePath()).isPresent()) {
                    log.info("Resuming interrupted transcode for contentId {}", asset.getContentId());
                    asset.setProgressPercent(null);
                    repository.save(asset);
                    transcoder.transcode(asset.getId());
                } else {
                    markInterrupted(asset);
                }
            } catch (Exception e) {
                log.error("Could not resume work for contentId {}", asset.getContentId(), e);
            }
        }
    }

    private void markInterrupted(MediaAsset asset) {
        asset.setStatus(MediaProcessingStatus.FAILED);
        asset.setProgressPercent(null);
        asset.setFailureReason("Service restarted before the video was stored; upload it again");
        repository.save(asset);
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<MediaAssetResponse> listAssets() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public MediaAssetResponse getByContentId(UUID contentId) {
        return repository.findByContentId(contentId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No video for content " + contentId));
    }

    /**
     * Playback readiness. While a replacement video is uploading, processing or has failed, the previous HLS
     * rendition (masterPlaylistUrl set) is still served, so the title stays playable.
     */
    @Transactional(readOnly = true)
    public Optional<MediaStatusResponse> getStatus(String contentIdStr) {
        UUID contentId;
        try {
            contentId = UUID.fromString(contentIdStr);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return repository.findByContentId(contentId).map(asset -> {
            boolean playable = asset.getStatus() == MediaProcessingStatus.COMPLETED || asset.getMasterPlaylistUrl() != null;
            return new MediaStatusResponse(playable ? MediaProcessingStatus.COMPLETED : asset.getStatus(),
                    asset.getDurationSeconds());
        });
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(UUID contentId, String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        MediaAsset asset = repository.findByContentId(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("No video for content " + contentId));
        if (asset.getStatus() != MediaProcessingStatus.COMPLETED) {
            throw new ConflictException("The video is not ready for preview (status " + asset.getStatus() + ")");
        }
        String token = jwtUtils.generateStreamToken(accountId, contentId.toString(), UUID.randomUUID().toString(),
                PREVIEW_TTL.toMillis());
        String path = "/api/v1/media/stream/" + token + "/" + contentId + "/master.m3u8";
        return new PreviewResponse(properties.publicUrl(path), LocalDateTime.now().plus(PREVIEW_TTL));
    }

    /** Resolves a playlist or segment of the content's HLS output; only bare HLS file names are accepted. */
    public HlsObject resolveHlsObject(String contentIdStr, String filename) {
        if (!isValidHlsFilename(filename)) {
            throw new BadRequestException("Invalid HLS file name");
        }
        UUID contentId = parseContentId(contentIdStr);
        String key = ObjectKeys.hls(contentId, filename);
        ObjectStat stat = storage.stat(key).orElseThrow(() -> new ResourceNotFoundException("HLS file not found"));
        return new HlsObject(key, stat);
    }

    public static boolean isValidHlsFilename(String filename) {
        return filename != null
                && !filename.contains("/")
                && !filename.contains("\\")
                && !filename.contains("..")
                && HLS_FILENAME.matcher(filename).matches();
    }

    public MediaAssetResponse mapToResponse(MediaAsset asset) {
        MediaAssetResponse response = new MediaAssetResponse(
                asset.getId().toString(),
                asset.getContentId().toString(),
                asset.getOriginalFilename(),
                asset.getMasterPlaylistUrl(),
                asset.getStatus(),
                asset.getDurationSeconds(),
                asset.getCreatedAt()
        );
        response.setFileSizeBytes(asset.getFileSizeBytes());
        response.setFailureReason(asset.getFailureReason());
        response.setUpdatedAt(asset.getUpdatedAt());
        response.setSourceUrl(asset.getSourceUrl());
        response.setProgressPercent(asset.getStatus() == MediaProcessingStatus.PROCESSING ? asset.getProgressPercent() : null);
        return response;
    }

    private void requireStorage() {
        if (!storage.isAvailable()) {
            throw new StorageUnavailableException("Media storage is unavailable; try again shortly");
        }
    }

    static UUID parseContentId(String contentIdStr) {
        try {
            return UUID.fromString(contentIdStr == null ? "" : contentIdStr.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid content id: " + contentIdStr);
        }
    }

    private static String lastPathSegment(String url) {
        String withoutQuery = url.split("[?#]", 2)[0];
        return withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
    }
}
