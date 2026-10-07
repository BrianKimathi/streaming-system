package com.streamx.catalog.dto;

import java.time.LocalDate;
import java.util.List;

public record SeasonResponse(
        String id,
        String tvShowId,
        Integer seasonNumber,
        String title,
        String synopsis,
        LocalDate releaseDate,
        String posterUrl,
        List<EpisodeResponse> episodes
) {
}
