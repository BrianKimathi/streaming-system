package com.streamx.user.dto;

import com.streamx.user.domain.TitleType;
import jakarta.validation.constraints.NotNull;

public class AddWatchlistItemRequest {

    @NotNull(message = "titleType is required (MOVIE or SERIES)")
    private TitleType titleType;

    public AddWatchlistItemRequest() {
    }

    public AddWatchlistItemRequest(TitleType titleType) {
        this.titleType = titleType;
    }

    public TitleType getTitleType() {
        return titleType;
    }

    public void setTitleType(TitleType titleType) {
        this.titleType = titleType;
    }
}
