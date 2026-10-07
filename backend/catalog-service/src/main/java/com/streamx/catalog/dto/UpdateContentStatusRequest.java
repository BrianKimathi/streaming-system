package com.streamx.catalog.dto;

import com.streamx.catalog.domain.ContentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateContentStatusRequest(@NotNull(message = "Status is required") ContentStatus status) {
}
