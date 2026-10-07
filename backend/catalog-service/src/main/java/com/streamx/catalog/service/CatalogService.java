package com.streamx.catalog.service;

import com.streamx.catalog.domain.*;
import com.streamx.catalog.dto.*;
import com.streamx.catalog.dto.InternalTitleResponse.TitleType;
import com.streamx.catalog.repository.*;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    public static final int MAX_LOOKUP_IDS = 100;

    private final MovieRepository movieRepository;
    private final TvShowRepository tvShowRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;
    private final GenreRepository genreRepository;
    private final CatalogMapper mapper;
    private final TvShowAssembler tvShowAssembler;

    public CatalogService(MovieRepository movieRepository, TvShowRepository tvShowRepository,
                          SeasonRepository seasonRepository, EpisodeRepository episodeRepository,
                          GenreRepository genreRepository, CatalogMapper mapper, TvShowAssembler tvShowAssembler) {
        this.movieRepository = movieRepository;
        this.tvShowRepository = tvShowRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.genreRepository = genreRepository;
        this.mapper = mapper;
        this.tvShowAssembler = tvShowAssembler;
    }

    // --- Genres ---

    @Transactional
    public GenreResponse createGenre(CreateGenreRequest request) {
        String name = request.getName().trim();
        if (genreRepository.existsByName(name)) {
            throw new BadRequestException("Genre already exists");
        }
        Genre genre = new Genre();
        genre.setName(name);
        genre.setSlug(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "-"));
        return mapper.toGenreResponse(genreRepository.save(genre));
    }

    @Transactional(readOnly = true)
    public List<GenreResponse> getAllGenres() {
        return genreRepository.findAll(Sort.by("name")).stream()
                .map(mapper::toGenreResponse)
                .toList();
    }

    // --- Movies ---

    @Transactional
    public MovieResponse createMovie(CreateMovieRequest request) {
        Set<Genre> genres = new HashSet<>();
        if (request.getGenreIds() != null) {
            for (String genreId : request.getGenreIds()) {
                genres.add(genreRepository.findById(CatalogIds.parse(genreId))
                        .orElseThrow(() -> new ResourceNotFoundException("Genre not found: " + genreId)));
            }
        }

        Movie movie = new Movie();
        movie.setTitle(request.getTitle().trim());
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
        return mapper.toMovieResponse(movieRepository.save(movie));
    }

    @Transactional(readOnly = true)
    public MovieResponse getPublishedMovie(String id) {
        return movieRepository.findById(CatalogIds.parse(id))
                .filter(movie -> movie.getStatus() == ContentStatus.PUBLISHED)
                .map(mapper::toPublicMovieResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found"));
    }

    @Transactional(readOnly = true)
    public Page<MovieResponse> getPublishedMovies(String search, String genreId, Pageable pageable) {
        return movieRepository.findAll(publishedFilter(search, genreId), pageable)
                .map(mapper::toPublicMovieResponse);
    }

    // --- TV shows ---

    @Transactional(readOnly = true)
    public Page<TvShowResponse> getPublishedTvShows(String search, String genreId, Pageable pageable) {
        Page<TvShow> page = tvShowRepository.findAll(publishedFilter(search, genreId), pageable);
        return new PageImpl<>(tvShowAssembler.toResponses(page.getContent()), page.getPageable(),
                page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TvShowDetailResponse getPublishedTvShowDetail(String id) {
        TvShow show = tvShowRepository.findById(CatalogIds.parse(id))
                .filter(s -> s.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("TV show not found"));
        return tvShowAssembler.toDetail(show);
    }

    // --- Lookup ---

    @Transactional(readOnly = true)
    public CatalogLookupResponse lookup(List<String> rawIds) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (rawIds != null) {
            for (String raw : rawIds) {
                if (raw != null && !raw.isBlank()) {
                    ids.add(CatalogIds.parse(raw));
                }
            }
        }
        if (ids.size() > MAX_LOOKUP_IDS) {
            throw new BadRequestException("A maximum of " + MAX_LOOKUP_IDS + " ids can be looked up at once");
        }
        if (ids.isEmpty()) {
            return new CatalogLookupResponse(List.of(), List.of(), List.of());
        }

        Map<UUID, Integer> requestOrder = new HashMap<>();
        for (UUID id : ids) {
            requestOrder.put(id, requestOrder.size());
        }
        Comparator<UUID> byRequestOrder = Comparator.comparing(requestOrder::get);

        List<MovieResponse> movies = movieRepository.findByIdInAndStatus(ids, ContentStatus.PUBLISHED).stream()
                .sorted(Comparator.comparing(Movie::getId, byRequestOrder))
                .map(mapper::toPublicMovieResponse)
                .toList();

        List<TvShow> shows = tvShowRepository.findByIdInAndStatus(ids, ContentStatus.PUBLISHED).stream()
                .sorted(Comparator.comparing(TvShow::getId, byRequestOrder))
                .toList();

        return new CatalogLookupResponse(movies, tvShowAssembler.toResponses(shows),
                lookupPublishedEpisodes(ids, byRequestOrder));
    }

    private List<EpisodeResponse> lookupPublishedEpisodes(Set<UUID> ids, Comparator<UUID> byRequestOrder) {
        List<Episode> episodes = episodeRepository.findAllById(ids);
        if (episodes.isEmpty()) {
            return List.of();
        }
        Map<UUID, Season> seasons = seasonRepository.findAllById(
                        episodes.stream().map(Episode::getSeasonId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Season::getId, Function.identity()));
        Set<UUID> publishedShowIds = tvShowRepository.findByIdInAndStatus(
                        seasons.values().stream().map(Season::getTvShowId).collect(Collectors.toSet()),
                        ContentStatus.PUBLISHED)
                .stream()
                .map(TvShow::getId)
                .collect(Collectors.toSet());

        return episodes.stream()
                .filter(episode -> {
                    Season season = seasons.get(episode.getSeasonId());
                    return season != null && publishedShowIds.contains(season.getTvShowId());
                })
                .sorted(Comparator.comparing(Episode::getId, byRequestOrder))
                .map(episode -> mapper.toEpisodeResponse(episode, seasons.get(episode.getSeasonId())))
                .toList();
    }

    // --- Internal ---

    @Transactional(readOnly = true)
    public InternalTitleResponse getInternalTitle(String rawId) {
        UUID id = CatalogIds.parse(rawId);

        Optional<Movie> movie = movieRepository.findById(id);
        if (movie.isPresent()) {
            Movie m = movie.get();
            return new InternalTitleResponse(m.getId().toString(), m.getTitle(), TitleType.MOVIE, null, m.getStatus());
        }

        Optional<TvShow> show = tvShowRepository.findById(id);
        if (show.isPresent()) {
            TvShow s = show.get();
            return new InternalTitleResponse(s.getId().toString(), s.getTitle(), TitleType.SERIES, null,
                    s.getStatus());
        }

        return episodeRepository.findById(id)
                .flatMap(episode -> seasonRepository.findById(episode.getSeasonId())
                        .flatMap(season -> tvShowRepository.findById(season.getTvShowId()))
                        .map(parent -> new InternalTitleResponse(episode.getId().toString(), episode.getTitle(),
                                TitleType.EPISODE, parent.getId().toString(), parent.getStatus())))
                .orElseThrow(() -> new ResourceNotFoundException("Title not found"));
    }

    private static <T> Specification<T> publishedFilter(String search, String genreId) {
        Specification<T> spec = CatalogSpecifications.hasStatus(ContentStatus.PUBLISHED);
        if (search != null && !search.isBlank()) {
            spec = spec.and(CatalogSpecifications.titleContains(search));
        }
        if (genreId != null && !genreId.isBlank()) {
            spec = spec.and(CatalogSpecifications.hasGenre(CatalogIds.parse(genreId)));
        }
        return spec;
    }
}
