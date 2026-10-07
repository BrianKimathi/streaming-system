package com.streamx.media.dto;

import com.streamx.media.domain.MediaProcessingStatus;

public record MediaStatusResponse(MediaProcessingStatus status, Integer durationSeconds) {
}
