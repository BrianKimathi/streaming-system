package com.streamx.catalog.service;

import com.streamx.catalog.domain.ContentStatus;
import com.streamx.catalog.domain.Genre;
import com.streamx.catalog.domain.Movie;
import com.streamx.catalog.dto.*;
import com.streamx.catalog.repository.GenreRepository;
import com.streamx.catalog.repository.MovieRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;

    public CatalogService(MovieRepository movieRepository, GenreRepository genreRepository) {
        this.movieRepository = movieRepository;
        this.genreRepository = genreRepository;
    }

    // --- Genre Operations ---
    @Transactional
    public GenreResponse createGenre(CreateGenreRequest request) {
        if (genreRepository.existsByName(request.getName())) {
            throw new BadRequestException("Genre already exists");
        }
        String slug = request.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        Genre genre = new Genre();
        genre.setName(request.getName());
        genre.setSlug(slug);

        Genre saved = genreRepository.save(genre);
        return mapToGenreResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GenreResponse> getAllGenres() {
        return genreRepository.findAll().stream()
                .map(this::mapToGenreResponse)
                .collect(Collectors.toList());
    }

    // --- Movie Operations ---
    @Transactional
    public MovieResponse createMovie(CreateMovieRequest request) {
        Set<Genre> genres = new HashSet<>();
        if (request.getGenreIds() != null && !request.getGenreIds().isEmpty()) {
            for (String genreId : request.getGenreIds()) {
                Genre g = genreRepository.findById(UUID.fromString(genreId))
                        .orElseThrow(() -> new ResourceNotFoundException("Genre not found: " + genreId));
                genres.add(g);
            }
        }

        Movie movie = new Movie();
        movie.setTitle(request.getTitle());
        movie.setSynopsis(request.getSynopsis());
        movie.setReleaseDate(request.getReleaseDate());
        movie.setRuntimeMinutes(request.getRuntimeMinutes());
        movie.setMaturityRating(request.getMaturityRating());
        movie.setPosterUrl(request.getPosterUrl());
        movie.setBackdropUrl(request.getBackdropUrl());
        movie.setTrailerUrl(request.getTrailerUrl());
        movie.setMediaAssetUrl(request.getMediaAssetUrl());
        movie.setStatus(request.getStatus() != null ? request.getStatus() : ContentStatus.DRAFT);
        movie.setGenres(genres);

        Movie saved = movieRepository.save(movie);
        return mapToMovieResponse(saved);
    }

    @Transactional(readOnly = true)
    public MovieResponse getMovieById(String id) {
        Movie movie = movieRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
        return mapToMovieResponse(movie);
    }

    @Transactional(readOnly = true)
    public Page<MovieResponse> getMovies(ContentStatus status, String search, Pageable pageable) {
        ContentStatus targetStatus = (status != null) ? status : ContentStatus.PUBLISHED;
        Page<Movie> page;
        if (search != null && !search.isBlank()) {
            page = movieRepository.findByTitleContainingIgnoreCaseAndStatus(search, targetStatus, pageable);
        } else {
            page = movieRepository.findByStatus(targetStatus, pageable);
        }
        return page.map(this::mapToMovieResponse);
    }

    private GenreResponse mapToGenreResponse(Genre genre) {
        return new GenreResponse(genre.getId().toString(), genre.getName(), genre.getSlug());
    }

    private MovieResponse mapToMovieResponse(Movie movie) {
        Set<GenreResponse> genreResponses = movie.getGenres().stream()
                .map(this::mapToGenreResponse)
                .collect(Collectors.toSet());

        return new MovieResponse(
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
                genreResponses
        );
    }
}
