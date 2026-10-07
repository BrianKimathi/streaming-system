package com.streamx.media.service;

import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.repository.MediaAssetRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AssetStatusUpdater {

    private static final int MAX_FAILURE_REASON_LENGTH = 1900;

    private final MediaAssetRepository repository;

    public AssetStatusUpdater(MediaAssetRepository repository) {
        this.repository = repository;
    }

    /** Marks the asset FAILED; a previously transcoded HLS rendition (masterPlaylistUrl) is left in place. */
    public void markFailed(UUID assetId, String reason) {
        repository.findById(assetId).ifPresent(asset -> {
            asset.setStatus(MediaProcessingStatus.FAILED);
            asset.setFailureReason(truncate(reason));
            asset.setProgressPercent(null);
            repository.save(asset);
        });
    }

    static String truncate(String reason) {
        String message = reason == null || reason.isBlank() ? "Unknown error" : reason;
        return message.length() > MAX_FAILURE_REASON_LENGTH
                ? message.substring(message.length() - MAX_FAILURE_REASON_LENGTH)
                : message;
    }

    public static boolean isBusy(MediaAsset asset) {
        return asset.getStatus() == MediaProcessingStatus.PROCESSING || asset.getStatus() == MediaProcessingStatus.UPLOADING;
    }
}
