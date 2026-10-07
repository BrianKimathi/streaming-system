package com.streamx.catalog.service;

import com.streamx.catalog.domain.ContentStatus;
import com.streamx.catalog.dto.*;
import com.streamx.catalog.repository.EpisodeRepository;
import com.streamx.catalog.repository.SeasonRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogAdminServiceTest {

    @Autowired
    private CatalogAdminService adminService;
    @Autowired
    private SeasonRepository seasonRepository;
    @Autowired
    private EpisodeRepository episodeRepository;

    private String showId;

    @BeforeEach
    void setUp() {
        showId = adminService.createTvShow(new CreateTvShowRequest("Admin Show", null, null, "TV-14",
                null, null, null, ContentStatus.DRAFT, null, 0)).id();
    }

    @Test
    void adminDetail_returnsUnpublishedShow() {
        TvShowDetailResponse detail = adminService.getTvShowDetail(showId);
        assertEquals(ContentStatus.DRAFT, detail.status());
        assertTrue(detail.seasons().isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> adminService.getTvShowDetail(UUID.randomUUID().toString()));
    }

    @Test
    void createSeason_defaultsTitleAndRejectsDuplicateNumber() {
        SeasonResponse season = adminService.createSeason(showId, seasonRequest(1, null));
        assertEquals("Season 1", season.title());
        assertEquals(showId, season.tvShowId());
        assertTrue(season.episodes().isEmpty());

        assertThrows(BadRequestException.class, () -> adminService.createSeason(showId, seasonRequest(1, "Again")));
        assertThrows(BadRequestException.class, () -> adminService.createSeason(showId, seasonRequest(0, null)));
        assertThrows(BadRequestException.class, () -> adminService.createSeason(showId, seasonRequest(-2, null)));
        assertThrows(ResourceNotFoundException.class,
                () -> adminService.createSeason(UUID.randomUUID().toString(), seasonRequest(1, null)));
    }

    @Test
    void updateSeason_allowsSameNumberButRejectsCollision() {
        SeasonResponse s1 = adminService.createSeason(showId, seasonRequest(1, null));
        adminService.createSeason(showId, seasonRequest(2, null));

        SeasonResponse renamed = adminService.updateSeason(s1.id(), seasonRequest(1, "The Beginning"));
        assertEquals("The Beginning", renamed.title());

        assertThrows(BadRequestException.class, () -> adminService.updateSeason(s1.id(), seasonRequest(2, null)));

        SeasonResponse moved = adminService.updateSeason(s1.id(), seasonRequest(3, null));
        assertEquals(3, moved.seasonNumber());
    }

    @Test
    void seasonsCount_tracksSeasonCrud() {
        SeasonResponse s1 = adminService.createSeason(showId, seasonRequest(1, null));
        adminService.createSeason(showId, seasonRequest(2, null));

        assertEquals(2, findListed().seasonsCount());
        assertEquals(2, adminService.getTvShowDetail(showId).seasonsCount());

        adminService.deleteSeason(s1.id());
        assertEquals(1, findListed().seasonsCount());
    }

    @Test
    void createTvShow_withSeasonsCountCreatesNumberedSeasons() {
        TvShowResponse show = adminService.createTvShow(new CreateTvShowRequest("Three Seasons", null, null, null,
                null, null, null, null, null, 3));
        assertEquals(3, show.seasonsCount());
        assertEquals(List.of(1, 2, 3), adminService.getTvShowDetail(show.id()).seasons().stream()
                .map(SeasonResponse::seasonNumber).toList());
    }

    @Test
    void episodes_crudWithValidation() {
        SeasonResponse season = adminService.createSeason(showId, seasonRequest(2, null));

        EpisodeResponse e1 = adminService.createEpisode(season.id(), episodeRequest(1, "Pilot", 45));
        assertEquals(showId, e1.tvShowId());
        assertEquals(2, e1.seasonNumber());
        assertEquals(45, e1.runtimeMinutes());

        EpisodeResponse e2 = adminService.createEpisode(season.id(), episodeRequest(2, "Second", null));

        assertThrows(BadRequestException.class,
                () -> adminService.createEpisode(season.id(), episodeRequest(1, "Dup", null)));
        assertThrows(BadRequestException.class,
                () -> adminService.createEpisode(season.id(), episodeRequest(0, "Zero", null)));
        assertThrows(BadRequestException.class,
                () -> adminService.createEpisode(season.id(), episodeRequest(3, "Neg runtime", -5)));
        assertThrows(BadRequestException.class,
                () -> adminService.createEpisode(season.id(), episodeRequest(3, "  ", null)));
        assertThrows(BadRequestException.class,
                () -> adminService.updateEpisode(e2.id(), episodeRequest(1, "Collide", null)));

        EpisodeResponse updated = adminService.updateEpisode(e2.id(), episodeRequest(2, "Second (Director's Cut)", 50));
        assertEquals("Second (Director's Cut)", updated.title());

        List<EpisodeResponse> listed = adminService.getTvShowDetail(showId).seasons().get(0).episodes();
        assertEquals(List.of(1, 2), listed.stream().map(EpisodeResponse::episodeNumber).toList());

        adminService.deleteEpisode(e1.id());
        assertEquals(1, adminService.getTvShowDetail(showId).seasons().get(0).episodes().size());
        assertThrows(ResourceNotFoundException.class, () -> adminService.deleteEpisode(e1.id()));
    }

    @Test
    void deleteSeason_cascadesEpisodes() {
        SeasonResponse season = adminService.createSeason(showId, seasonRequest(1, null));
        EpisodeResponse e1 = adminService.createEpisode(season.id(), episodeRequest(1, "One", null));
        EpisodeResponse e2 = adminService.createEpisode(season.id(), episodeRequest(2, "Two", null));

        adminService.deleteSeason(season.id());

        assertFalse(seasonRepository.existsById(UUID.fromString(season.id())));
        assertFalse(episodeRepository.existsById(UUID.fromString(e1.id())));
        assertFalse(episodeRepository.existsById(UUID.fromString(e2.id())));
        assertThrows(ResourceNotFoundException.class, () -> adminService.deleteSeason(season.id()));
    }

    private TvShowResponse findListed() {
        return adminService.listAllTvShows().stream()
                .filter(show -> show.id().equals(showId))
                .findFirst()
                .orElseThrow();
    }

    private static SeasonRequest seasonRequest(int number, String title) {
        return new SeasonRequest(number, title, null, LocalDate.of(2024, 1, 1), null);
    }

    private static EpisodeRequest episodeRequest(int number, String title, Integer runtime) {
        return new EpisodeRequest(number, title, null, runtime, null, null);
    }
}
