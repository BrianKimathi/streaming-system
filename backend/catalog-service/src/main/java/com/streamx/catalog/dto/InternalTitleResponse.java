package com.streamx.catalog.dto;

import com.streamx.catalog.domain.ContentStatus;

/**
 * Service-to-service title descriptor. For episodes, {@code tvShowId} is the parent show and {@code status} is the
 * show's status (episodes have no status of their own).
 */
public record InternalTitleResponse(
        String id,
        String title,
        TitleType titleType,
        String tvShowId,
        ContentStatus status
) {
    public enum TitleType {
        MOVIE, SERIES, EPISODE
    }
}
