package com.streamx.media.dto;

import com.streamx.media.domain.UploadPurpose;

public record CompleteUploadResponse(UploadPurpose purpose, MediaFileResponse file, MediaAssetResponse asset) {
}
