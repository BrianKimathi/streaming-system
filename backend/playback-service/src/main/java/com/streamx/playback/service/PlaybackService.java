package com.streamx.playback.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ForbiddenException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.security.JwtUtils;
import com.streamx.playback.client.DeviceClient;
import com.streamx.playback.client.EntitlementClient;
import com.streamx.playback.client.MediaClient;
import com.streamx.playback.client.TrendingClient;
import com.streamx.playback.domain.PlaybackSession;
import com.streamx.playback.domain.PlaybackStatus;
import com.streamx.playback.dto.PlaybackAuthRequest;
import com.streamx.playback.dto.PlaybackAuthResponse;
import com.streamx.playback.exception.ConflictException;
import com.streamx.playback.repository.PlaybackSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PlaybackService {

    public static final String SELECT_PROFILE = "Select a profile first";
    public static final String DEVICE_SIGNED_OUT = "This device has been signed out. Sign in again.";
    public static final String NOT_READY = "This title isn't available to stream yet";
    public static final long STREAM_TOKEN_TTL_MS = 6L * 60 * 60 * 1000;

    private static final Logger log = LoggerFactory.getLogger(PlaybackService.class);

    private final PlaybackSessionRepository repository;
    private final EntitlementClient entitlementClient;
    private final DeviceClient deviceClient;
    private final MediaClient mediaClient;
    private final TrendingClient trendingClient;
    private final JwtUtils jwtUtils;
    private final TransactionTemplate transactionTemplate;
    private final long heartbeatTimeoutSeconds;

    public PlaybackService(PlaybackSessionRepository repository,
                           EntitlementClient entitlementClient,
                           DeviceClient deviceClient,
                           MediaClient mediaClient,
                           TrendingClient trendingClient,
                           JwtUtils jwtUtils,
                           PlatformTransactionManager transactionManager,
                           @Value("${playback.heartbeat-timeout-seconds:120}") long heartbeatTimeoutSeconds) {
        this.repository = repository;
        this.entitlementClient = entitlementClient;
        this.deviceClient = deviceClient;
        this.mediaClient = mediaClient;
        this.trendingClient = trendingClient;
        this.jwtUtils = jwtUtils;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    public LocalDateTime heartbeatCutoff() {
        return LocalDateTime.now().minusSeconds(heartbeatTimeoutSeconds);
    }

    public static String streamUrl(String streamToken, UUID contentId) {
        return "/api/v1/media/stream/" + streamToken + "/" + contentId + "/master.m3u8";
    }

    /**
     * Checks run in a fixed order (subscription, device, media readiness, concurrent streams) so the client always
     * gets the most actionable error. Remote checks run outside the DB transaction.
     */
    public PlaybackAuthResponse authorizePlayback(String accountIdStr, String profileIdStr, PlaybackAuthRequest request) {
        if (profileIdStr == null || profileIdStr.isBlank()) {
            throw new BadRequestException(SELECT_PROFILE);
        }
        UUID accountId = parse(accountIdStr, "account id");
        UUID profileId = parse(profileIdStr, "profile id");
        UUID contentId = parse(request.getContentId(), "content id");
        UUID deviceId = parse(request.getDeviceId(), "device id");
        UUID titleId = request.getTitleId() == null || request.getTitleId().isBlank()
                ? contentId
                : parse(request.getTitleId(), "title id");

        int maxStreams = entitlementClient.maxConcurrentStreams(accountId);

        if (deviceClient.deviceStatus(accountId, deviceId) != DeviceClient.DeviceStatus.ACTIVE) {
            throw new ForbiddenException(DEVICE_SIGNED_OUT);
        }

        MediaClient.MediaStatus media = mediaClient.mediaStatus(contentId)
                .filter(MediaClient.MediaStatus::isReady)
                .orElseThrow(() -> new ConflictException(NOT_READY));

        PlaybackSession saved = transactionTemplate.execute(status ->
                startSession(accountId, profileId, contentId, deviceId.toString(), maxStreams));

        String streamToken = jwtUtils.generateStreamToken(
                accountId.toString(), contentId.toString(), saved.getId().toString(), STREAM_TOKEN_TTL_MS);
        log.info("Created playback session {} for contentId {}", saved.getId(), contentId);

        trendingClient.recordView(titleId);

        return new PlaybackAuthResponse(
                saved.getId().toString(),
                contentId.toString(),
                streamUrl(streamToken, contentId),
                saved.getStatus(),
                saved.getExpiresAt(),
                media.durationSeconds()
        );
    }

    private PlaybackSession startSession(UUID accountId, UUID profileId, UUID contentId, String deviceId, int maxStreams) {
        repository.expireStaleSessions(heartbeatCutoff());
        // A device plays one stream at a time; restarting playback on it replaces its previous session.
        repository.endActiveSessionsOnDevice(accountId, deviceId);

        long activeStreams = repository.countByAccountIdAndStatus(accountId, PlaybackStatus.ACTIVE);
        if (activeStreams >= maxStreams) {
            throw new BadRequestException("Maximum concurrent stream limit (" + maxStreams + ") reached for subscription.");
        }

        PlaybackSession session = new PlaybackSession();
        session.setAccountId(accountId);
        session.setProfileId(profileId);
        session.setContentId(contentId);
        session.setDeviceId(deviceId);
        session.setStatus(PlaybackStatus.ACTIVE);
        session.setExpiresAt(LocalDateTime.now().plusSeconds(STREAM_TOKEN_TTL_MS / 1000));
        return repository.save(session);
    }

    @Transactional
    public void heartbeat(String accountIdStr, String sessionIdStr) {
        PlaybackSession session = ownedSession(accountIdStr, sessionIdStr);
        if (session.getStatus() == PlaybackStatus.ACTIVE) {
            session.setLastHeartbeat(LocalDateTime.now());
            repository.save(session);
        }
    }

    @Transactional
    public void endSession(String accountIdStr, String sessionIdStr) {
        PlaybackSession session = ownedSession(accountIdStr, sessionIdStr);
        session.setStatus(PlaybackStatus.ENDED);
        repository.save(session);
        log.info("Ended playback session {}", sessionIdStr);
    }

    private PlaybackSession ownedSession(String accountIdStr, String sessionIdStr) {
        UUID accountId = parse(accountIdStr, "account id");
        UUID sessionId = parse(sessionIdStr, "session id");
        PlaybackSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Playback session not found"));
        if (!session.getAccountId().equals(accountId)) {
            throw new ResourceNotFoundException("Playback session not found");
        }
        return session;
    }

    private static UUID parse(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid " + label);
        }
    }
}
