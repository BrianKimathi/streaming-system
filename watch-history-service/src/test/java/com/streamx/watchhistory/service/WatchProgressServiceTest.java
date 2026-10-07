package com.streamx.watchhistory.service;

import com.streamx.watchhistory.dto.RecordProgressRequest;
import com.streamx.watchhistory.dto.WatchProgressResponse;
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
class WatchProgressServiceTest {

    @Autowired
    private WatchProgressService watchProgressService;

    @Test
    void testRecordProgressAndContinueWatching() {
        String accountId = UUID.randomUUID().toString();
        String profileId = UUID.randomUUID().toString();
        String contentId = UUID.randomUUID().toString();

        RecordProgressRequest req1 = new RecordProgressRequest(contentId, null, 1200L, 7200L); // 20 mins into 2h movie
        WatchProgressResponse res1 = watchProgressService.recordProgress(accountId, profileId, req1);

        assertNotNull(res1.getId());
        assertEquals(1200L, res1.getPositionSeconds());
        assertFalse(res1.isCompleted());

        // Update progress to 2852 seconds (47m 32s)
        RecordProgressRequest req2 = new RecordProgressRequest(contentId, null, 2852L, 7200L);
        WatchProgressResponse res2 = watchProgressService.recordProgress(accountId, profileId, req2);

        assertEquals(2852L, res2.getPositionSeconds());

        // Verify Continue Watching for profile
        List<WatchProgressResponse> continueWatching = watchProgressService.getContinueWatching(profileId);
        assertEquals(1, continueWatching.size());
        assertEquals(2852L, continueWatching.get(0).getPositionSeconds());
    }

    @Test
    void testProgressCompletionThreshold() {
        String accountId = UUID.randomUUID().toString();
        String profileId = UUID.randomUUID().toString();
        String contentId = UUID.randomUUID().toString();

        // 92% progress (6624s / 7200s)
        RecordProgressRequest req = new RecordProgressRequest(contentId, null, 6624L, 7200L);
        WatchProgressResponse res = watchProgressService.recordProgress(accountId, profileId, req);

        assertTrue(res.isCompleted());

        // Completed item should no longer appear in Continue Watching carousel
        List<WatchProgressResponse> continueWatching = watchProgressService.getContinueWatching(profileId);
        assertTrue(continueWatching.isEmpty());
    }
}
