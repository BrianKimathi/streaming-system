package com.streamx.user.dto;

import com.streamx.user.domain.TitleType;

import java.time.LocalDateTime;

public record WatchlistItemResponse(String titleId, TitleType titleType, LocalDateTime addedAt) {
}
