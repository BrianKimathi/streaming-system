package com.streamx.trending.dto;

import com.streamx.trending.domain.TrendingEventType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code titleId} may be a movie, show or episode id; episodes count towards their show. */
public record RecordTrendingEventRequest(
        @NotNull(message = "titleId is required") UUID titleId,
        @NotNull(message = "eventType is required") TrendingEventType eventType
) {
}
