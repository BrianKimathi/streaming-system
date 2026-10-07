package com.streamx.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EpisodeRequest(
        @NotNull(message = "Episode number is required")
        @Positive(message = "Episode number must be positive") Integer episodeNumber,
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title is too long") String title,
        @Size(max = 2000, message = "Synopsis is too long") String synopsis,
        @Positive(message = "Runtime must be positive") Integer runtimeMinutes,
        LocalDate releaseDate,
        @Size(max = 255, message = "Thumbnail URL is too long") String thumbnailUrl
) {
}
