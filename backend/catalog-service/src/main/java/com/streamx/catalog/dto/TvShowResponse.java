package com.streamx.catalog.dto;

import com.streamx.catalog.domain.ContentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record TvShowResponse(
        String id,
        String title,
        String synopsis,
        LocalDate releaseDate,
        String maturityRating,
        String posterUrl,
        String backdropUrl,
        String trailerUrl,
        ContentStatus status,
        Set<GenreResponse> genres,
        int seasonsCount,
        LocalDateTime createdAt
) {
}
