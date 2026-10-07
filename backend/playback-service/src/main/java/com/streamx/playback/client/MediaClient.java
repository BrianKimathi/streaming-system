package com.streamx.playback.client;

import java.util.Optional;
import java.util.UUID;

public interface MediaClient {

    record MediaStatus(String status, Integer durationSeconds) {
        public boolean isReady() {
            return "COMPLETED".equals(status);
        }
    }

    /**
     * Returns the processing status of the content's video, or empty when no video was uploaded for it.
     * Throws ServiceUnavailableException when media-service cannot be reached.
     */
    Optional<MediaStatus> mediaStatus(UUID contentId);
}
