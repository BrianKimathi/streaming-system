package com.streamx.media.dto;

import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.domain.UploadSessionStatus;

import java.time.LocalDateTime;
import java.util.List;

public record UploadSessionResponse(
        String uploadId,
        UploadPurpose purpose,
        String contentId,
        String filename,
        long sizeBytes,
        long chunkSizeBytes,
        int totalParts,
        List<Integer> receivedParts,
        UploadSessionStatus status,
        LocalDateTime createdAt) {
}
