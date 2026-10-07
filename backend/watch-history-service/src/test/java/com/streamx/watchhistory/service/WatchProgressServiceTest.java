package com.streamx.watchhistory.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.watchhistory.client.TrendingClient;
import com.streamx.watchhistory.domain.TitleType;
import com.streamx.watchhistory.domain.WatchProgress;
import com.streamx.watchhistory.dto.RecordProgressRequest;
import com.streamx.watchhistory.dto.WatchProgressResponse;
import com.streamx.watchhistory.repository.WatchProgressRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Not @Transactional: completion events are published after commit, so tests must really commit. */
@SpringBootTest
@ActiveProfiles("test")
class WatchProgressServiceTest {

    @Autowired
    private WatchProgressService service;

    @Autowired
    private WatchHistoryAdminService adminService;

    @Autowired
    private WatchProgressRepository repository;

    @MockitoBean
    private TrendingClient trendingClient;

    private final String accountId = UUID.randomUUID().toString();
    private final String profileId = UUID.randomUUID().toString();

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private WatchProgressResponse movie(String profile, String movieId, long position, long duration) {
        return service.recordProgress(accountId, profile, new RecordProgressRequest(movieId, movieId, "MOVIE", position, duration));
    }

    private WatchProgressResponse episode(String profile, String showId, String episodeId, long position, long duration) {
        return service.recordProgress(accountId, profile, new RecordProgressRequest(episodeId, showId, "SERIES", position, duration));
    }

    private static void tick() throws InterruptedException {
        Thread.sleep(5);
    }

    @Test
    void recordsAndUpdatesProgress() {
        String movieId = id();
        WatchProgressResponse first = movie(profileId, movieId, 1200, 7200);
        assertNotNull(first.getId());
        assertEquals(movieId, first.getTitleId());
        assertEquals(TitleType.MOVIE, first.getTitleType());
        assertFalse(first.isCompleted());

        WatchProgressResponse second = movie(profileId, movieId, 2852, 7200);
        assertEquals(first.getId(), second.getId());
        assertEquals(2852, second.getPositionSeconds());

        // Seeking backwards is a real position too.
        assertEquals(600, movie(profileId, movieId, 600, 7200).getPositionSeconds());

        List<WatchProgressResponse> continueWatching = service.getContinueWatching(profileId);
        assertEquals(1, continueWatching.size());
        assertEquals(600, continueWatching.get(0).getPositionSeconds());
        verifyNoInteractions(trendingClient);
    }

    @Test
    void legacyRequestsDefaultToMovieOfTheContent() {
        String contentId = id();
        WatchProgressResponse res = service.recordProgress(accountId, profileId,
                new RecordProgressRequest(contentId, null, null, 10L, 100L));
        assertEquals(contentId, res.getTitleId());
        assertEquals(TitleType.MOVIE, res.getTitleType());
        assertNull(repository.findById(UUID.fromString(res.getId())).orElseThrow().getEpisodeId());
    }

    @Test
    void seriesEpisodesKeepEpisodeId() {
        String showId = id();
        String episodeId = id();
        WatchProgressResponse res = episode(profileId, showId, episodeId, 30, 1500);
        WatchProgress row = repository.findById(UUID.fromString(res.getId())).orElseThrow();
        assertEquals(UUID.fromString(episodeId), row.getEpisodeId());
        assertEquals(UUID.fromString(showId), row.getTitleId());
        assertEquals(TitleType.SERIES, row.getTitleType());
    }

    @Test
    void validatesIdsAndTitleType() {
        assertThrows(BadRequestException.class, () -> service.recordProgress(accountId, profileId,
                new RecordProgressRequest(id(), id(), "PODCAST", 1L, 10L)));
        assertThrows(BadRequestException.class, () -> service.recordProgress(accountId, profileId,
                new RecordProgressRequest("nope", null, null, 1L, 10L)));
        assertThrows(BadRequestException.class, () -> service.recordProgress(accountId, profileId,
                new RecordProgressRequest(id(), "nope", "MOVIE", 1L, 10L)));
    }

    @Test
    void clampsPositionToDuration() {
        WatchProgressResponse res = movie(profileId, id(), 9000, 7200);
        assertEquals(7200, res.getPositionSeconds());
        assertEquals(100.0, res.getPercentage());
        assertTrue(res.isCompleted());
    }

    @Test
    void completionFiresTrendingOnlyOnTransition() {
        String movieId = id();
        UUID title = UUID.fromString(movieId);

        assertFalse(movie(profileId, movieId, 3000, 7200).isCompleted());
        verify(trendingClient, never()).recordCompletion(any());

        assertTrue(movie(profileId, movieId, 6624, 7200).isCompleted()); // 92%
        verify(trendingClient, times(1)).recordCompletion(title);

        movie(profileId, movieId, 7000, 7200);
        verify(trendingClient, times(1)).recordCompletion(title);
        assertTrue(service.getContinueWatching(profileId).isEmpty());

        // Rewatching from the start puts it back in Continue Watching; finishing again is a new completion.
        assertFalse(movie(profileId, movieId, 60, 7200).isCompleted());
        assertEquals(1, service.getContinueWatching(profileId).size());
        movie(profileId, movieId, 7100, 7200);
        verify(trendingClient, times(2)).recordCompletion(title);
    }

    @Test
    void episodeCompletionCountsForTheShow() {
        String showId = id();
        episode(profileId, showId, id(), 1450, 1500);
        verify(trendingClient).recordCompletion(UUID.fromString(showId));
    }

    @Test
    void continueWatchingHasOneEntryPerTitleNewestFirst() throws InterruptedException {
        String showId = id();
        String e1 = id();
        String e2 = id();
        String e3 = id();
        String movieId = id();

        episode(profileId, showId, e1, 100, 1500);
        tick();
        episode(profileId, showId, e2, 200, 1500);
        tick();
        movie(profileId, movieId, 500, 7200);
        tick();
        episode(profileId, showId, e3, 1490, 1500); // finished most recently, so it is skipped

        List<WatchProgressResponse> items = service.getContinueWatching(profileId);
        assertEquals(2, items.size());
        assertEquals(movieId, items.get(0).getTitleId());
        assertEquals(showId, items.get(1).getTitleId());
        assertEquals(e2, items.get(1).getContentId());

        tick();
        episode(profileId, showId, e1, 300, 1500);
        items = service.getContinueWatching(profileId);
        assertEquals(List.of(showId, movieId), items.stream().map(WatchProgressResponse::getTitleId).toList());
        assertEquals(e1, items.get(0).getContentId());
    }

    @Test
    void continueWatchingIsCappedAtTwenty() {
        for (int i = 0; i < 25; i++) {
            movie(profileId, id(), 100, 7200);
        }
        assertEquals(WatchProgressService.CONTINUE_WATCHING_LIMIT, service.getContinueWatching(profileId).size());
    }

    @Test
    void titleProgressListsAllEpisodesAndDeleteRemovesTitle() {
        String showId = id();
        String movieId = id();
        String e1 = id();
        String e2 = id();
        episode(profileId, showId, e1, 1490, 1500);
        episode(profileId, showId, e2, 200, 1500);
        movie(profileId, movieId, 100, 7200);

        List<WatchProgressResponse> rows = service.getTitleProgress(profileId, showId);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> showId.equals(r.getTitleId())));

        assertEquals(2, service.removeTitle(profileId, showId));
        assertTrue(service.getTitleProgress(profileId, showId).isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.getProgress(profileId, e2));
        assertEquals(List.of(movieId),
                service.getContinueWatching(profileId).stream().map(WatchProgressResponse::getTitleId).toList());
        assertEquals(0, service.removeTitle(profileId, showId));
    }

    @Test
    void profilesAreIsolated() {
        String other = id();
        String showId = id();
        String episodeId = id();
        episode(profileId, showId, episodeId, 200, 1500);

        assertTrue(service.getContinueWatching(other).isEmpty());
        assertTrue(service.getTitleProgress(other, showId).isEmpty());
        assertThrows(ResourceNotFoundException.class, () -> service.getProgress(other, episodeId));
        assertEquals(0, service.removeTitle(other, showId));

        // Same content for another profile is a separate row.
        WatchProgressResponse theirs = episode(other, showId, episodeId, 900, 1500);
        WatchProgressResponse mine = service.getProgress(profileId, episodeId);
        assertNotEquals(mine.getId(), theirs.getId());
        assertEquals(200, mine.getPositionSeconds());
        assertEquals(profileId, mine.getProfileId());
    }

    @Test
    void everyEndpointRequiresAProfile() {
        RecordProgressRequest req = new RecordProgressRequest(id(), null, null, 1L, 10L);
        for (String missing : new String[]{null, "", " "}) {
            assertEquals("Select a profile first",
                    assertThrows(BadRequestException.class, () -> service.recordProgress(accountId, missing, req)).getMessage());
            assertEquals("Select a profile first",
                    assertThrows(BadRequestException.class, () -> service.getContinueWatching(missing)).getMessage());
            assertThrows(BadRequestException.class, () -> service.getTitleProgress(missing, id()));
            assertThrows(BadRequestException.class, () -> service.getProgress(missing, id()));
            assertThrows(BadRequestException.class, () -> service.removeTitle(missing, id()));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminTopContentGroupsByTitle() {
        String showId = id();
        String e1 = id();
        String e2 = id();
        for (int i = 0; i < 30; i++) {
            String viewer = id();
            episode(viewer, showId, e1, 1490, 1500);
            episode(viewer, showId, e2, 100, 1500);
        }

        List<Map<String, Object>> top = (List<Map<String, Object>>) adminService.getStats().get("topContent");
        Map<String, Object> show = top.stream().filter(t -> showId.equals(t.get("titleId"))).findFirst().orElseThrow();
        assertEquals(showId, show.get("contentId"));
        assertEquals(30L, ((Number) show.get("viewers")).longValue());
        assertEquals(30L, ((Number) show.get("completions")).longValue());
    }
}
