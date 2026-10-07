package com.streamx.trending.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Mirror of catalog-service {@code InternalTitleResponse}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogTitle(UUID id, String title, String titleType, UUID tvShowId, String status) {

    public static final String MOVIE = "MOVIE";
    public static final String SERIES = "SERIES";
    public static final String EPISODE = "EPISODE";
    public static final String PUBLISHED = "PUBLISHED";

    public boolean isPublished() {
        return PUBLISHED.equals(status);
    }
}
