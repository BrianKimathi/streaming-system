package com.streamx.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SeasonRequest(
        @NotNull(message = "Season number is required")
        @Positive(message = "Season number must be positive") Integer seasonNumber,
        @Size(max = 255, message = "Title is too long") String title,
        @Size(max = 2000, message = "Synopsis is too long") String synopsis,
        LocalDate releaseDate,
        @MediaUrl String posterUrl
) {
}
