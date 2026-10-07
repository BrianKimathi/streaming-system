package com.streamx.catalog.service;

import com.streamx.catalog.domain.*;
import com.streamx.catalog.dto.*;
import com.streamx.catalog.repository.*;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CatalogAdminService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final MovieRepository movieRepository;
    private final TvShowRepository tvShowRepository;
    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;
    private final GenreRepository genreRepository;
    private final CatalogMapper mapper;
    private final TvShowAssembler tvShowAssembler;

    public CatalogAdminService(MovieRepository movieRepository, TvShowRepository tvShowRepository,
                               SeasonRepository seasonRepository, EpisodeRepository episodeRepository,
                               GenreRepository genreRepository, CatalogMapper mapper,
                               TvShowAssembler tvShowAssembler) {
        this.movieRepository = movieRepository;
        this.tvShowRepository = tvShowRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.genreRepository = genreRepository;
        this.mapper = mapper;
        this.tvShowAssembler = tvShowAssembler;
    }

    // --- Movies ---

    @Transactional(readOnly = true)
    public List<MovieResponse> listAllMovies() {
        return movieRepository.findAll(NEWEST_FIRST).stream()
                .map(mapper::toMovieResponse)
                .toList();
    }

    @Transactional
    public MovieResponse updateMovieStatus(String id, ContentStatus status) {
        Movie movie = movieRepository.findById(CatalogIds.parse(id))
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
        movie.setStatus(status);
        return mapper.toMovieResponse(movieRepository.save(movie));
    }

    // --- TV shows ---

    @Transactional(readOnly = true)
    public List<TvShowResponse> listAllTvShows() {
        return tvShowAssembler.toResponses(tvShowRepository.findAll(NEWEST_FIRST));
    }

    @Transactional(readOnly = true)
    public TvShowDetailResponse getTvShowDetail(String id) {
        return tvShowAssembler.toDetail(findShow(id));
    }

    @Transactional
    public TvShowResponse createTvShow(CreateTvShowRequest request) {
        TvShow show = new TvShow();
        show.setTitle(request.title().trim());
        show.setSynopsis(request.synopsis());
        show.setReleaseDate(request.releaseDate());
        show.setMaturityRating(request.maturityRating());
        show.setPosterUrl(blankToNull(request.posterUrl()));
        show.setBackdropUrl(blankToNull(request.backdropUrl()));
        show.setTrailerUrl(blankToNull(request.trailerUrl()));
        show.setStatus(request.status() != null ? request.status() : ContentStatus.DRAFT);
        show.setGenres(resolveGenres(request.genreIds()));
        TvShow saved = tvShowRepository.save(show);

        int seasons = request.seasonsCount() != null ? request.seasonsCount() : 0;
        for (int number = 1; number <= seasons; number++) {
            Season season = new Season();
            season.setTvShowId(saved.getId());
            season.setSeasonNumber(number);
            season.setTitle(defaultSeasonTitle(number));
            seasonRepository.save(season);
        }
        return tvShowAssembler.toResponse(saved);
    }

    @Transactional
    public TvShowResponse updateTvShowStatus(String id, ContentStatus status) {
        TvShow show = findShow(id);
        show.setStatus(status);
        return tvShowAssembler.toResponse(tvShowRepository.save(show));
    }

    // --- Seasons ---

    @Transactional
    public SeasonResponse createSeason(String showId, SeasonRequest request) {
        TvShow show = findShow(showId);
        int number = requirePositive(request.seasonNumber(), "Season number");
        if (seasonRepository.existsByTvShowIdAndSeasonNumber(show.getId(), number)) {
            throw new BadRequestException("Season " + number + " already exists for this show");
        }
        Season season = new Season();
        season.setTvShowId(show.getId());
        applySeason(season, request, number);
        return mapper.toSeasonResponse(seasonRepository.save(season), List.of());
    }

    @Transactional
    public SeasonResponse updateSeason(String seasonId, SeasonRequest request) {
        Season season = findSeason(seasonId);
        int number = requirePositive(request.seasonNumber(), "Season number");
        if (seasonRepository.existsByTvShowIdAndSeasonNumberAndIdNot(season.getTvShowId(), number, season.getId())) {
            throw new BadRequestException("Season " + number + " already exists for this show");
        }
        applySeason(season, request, number);
        return tvShowAssembler.toSeasonResponse(seasonRepository.save(season));
    }

    @Transactional
    public void deleteSeason(String seasonId) {
        Season season = findSeason(seasonId);
        episodeRepository.deleteBySeasonId(season.getId());
        seasonRepository.delete(season);
    }

    // --- Episodes ---

    @Transactional
    public EpisodeResponse createEpisode(String seasonId, EpisodeRequest request) {
        Season season = findSeason(seasonId);
        int number = requirePositive(request.episodeNumber(), "Episode number");
        if (episodeRepository.existsBySeasonIdAndEpisodeNumber(season.getId(), number)) {
            throw new BadRequestException("Episode " + number + " already exists in this season");
        }
        Episode episode = new Episode();
        episode.setSeasonId(season.getId());
        applyEpisode(episode, request, number);
        return mapper.toEpisodeResponse(episodeRepository.save(episode), season);
    }

    @Transactional
    public EpisodeResponse updateEpisode(String episodeId, EpisodeRequest request) {
        Episode episode = findEpisode(episodeId);
        int number = requirePositive(request.episodeNumber(), "Episode number");
        if (episodeRepository.existsBySeasonIdAndEpisodeNumberAndIdNot(episode.getSeasonId(), number,
                episode.getId())) {
            throw new BadRequestException("Episode " + number + " already exists in this season");
        }
        applyEpisode(episode, request, number);
        Season season = seasonRepository.findById(episode.getSeasonId())
                .orElseThrow(() -> new ResourceNotFoundException("Season not found"));
        return mapper.toEpisodeResponse(episodeRepository.save(episode), season);
    }

    @Transactional
    public void deleteEpisode(String episodeId) {
        episodeRepository.delete(findEpisode(episodeId));
    }

    // --- Stats ---

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<String, Long> moviesByStatus = new LinkedHashMap<>();
        Map<String, Long> showsByStatus = new LinkedHashMap<>();
        for (ContentStatus status : ContentStatus.values()) {
            moviesByStatus.put(status.name(), movieRepository.countByStatus(status));
            showsByStatus.put(status.name(), tvShowRepository.countByStatus(status));
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalMovies", movieRepository.count());
        stats.put("publishedMovies", moviesByStatus.get(ContentStatus.PUBLISHED.name()));
        stats.put("totalTvShows", tvShowRepository.count());
        stats.put("publishedTvShows", showsByStatus.get(ContentStatus.PUBLISHED.name()));
        stats.put("totalGenres", genreRepository.count());
        stats.put("moviesByStatus", moviesByStatus);
        stats.put("tvShowsByStatus", showsByStatus);
        return stats;
    }

    private void applySeason(Season season, SeasonRequest request, int number) {
        String title = blankToNull(request.title());
        season.setSeasonNumber(number);
        season.setTitle(title != null ? title : defaultSeasonTitle(number));
        season.setSynopsis(blankToNull(request.synopsis()));
        season.setReleaseDate(request.releaseDate());
        season.setPosterUrl(blankToNull(request.posterUrl()));
    }

    private void applyEpisode(Episode episode, EpisodeRequest request, int number) {
        String title = blankToNull(request.title());
        if (title == null) {
            throw new BadRequestException("Title is required");
        }
        if (request.runtimeMinutes() != null) {
            requirePositive(request.runtimeMinutes(), "Runtime");
        }
        episode.setEpisodeNumber(number);
        episode.setTitle(title);
        episode.setSynopsis(blankToNull(request.synopsis()));
        episode.setRuntimeMinutes(request.runtimeMinutes());
        episode.setReleaseDate(request.releaseDate());
        episode.setThumbnailUrl(blankToNull(request.thumbnailUrl()));
    }

    private TvShow findShow(String id) {
        return tvShowRepository.findById(CatalogIds.parse(id))
                .orElseThrow(() -> new ResourceNotFoundException("TV show not found with id: " + id));
    }

    private Season findSeason(String id) {
        return seasonRepository.findById(CatalogIds.parse(id))
                .orElseThrow(() -> new ResourceNotFoundException("Season not found with id: " + id));
    }

    private Episode findEpisode(String id) {
        return episodeRepository.findById(CatalogIds.parse(id))
                .orElseThrow(() -> new ResourceNotFoundException("Episode not found with id: " + id));
    }

    private Set<Genre> resolveGenres(Set<String> genreIds) {
        Set<Genre> genres = new HashSet<>();
        if (genreIds != null) {
            for (String genreId : genreIds) {
                genres.add(genreRepository.findById(CatalogIds.parse(genreId))
                        .orElseThrow(() -> new ResourceNotFoundException("Genre not found: " + genreId)));
            }
        }
        return genres;
    }

    private static int requirePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new BadRequestException(field + " must be a positive number");
        }
        return value;
    }

    private static String defaultSeasonTitle(int number) {
        return "Season " + number;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
