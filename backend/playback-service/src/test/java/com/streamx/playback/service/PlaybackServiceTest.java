package com.streamx.playback.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.playback.domain.PlaybackStatus;
import com.streamx.playback.dto.PlaybackAuthRequest;
import com.streamx.playback.dto.PlaybackAuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlaybackServiceTest {

    @Autowired
    private PlaybackService playbackService;

    @Test
    void testAuthorizePlaybackAndConcurrentLimits() {
        String accountId = UUID.randomUUID().toString();
        String profileId = UUID.randomUUID().toString();
        String contentId1 = UUID.randomUUID().toString();
        String contentId2 = UUID.randomUUID().toString();
        String contentId3 = UUID.randomUUID().toString();

        PlaybackAuthRequest req1 = new PlaybackAuthRequest(contentId1, "TV_1", 2);
        PlaybackAuthRequest req2 = new PlaybackAuthRequest(contentId2, "PHONE_1", 2);
        PlaybackAuthRequest req3 = new PlaybackAuthRequest(contentId3, "LAPTOP_1", 2);

        PlaybackAuthResponse res1 = playbackService.authorizePlayback(accountId, profileId, req1);
        assertNotNull(res1.getSessionId());
        assertEquals(PlaybackStatus.ACTIVE, res1.getStatus());

        PlaybackAuthResponse res2 = playbackService.authorizePlayback(accountId, profileId, req2);
        assertNotNull(res2.getSessionId());

        // 3rd concurrent stream attempt should throw BadRequestException when limit is 2
        assertThrows(BadRequestException.class, () -> playbackService.authorizePlayback(accountId, profileId, req3));

        // End 1st session
        playbackService.endSession(res1.getSessionId());

        // Now 3rd stream attempt succeeds!
        PlaybackAuthResponse res3 = playbackService.authorizePlayback(accountId, profileId, req3);
        assertNotNull(res3.getSessionId());
    }
}
