package com.streamx.playback.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ForbiddenException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.security.JwtUtils;
import com.streamx.common.security.SecurityConstants;
import com.streamx.playback.client.DeviceClient;
import com.streamx.playback.client.DeviceClient.DeviceStatus;
import com.streamx.playback.client.EntitlementClient;
import com.streamx.playback.client.MediaClient;
import com.streamx.playback.client.MediaClient.MediaStatus;
import com.streamx.playback.client.SubscriptionEntitlementClient;
import com.streamx.playback.client.TrendingClient;
import com.streamx.playback.domain.PlaybackStatus;
import com.streamx.playback.dto.PlaybackAuthRequest;
import com.streamx.playback.dto.PlaybackAuthResponse;
import com.streamx.playback.exception.ConflictException;
import com.streamx.playback.exception.ServiceUnavailableException;
import com.streamx.playback.repository.PlaybackSessionRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlaybackServiceTest {

    private static final Pattern STREAM_URL =
            Pattern.compile("^/api/v1/media/stream/([^/]+)/([0-9a-f-]{36})/master\\.m3u8$");

    @Autowired
    private PlaybackService playbackService;

    @Autowired
    private PlaybackSessionRepository repository;

    @Autowired
    private JwtUtils jwtUtils;

    @MockitoBean
    private EntitlementClient entitlementClient;

    @MockitoBean
    private DeviceClient deviceClient;

    @MockitoBean
    private MediaClient mediaClient;

    @MockitoBean
    private TrendingClient trendingClient;

    private final String accountId = UUID.randomUUID().toString();
    private final String profileId = UUID.randomUUID().toString();

    @BeforeEach
    void allowEverything() {
        when(entitlementClient.maxConcurrentStreams(any(UUID.class))).thenReturn(2);
        when(deviceClient.deviceStatus(any(UUID.class), any(UUID.class))).thenReturn(DeviceStatus.ACTIVE);
        when(mediaClient.mediaStatus(any(UUID.class))).thenReturn(Optional.of(new MediaStatus("COMPLETED", 5400)));
    }

    private PlaybackAuthRequest request() {
        return new PlaybackAuthRequest(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    @Test
    void enforcesConcurrentLimitFromSubscriptionEntitlements() {
        PlaybackAuthResponse res1 = playbackService.authorizePlayback(accountId, profileId, request());
        assertEquals(PlaybackStatus.ACTIVE, res1.getStatus());
        playbackService.authorizePlayback(accountId, profileId, request());

        PlaybackAuthRequest third = request();
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> playbackService.authorizePlayback(accountId, profileId, third));
        assertTrue(ex.getMessage().contains("Maximum concurrent stream limit (2)"));

        playbackService.endSession(accountId, res1.getSessionId());
        assertNotNull(playbackService.authorizePlayback(accountId, profileId, third).getSessionId());
    }

    @Test
    void restartingOnTheSameDeviceReplacesItsSession() {
        when(entitlementClient.maxConcurrentStreams(any(UUID.class))).thenReturn(1);
        String deviceId = UUID.randomUUID().toString();

        PlaybackAuthResponse first = playbackService.authorizePlayback(accountId, profileId,
                new PlaybackAuthRequest(UUID.randomUUID().toString(), deviceId));
        PlaybackAuthResponse second = playbackService.authorizePlayback(accountId, profileId,
                new PlaybackAuthRequest(UUID.randomUUID().toString(), deviceId));

        assertNotEquals(first.getSessionId(), second.getSessionId());
        assertEquals(PlaybackStatus.ENDED, repository.findById(UUID.fromString(first.getSessionId())).orElseThrow().getStatus());
        assertThrows(BadRequestException.class, () -> playbackService.authorizePlayback(accountId, profileId, request()));
    }

    @Test
    void requiresProfile() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> playbackService.authorizePlayback(accountId, null, request()));
        assertEquals("Select a profile first", ex.getMessage());
        verifyNoInteractions(entitlementClient, deviceClient, mediaClient, trendingClient);
    }

    @Test
    void subscriptionIsCheckedFirst() {
        when(entitlementClient.maxConcurrentStreams(any(UUID.class)))
                .thenThrow(new BadRequestException(SubscriptionEntitlementClient.SUBSCRIPTION_REQUIRED));
        when(deviceClient.deviceStatus(any(UUID.class), any(UUID.class))).thenReturn(DeviceStatus.REVOKED);
        when(mediaClient.mediaStatus(any(UUID.class))).thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> playbackService.authorizePlayback(accountId, profileId, request()));
        assertEquals("An active subscription is required", ex.getMessage());
        verifyNoInteractions(deviceClient, mediaClient, trendingClient);
        assertEquals(0, repository.countByAccountIdAndStatus(UUID.fromString(accountId), PlaybackStatus.ACTIVE));
    }

    @Test
    void revokedDeviceIsRejectedBeforeMediaCheck() {
        when(deviceClient.deviceStatus(any(UUID.class), any(UUID.class))).thenReturn(DeviceStatus.REVOKED);
        when(mediaClient.mediaStatus(any(UUID.class))).thenReturn(Optional.empty());

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> playbackService.authorizePlayback(accountId, profileId, request()));
        assertEquals("This device has been signed out. Sign in again.", ex.getMessage());
        verifyNoInteractions(mediaClient, trendingClient);
    }

    @Test
    void unknownDeviceIsTreatedAsSignedOut() {
        when(deviceClient.deviceStatus(any(UUID.class), any(UUID.class))).thenReturn(DeviceStatus.NOT_FOUND);
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> playbackService.authorizePlayback(accountId, profileId, request()));
        assertEquals(PlaybackService.DEVICE_SIGNED_OUT, ex.getMessage());
    }

    @Test
    void deviceServiceOutageFailsHonestly() {
        when(deviceClient.deviceStatus(any(UUID.class), any(UUID.class)))
                .thenThrow(new ServiceUnavailableException("Device verification is temporarily unavailable. Try again shortly."));
        assertThrows(ServiceUnavailableException.class,
                () -> playbackService.authorizePlayback(accountId, profileId, request()));
        verifyNoInteractions(mediaClient, trendingClient);
    }

    @Test
    void mediaNotReadyIsAConflict() {
        when(mediaClient.mediaStatus(any(UUID.class))).thenReturn(Optional.of(new MediaStatus("PROCESSING", null)));
        ConflictException processing = assertThrows(ConflictException.class,
                () -> playbackService.authorizePlayback(accountId, profileId, request()));
        assertEquals("This title isn't available to stream yet", processing.getMessage());

        when(mediaClient.mediaStatus(any(UUID.class))).thenReturn(Optional.empty());
        ConflictException missing = assertThrows(ConflictException.class,
                () -> playbackService.authorizePlayback(accountId, profileId, request()));
        assertEquals(PlaybackService.NOT_READY, missing.getMessage());

        verifyNoInteractions(trendingClient);
        assertEquals(0, repository.countByAccountIdAndStatus(UUID.fromString(accountId), PlaybackStatus.ACTIVE));
    }

    @Test
    void mediaCheckComesBeforeStreamLimit() {
        when(entitlementClient.maxConcurrentStreams(any(UUID.class))).thenReturn(1);
        playbackService.authorizePlayback(accountId, profileId, request());

        when(mediaClient.mediaStatus(any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(ConflictException.class, () -> playbackService.authorizePlayback(accountId, profileId, request()));
    }

    @Test
    void issuesContentScopedStreamTokenAndRecordsView() {
        String contentId = UUID.randomUUID().toString();
        String titleId = UUID.randomUUID().toString();

        PlaybackAuthResponse res = playbackService.authorizePlayback(accountId, profileId,
                new PlaybackAuthRequest(contentId, UUID.randomUUID().toString(), titleId));

        Matcher matcher = STREAM_URL.matcher(res.getStreamUrl());
        assertTrue(matcher.matches(), res.getStreamUrl());
        assertEquals(contentId, matcher.group(2));
        assertEquals(contentId, res.getContentId());
        assertEquals(5400, res.getDurationSeconds());
        assertNotNull(res.getExpiresAt());

        String token = matcher.group(1);
        Claims claims = jwtUtils.parseStreamToken(token, contentId);
        assertNotNull(claims);
        assertTrue(JwtUtils.isStreamToken(claims));
        assertEquals(accountId, claims.getSubject());
        assertEquals(res.getSessionId(), claims.get(SecurityConstants.CLAIM_SESSION_ID, String.class));
        long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(PlaybackService.STREAM_TOKEN_TTL_MS, ttlMs);

        assertNull(jwtUtils.parseStreamToken(token, UUID.randomUUID().toString()));

        verify(trendingClient).recordView(UUID.fromString(titleId));
    }

    @Test
    void trendingDefaultsToContentId() {
        PlaybackAuthRequest req = request();
        playbackService.authorizePlayback(accountId, profileId, req);
        verify(trendingClient).recordView(UUID.fromString(req.getContentId()));
    }

    @Test
    void rejectsMalformedDeviceId() {
        PlaybackAuthRequest req = new PlaybackAuthRequest(UUID.randomUUID().toString(), "TV_1");
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> playbackService.authorizePlayback(accountId, profileId, req));
        assertEquals("Invalid device id", ex.getMessage());
    }

    @Test
    void cannotControlAnotherAccountsSession() {
        PlaybackAuthResponse res = playbackService.authorizePlayback(accountId, profileId, request());

        String intruder = UUID.randomUUID().toString();
        assertThrows(ResourceNotFoundException.class, () -> playbackService.endSession(intruder, res.getSessionId()));
        assertThrows(ResourceNotFoundException.class, () -> playbackService.heartbeat(intruder, res.getSessionId()));
        playbackService.heartbeat(accountId, res.getSessionId());
    }
}
