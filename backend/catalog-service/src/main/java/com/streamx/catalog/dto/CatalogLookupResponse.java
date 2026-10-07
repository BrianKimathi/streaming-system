package com.streamx.catalog.dto;

import java.util.List;

public record CatalogLookupResponse(
        List<MovieResponse> movies,
        List<TvShowResponse> tvShows,
        List<EpisodeResponse> episodes
) {
}
