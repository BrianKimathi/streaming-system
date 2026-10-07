package com.streamx.catalog.dto;

import com.streamx.catalog.domain.ContentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record TvShowDetailResponse(
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
        LocalDateTime createdAt,
        List<SeasonResponse> seasons
) {
    public static TvShowDetailResponse of(TvShowResponse show, List<SeasonResponse> seasons) {
        return new TvShowDetailResponse(show.id(), show.title(), show.synopsis(), show.releaseDate(),
                show.maturityRating(), show.posterUrl(), show.backdropUrl(), show.trailerUrl(), show.status(),
                show.genres(), show.seasonsCount(), show.createdAt(), seasons);
    }
}
