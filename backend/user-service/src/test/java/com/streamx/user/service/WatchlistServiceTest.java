package com.streamx.user.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.user.domain.TitleType;
import com.streamx.user.dto.CreateProfileRequest;
import com.streamx.user.dto.WatchlistItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WatchlistServiceTest {

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private ProfileService profileService;

    private String createProfile(String accountId, String name) {
        CreateProfileRequest request = new CreateProfileRequest();
        request.setName(name);
        return profileService.createProfile(accountId, request).getId();
    }

    @Test
    void addIsIdempotent() {
        String accountId = UUID.randomUUID().toString();
        String profileId = createProfile(accountId, "Viewer");
        String titleId = UUID.randomUUID().toString();

        WatchlistItemResponse first = watchlistService.addToWatchlist(accountId, profileId, titleId, TitleType.MOVIE);
        WatchlistItemResponse second = watchlistService.addToWatchlist(accountId, profileId, titleId, TitleType.MOVIE);

        assertEquals(first.titleId(), second.titleId());
        assertEquals(first.addedAt(), second.addedAt());
        assertEquals(1, watchlistService.getWatchlist(accountId, profileId).size());
    }

    @Test
    void removeIsIdempotent() {
        String accountId = UUID.randomUUID().toString();
        String profileId = createProfile(accountId, "Viewer");
        String titleId = UUID.randomUUID().toString();
        watchlistService.addToWatchlist(accountId, profileId, titleId, TitleType.SERIES);

        watchlistService.removeFromWatchlist(accountId, profileId, titleId);
        watchlistService.removeFromWatchlist(accountId, profileId, titleId);

        assertTrue(watchlistService.getWatchlist(accountId, profileId).isEmpty());
    }

    @Test
    void listIsNewestFirst() throws InterruptedException {
        String accountId = UUID.randomUUID().toString();
        String profileId = createProfile(accountId, "Viewer");
        String older = UUID.randomUUID().toString();
        String newer = UUID.randomUUID().toString();

        watchlistService.addToWatchlist(accountId, profileId, older, TitleType.MOVIE);
        Thread.sleep(20);
        watchlistService.addToWatchlist(accountId, profileId, newer, TitleType.SERIES);

        List<WatchlistItemResponse> items = watchlistService.getWatchlist(accountId, profileId);
        assertEquals(List.of(newer, older), items.stream().map(WatchlistItemResponse::titleId).toList());
        assertEquals(TitleType.SERIES, items.get(0).titleType());
    }

    @Test
    void watchlistsAreIsolatedPerProfile() {
        String accountId = UUID.randomUUID().toString();
        String adult = createProfile(accountId, "Adult");
        String kid = createProfile(accountId, "Kid");
        String titleId = UUID.randomUUID().toString();

        watchlistService.addToWatchlist(accountId, adult, titleId, TitleType.MOVIE);

        assertEquals(1, watchlistService.getWatchlist(accountId, adult).size());
        assertTrue(watchlistService.getWatchlist(accountId, kid).isEmpty());
    }

    @Test
    void cannotUseProfileOfAnotherAccount() {
        String ownerAccount = UUID.randomUUID().toString();
        String profileId = createProfile(ownerAccount, "Owner");
        String otherAccount = UUID.randomUUID().toString();

        assertThrows(ResourceNotFoundException.class, () -> watchlistService.getWatchlist(otherAccount, profileId));
        assertThrows(ResourceNotFoundException.class, () -> watchlistService.addToWatchlist(
                otherAccount, profileId, UUID.randomUUID().toString(), TitleType.MOVIE));
    }

    @Test
    void requiresSelectedProfile() {
        String accountId = UUID.randomUUID().toString();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> watchlistService.getWatchlist(accountId, null));
        assertEquals("Select a profile first", ex.getMessage());
    }

    @Test
    void rejectsInvalidTitleId() {
        String accountId = UUID.randomUUID().toString();
        String profileId = createProfile(accountId, "Viewer");

        assertThrows(BadRequestException.class,
                () -> watchlistService.addToWatchlist(accountId, profileId, "not-a-uuid", TitleType.MOVIE));
    }
}
