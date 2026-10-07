package com.streamx.media.dto;

import com.streamx.media.domain.UploadPurpose;

import java.time.LocalDateTime;

public record MediaFileResponse(
        String id,
        UploadPurpose purpose,
        String filename,
        String contentType,
        long sizeBytes,
        String url,
        LocalDateTime createdAt) {
}
