package com.streamx.catalog.dto;

import com.streamx.catalog.domain.ContentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.Set;

public record CreateTvShowRequest(
        @NotBlank(message = "Title is required") String title,
        String synopsis,
        LocalDate releaseDate,
        String maturityRating,
        String posterUrl,
        String backdropUrl,
        String trailerUrl,
        ContentStatus status,
        Set<String> genreIds,
        @Min(value = 0, message = "Seasons count cannot be negative")
        @Max(value = 100, message = "Seasons count is too large") Integer seasonsCount
) {
}
