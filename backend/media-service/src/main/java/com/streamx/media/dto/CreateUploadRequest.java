package com.streamx.media.dto;

import com.streamx.media.domain.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUploadRequest(
        @NotBlank @Size(max = 500) String filename,
        @Size(max = 200) String contentType,
        @NotNull @Positive Long sizeBytes,
        @NotNull UploadPurpose purpose,
        String contentId) {
}
