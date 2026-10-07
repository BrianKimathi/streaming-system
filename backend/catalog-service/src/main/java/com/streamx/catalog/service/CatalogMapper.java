package com.streamx.catalog.service;

import com.streamx.catalog.domain.*;
import com.streamx.catalog.dto.*;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CatalogMapper {

    public GenreResponse toGenreResponse(Genre genre) {
        return new GenreResponse(genre.getId().toString(), genre.getName(), genre.getSlug());
    }

    public Set<GenreResponse> toGenreResponses(Set<Genre> genres) {
        return genres.stream()
                .sorted(Comparator.comparing(Genre::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toGenreResponse)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Full representation for admins, including the raw media asset URL. */
    public MovieResponse toMovieResponse(Movie movie) {
        MovieResponse response = new MovieResponse(
                movie.getId().toString(),
                movie.getTitle(),
                movie.getSynopsis(),
                movie.getReleaseDate(),
                movie.getRuntimeMinutes(),
                movie.getMaturityRating(),
                movie.getPosterUrl(),
                movie.getBackdropUrl(),
                movie.getTrailerUrl(),
                movie.getMediaAssetUrl(),
                movie.getStatus(),
                toGenreResponses(movie.getGenres())
        );
        response.setCreatedAt(movie.getCreatedAt());
        return response;
    }

    /** Public representation: media is only reachable through playback stream tokens. */
    public MovieResponse toPublicMovieResponse(Movie movie) {
        MovieResponse response = toMovieResponse(movie);
        response.setMediaAssetUrl(null);
        return response;
    }

    public TvShowResponse toTvShowResponse(TvShow show, int seasonsCount) {
        return new TvShowResponse(
                show.getId().toString(),
                show.getTitle(),
                show.getSynopsis(),
                show.getReleaseDate(),
                show.getMaturityRating(),
                show.getPosterUrl(),
                show.getBackdropUrl(),
                show.getTrailerUrl(),
                show.getStatus(),
                toGenreResponses(show.getGenres()),
                seasonsCount,
                show.getCreatedAt()
        );
    }

    public EpisodeResponse toEpisodeResponse(Episode episode, Season season) {
        return new EpisodeResponse(
                episode.getId().toString(),
                season.getId().toString(),
                season.getTvShowId().toString(),
                season.getSeasonNumber(),
                episode.getEpisodeNumber(),
                episode.getTitle(),
                episode.getSynopsis(),
                episode.getRuntimeMinutes(),
                episode.getReleaseDate(),
                episode.getThumbnailUrl()
        );
    }

    public SeasonResponse toSeasonResponse(Season season, List<EpisodeResponse> episodes) {
        return new SeasonResponse(
                season.getId().toString(),
                season.getTvShowId().toString(),
                season.getSeasonNumber(),
                season.getTitle(),
                season.getSynopsis(),
                season.getReleaseDate(),
                season.getPosterUrl(),
                episodes
        );
    }
}
