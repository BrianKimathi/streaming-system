package com.streamx.catalog.dto;

import java.time.LocalDate;

public record EpisodeResponse(
        String id,
        String seasonId,
        String tvShowId,
        Integer seasonNumber,
        Integer episodeNumber,
        String title,
        String synopsis,
        Integer runtimeMinutes,
        LocalDate releaseDate,
        String thumbnailUrl
) {
}
