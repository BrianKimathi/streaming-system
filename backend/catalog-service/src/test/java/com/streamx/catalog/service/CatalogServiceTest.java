package com.streamx.catalog.service;

import com.streamx.catalog.domain.*;
import com.streamx.catalog.dto.*;
import com.streamx.catalog.dto.InternalTitleResponse.TitleType;
import com.streamx.catalog.repository.*;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private MovieRepository movieRepository;
    @Autowired
    private TvShowRepository tvShowRepository;
    @Autowired
    private SeasonRepository seasonRepository;
    @Autowired
    private EpisodeRepository episodeRepository;
    @Autowired
    private GenreRepository genreRepository;

    @Test
    void createGenreAndMovie_publishedMovieIsSearchable() {
        CreateGenreRequest genreReq = new CreateGenreRequest();
        genreReq.setName("Sci-Fi");
        GenreResponse genreRes = catalogService.createGenre(genreReq);

        assertNotNull(genreRes.getId());
        assertEquals("Sci-Fi", genreRes.getName());
        assertEquals("sci-fi", genreRes.getSlug());

        CreateMovieRequest movieReq = new CreateMovieRequest();
        movieReq.setTitle("Interstellar");
        movieReq.setReleaseDate(LocalDate.of(2014, 11, 7));
        movieReq.setRuntimeMinutes(169);
        movieReq.setMaturityRating("PG-13");
        movieReq.setStatus(ContentStatus.PUBLISHED);
        movieReq.setGenreIds(Collections.singleton(genreRes.getId()));

        MovieResponse movieRes = catalogService.createMovie(movieReq);

        assertNotNull(movieRes.getId());
        assertNotNull(movieRes.getCreatedAt());
        assertEquals(1, movieRes.getGenres().size());

        Page<MovieResponse> movies = catalogService.getPublishedMovies("stell", null, PAGE);
        assertEquals(1, movies.getTotalElements());
        assertEquals("Interstellar", movies.getContent().get(0).getTitle());
    }

    @Test
    void publicMovies_onlyReturnPublished() {
        Movie published = movie("Visible", ContentStatus.PUBLISHED);
        Movie draft = movie("Hidden Draft", ContentStatus.DRAFT);
        movie("Hidden Archived", ContentStatus.ARCHIVED);

        Page<MovieResponse> page = catalogService.getPublishedMovies(null, null, PAGE);

        assertEquals(List.of("Visible"), page.getContent().stream().map(MovieResponse::getTitle).toList());
        assertEquals("Visible", catalogService.getPublishedMovie(published.getId().toString()).getTitle());
        assertThrows(ResourceNotFoundException.class,
                () -> catalogService.getPublishedMovie(draft.getId().toString()));
        assertThrows(ResourceNotFoundException.class,
                () -> catalogService.getPublishedMovie(UUID.randomUUID().toString()));
        assertThrows(BadRequestException.class, () -> catalogService.getPublishedMovie("not-a-uuid"));
    }

    @Test
    void publicMovies_hideMediaAssetUrl() {
        Movie m = movie("Asset", ContentStatus.PUBLISHED);
        m.setMediaAssetUrl("s3://private/asset.mp4");
        movieRepository.save(m);

        assertNull(catalogService.getPublishedMovie(m.getId().toString()).getMediaAssetUrl());
    }

    @Test
    void publicMovies_filterByGenreAndSearch() {
        Genre drama = genre("Drama");
        Genre comedy = genre("Comedy");
        movie("Sad Story", ContentStatus.PUBLISHED, drama);
        movie("Funny Story", ContentStatus.PUBLISHED, comedy);
        movie("Funny Drama", ContentStatus.PUBLISHED, drama, comedy);
        movie("Draft Drama", ContentStatus.DRAFT, drama);

        Set<String> dramas = titles(catalogService.getPublishedMovies(null, drama.getId().toString(), PAGE));
        assertEquals(Set.of("Sad Story", "Funny Drama"), dramas);

        Set<String> funnyDramas = titles(catalogService.getPublishedMovies("FUNNY", drama.getId().toString(), PAGE));
        assertEquals(Set.of("Funny Drama"), funnyDramas);

        assertEquals(0, catalogService.getPublishedMovies("100%", null, PAGE).getTotalElements());
        assertThrows(BadRequestException.class, () -> catalogService.getPublishedMovies(null, "bad", PAGE));
    }

    @Test
    void publicTvShows_onlyReturnPublishedWithAccurateSeasonCounts() {
        Genre drama = genre("Drama");
        TvShow published = show("Public Show", ContentStatus.PUBLISHED, drama);
        season(published, 1);
        season(published, 2);
        show("Draft Show", ContentStatus.DRAFT, drama);

        Page<TvShowResponse> page = catalogService.getPublishedTvShows(null, drama.getId().toString(), PAGE);

        assertEquals(1, page.getTotalElements());
        TvShowResponse response = page.getContent().get(0);
        assertEquals("Public Show", response.title());
        assertEquals(2, response.seasonsCount());
        assertEquals(1, catalogService.getPublishedTvShows("public", null, PAGE).getTotalElements());
        assertEquals(0, catalogService.getPublishedTvShows("draft", null, PAGE).getTotalElements());
    }

    @Test
    void publicTvShowDetail_returnsOrderedSeasonsAndEpisodes() {
        TvShow show = show("Ordered", ContentStatus.PUBLISHED);
        Season s2 = season(show, 2);
        Season s1 = season(show, 1);
        episode(s1, 2, "S1E2");
        episode(s1, 1, "S1E1");
        episode(s2, 1, "S2E1");

        TvShowDetailResponse detail = catalogService.getPublishedTvShowDetail(show.getId().toString());

        assertEquals(2, detail.seasonsCount());
        assertEquals(List.of(1, 2), detail.seasons().stream().map(SeasonResponse::seasonNumber).toList());
        SeasonResponse first = detail.seasons().get(0);
        assertEquals(List.of("S1E1", "S1E2"), first.episodes().stream().map(EpisodeResponse::title).toList());
        EpisodeResponse ep = first.episodes().get(0);
        assertEquals(show.getId().toString(), ep.tvShowId());
        assertEquals(s1.getId().toString(), ep.seasonId());
        assertEquals(1, ep.seasonNumber());
        assertEquals(1, detail.seasons().get(1).episodes().size());
    }

    @Test
    void publicTvShowDetail_hidesUnpublished() {
        TvShow draft = show("Draft", ContentStatus.DRAFT);
        assertThrows(ResourceNotFoundException.class,
                () -> catalogService.getPublishedTvShowDetail(draft.getId().toString()));
    }

    @Test
    void lookup_returnsOnlyPublishedContentInRequestOrder() {
        Movie m1 = movie("Movie One", ContentStatus.PUBLISHED);
        Movie m2 = movie("Movie Two", ContentStatus.PUBLISHED);
        Movie draftMovie = movie("Draft Movie", ContentStatus.DRAFT);
        TvShow publishedShow = show("Published Show", ContentStatus.PUBLISHED);
        TvShow draftShow = show("Draft Show", ContentStatus.DRAFT);
        Season publishedSeason = season(publishedShow, 1);
        Season draftSeason = season(draftShow, 1);
        Episode visibleEpisode = episode(publishedSeason, 1, "Visible Episode");
        Episode hiddenEpisode = episode(draftSeason, 1, "Hidden Episode");

        CatalogLookupResponse result = catalogService.lookup(List.of(
                m2.getId().toString(), hiddenEpisode.getId().toString(), draftMovie.getId().toString(),
                publishedShow.getId().toString(), draftShow.getId().toString(), m1.getId().toString(),
                visibleEpisode.getId().toString(), UUID.randomUUID().toString(), m1.getId().toString()));

        assertEquals(List.of("Movie Two", "Movie One"), result.movies().stream().map(MovieResponse::getTitle).toList());
        assertEquals(List.of("Published Show"), result.tvShows().stream().map(TvShowResponse::title).toList());
        assertEquals(1, result.tvShows().get(0).seasonsCount());
        assertEquals(1, result.episodes().size());
        EpisodeResponse ep = result.episodes().get(0);
        assertEquals("Visible Episode", ep.title());
        assertEquals(publishedShow.getId().toString(), ep.tvShowId());
    }

    @Test
    void lookup_validatesIds() {
        List<String> tooMany = IntStream.range(0, CatalogService.MAX_LOOKUP_IDS + 1)
                .mapToObj(i -> UUID.randomUUID().toString())
                .toList();
        assertThrows(BadRequestException.class, () -> catalogService.lookup(tooMany));
        assertThrows(BadRequestException.class, () -> catalogService.lookup(List.of("nope")));

        CatalogLookupResponse empty = catalogService.lookup(List.of());
        assertTrue(empty.movies().isEmpty() && empty.tvShows().isEmpty() && empty.episodes().isEmpty());
    }

    @Test
    void internalTitle_resolvesAnyStatusAndEpisodeParent() {
        Movie draftMovie = movie("Draft Movie", ContentStatus.DRAFT);
        TvShow show = show("Series", ContentStatus.ARCHIVED);
        Episode ep = episode(season(show, 1), 3, "Pilot");

        InternalTitleResponse movieTitle = catalogService.getInternalTitle(draftMovie.getId().toString());
        assertEquals(TitleType.MOVIE, movieTitle.titleType());
        assertEquals(ContentStatus.DRAFT, movieTitle.status());
        assertNull(movieTitle.tvShowId());

        InternalTitleResponse showTitle = catalogService.getInternalTitle(show.getId().toString());
        assertEquals(TitleType.SERIES, showTitle.titleType());
        assertEquals("Series", showTitle.title());

        InternalTitleResponse episodeTitle = catalogService.getInternalTitle(ep.getId().toString());
        assertEquals(TitleType.EPISODE, episodeTitle.titleType());
        assertEquals("Pilot", episodeTitle.title());
        assertEquals(show.getId().toString(), episodeTitle.tvShowId());
        assertEquals(ContentStatus.ARCHIVED, episodeTitle.status());

        assertThrows(ResourceNotFoundException.class,
                () -> catalogService.getInternalTitle(UUID.randomUUID().toString()));
    }

    private Set<String> titles(Page<MovieResponse> page) {
        Set<String> titles = new HashSet<>();
        page.forEach(m -> titles.add(m.getTitle()));
        return titles;
    }

    private Genre genre(String name) {
        Genre genre = new Genre();
        genre.setName(name);
        genre.setSlug(name.toLowerCase(Locale.ROOT));
        return genreRepository.save(genre);
    }

    private Movie movie(String title, ContentStatus status, Genre... genres) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setStatus(status);
        movie.setGenres(new HashSet<>(Arrays.asList(genres)));
        return movieRepository.save(movie);
    }

    private TvShow show(String title, ContentStatus status, Genre... genres) {
        TvShow show = new TvShow();
        show.setTitle(title);
        show.setStatus(status);
        show.setGenres(new HashSet<>(Arrays.asList(genres)));
        return tvShowRepository.save(show);
    }

    private Season season(TvShow show, int number) {
        Season season = new Season();
        season.setTvShowId(show.getId());
        season.setSeasonNumber(number);
        season.setTitle("Season " + number);
        return seasonRepository.save(season);
    }

    private Episode episode(Season season, int number, String title) {
        Episode episode = new Episode();
        episode.setSeasonId(season.getId());
        episode.setEpisodeNumber(number);
        episode.setTitle(title);
        return episodeRepository.save(episode);
    }
}
