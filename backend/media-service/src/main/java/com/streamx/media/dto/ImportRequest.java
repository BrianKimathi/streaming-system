package com.streamx.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImportRequest(@NotBlank String contentId, @NotBlank @Size(max = 2000) String url) {
}
