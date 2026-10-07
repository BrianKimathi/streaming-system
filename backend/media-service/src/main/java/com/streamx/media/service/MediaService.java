package com.streamx.media.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.dto.MediaStatusResponse;
import com.streamx.media.hls.HlsTranscoderService;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp4", "mov", "mkv", "webm", "avi", "m4v", "mpg", "mpeg", "ts");
    private static final Pattern HLS_FILENAME = Pattern.compile("^[A-Za-z0-9_.-]+\\.(m3u8|ts)$");

    private final MediaAssetRepository repository;
    private final StorageService storageService;
    private final HlsTranscoderService hlsTranscoderService;

    public MediaService(MediaAssetRepository repository,
                        StorageService storageService,
                        HlsTranscoderService hlsTranscoderService) {
        this.repository = repository;
        this.storageService = storageService;
        this.hlsTranscoderService = hlsTranscoderService;
    }

    @Transactional
    public MediaAssetResponse processUpload(String contentIdStr, MultipartFile file) {
        UUID contentId = parseContentId(contentIdStr);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension) && !contentType.startsWith("video/")) {
            throw new BadRequestException("Unsupported file type; upload a video file (" + String.join(", ", ALLOWED_EXTENSIONS) + ")");
        }

        MediaAsset asset = repository.findByContentId(contentId).orElseGet(MediaAsset::new);
        if (asset.getId() != null && asset.getStatus() == MediaProcessingStatus.PROCESSING) {
            throw new BadRequestException("A video for this title is already being transcoded");
        }

        String rawDir = "raw/" + contentId;
        String rawStoragePath = rawDir + "/" + originalFilename;
        storageService.deleteRecursively(rawDir);
        try {
            storageService.storeStream(rawStoragePath, file.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }

        asset.setContentId(contentId);
        asset.setOriginalFilename(originalFilename);
        asset.setStoragePath(rawStoragePath);
        asset.setFileSizeBytes(file.getSize());
        asset.setMasterPlaylistUrl(null);
        asset.setDurationSeconds(null);
        asset.setFailureReason(null);
        asset.setStatus(MediaProcessingStatus.PROCESSING);

        MediaAsset saved = repository.save(asset);
        log.info("Stored upload {} ({} bytes) for contentId {}; queued for transcoding", originalFilename, file.getSize(), contentId);
        enqueueAfterCommit(saved.getId());
        return mapToResponse(saved);
    }

    @Transactional
    public MediaAssetResponse retryTranscode(UUID assetId) {
        MediaAsset asset = repository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found: " + assetId));
        if (asset.getStatus() == MediaProcessingStatus.PROCESSING) {
            throw new BadRequestException("Asset is already being transcoded");
        }
        if (asset.getStoragePath() == null || !storageService.exists(asset.getStoragePath())) {
            throw new BadRequestException("Source file is no longer available; upload the video again");
        }
        asset.setStatus(MediaProcessingStatus.PROCESSING);
        asset.setFailureReason(null);
        MediaAsset saved = repository.save(asset);
        enqueueAfterCommit(saved.getId());
        return mapToResponse(saved);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedTranscodes() {
        List<MediaAsset> pending = repository.findByStatusIn(
                List.of(MediaProcessingStatus.UPLOADING, MediaProcessingStatus.PROCESSING));
        for (MediaAsset asset : pending) {
            if (asset.getStoragePath() != null && storageService.exists(asset.getStoragePath())) {
                log.info("Resuming interrupted transcode for contentId {}", asset.getContentId());
                asset.setStatus(MediaProcessingStatus.PROCESSING);
                repository.save(asset);
                hlsTranscoderService.transcode(asset.getId());
            } else {
                asset.setStatus(MediaProcessingStatus.FAILED);
                asset.setFailureReason("Service restarted before the upload was stored");
                repository.save(asset);
            }
        }
    }

    @Transactional(readOnly = true)
    public MediaAssetResponse getMediaAsset(String contentIdStr) {
        UUID contentId = parseContentId(contentIdStr);
        MediaAsset asset = repository.findByContentId(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found for contentId: " + contentIdStr));
        return mapToResponse(asset);
    }

    @Transactional(readOnly = true)
    public Optional<MediaStatusResponse> getStatus(String contentIdStr) {
        UUID contentId;
        try {
            contentId = UUID.fromString(contentIdStr);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return repository.findByContentId(contentId)
                .map(asset -> new MediaStatusResponse(asset.getStatus(), asset.getDurationSeconds()));
    }

    /** Resolves a playlist or segment of the content's HLS output; only bare HLS file names are accepted. */
    public Path resolveHlsFile(String contentIdStr, String filename) {
        if (!isValidHlsFilename(filename)) {
            throw new BadRequestException("Invalid HLS file name");
        }
        UUID contentId = parseContentId(contentIdStr);
        Path file = storageService.resolve(HlsTranscoderService.hlsDirectory(contentId) + "/" + filename);
        if (!Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("HLS file not found");
        }
        return file;
    }

    public static boolean isValidHlsFilename(String filename) {
        return filename != null
                && !filename.contains("/")
                && !filename.contains("\\")
                && !filename.contains("..")
                && HLS_FILENAME.matcher(filename).matches();
    }

    MediaAssetResponse mapToResponse(MediaAsset asset) {
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
        return response;
    }

    private void enqueueAfterCommit(UUID assetId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    hlsTranscoderService.transcode(assetId);
                }
            });
        } else {
            hlsTranscoderService.transcode(assetId);
        }
    }

    private static UUID parseContentId(String contentIdStr) {
        try {
            return UUID.fromString(contentIdStr);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid content id: " + contentIdStr);
        }
    }

    private static String sanitizeFilename(String name) {
        String base = name == null || name.isBlank() ? "video.mp4" : Paths.get(name.replace('\\', '/')).getFileName().toString();
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank() || cleaned.startsWith(".")) {
            cleaned = "video" + cleaned;
        }
        return cleaned.length() > 150 ? cleaned.substring(cleaned.length() - 150) : cleaned;
    }
}
