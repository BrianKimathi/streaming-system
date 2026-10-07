package com.streamx.media.service;

import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.hls.HlsTranscoderService;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

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
        UUID contentId = UUID.fromString(contentIdStr);

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "video.mp4";
        String rawStoragePath = "raw/" + contentId + "/" + originalFilename;

        try {
            storageService.storeFile(rawStoragePath, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file", e);
        }

        String masterPlaylistUrl = hlsTranscoderService.generateHlsStream(contentIdStr);

        MediaAsset asset = repository.findByContentId(contentId).orElseGet(MediaAsset::new);
        asset.setContentId(contentId);
        asset.setOriginalFilename(originalFilename);
        asset.setStoragePath(rawStoragePath);
        asset.setMasterPlaylistUrl(masterPlaylistUrl);
        asset.setStatus(MediaProcessingStatus.COMPLETED);
        asset.setDurationSeconds(7200); // 2 hours default mock duration

        MediaAsset saved = repository.save(asset);
        log.info("Media processing completed for contentId {}", contentIdStr);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public MediaAssetResponse getMediaAsset(String contentIdStr) {
        UUID contentId = UUID.fromString(contentIdStr);
        MediaAsset asset = repository.findByContentId(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Media asset not found for contentId: " + contentIdStr));
        return mapToResponse(asset);
    }

    public byte[] getHlsFile(String contentIdStr, String filename) {
        String relativePath = "hls/" + contentIdStr + "/" + filename;
        return storageService.readFile(relativePath);
    }

    private MediaAssetResponse mapToResponse(MediaAsset asset) {
        return new MediaAssetResponse(
                asset.getId().toString(),
                asset.getContentId().toString(),
                asset.getOriginalFilename(),
                asset.getMasterPlaylistUrl(),
                asset.getStatus(),
                asset.getDurationSeconds(),
                asset.getCreatedAt()
        );
    }
}
