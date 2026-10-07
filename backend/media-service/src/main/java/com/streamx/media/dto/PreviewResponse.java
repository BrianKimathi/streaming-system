package com.streamx.media.dto;

import java.time.LocalDateTime;

public record PreviewResponse(String streamUrl, LocalDateTime expiresAt) {
}
