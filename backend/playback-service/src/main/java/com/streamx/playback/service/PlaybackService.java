package com.streamx.playback.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.playback.domain.PlaybackSession;
import com.streamx.playback.domain.PlaybackStatus;
import com.streamx.playback.dto.PlaybackAuthRequest;
import com.streamx.playback.dto.PlaybackAuthResponse;
import com.streamx.playback.repository.PlaybackSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PlaybackService {

    private static final Logger log = LoggerFactory.getLogger(PlaybackService.class);

    private final PlaybackSessionRepository repository;

    public PlaybackService(PlaybackSessionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PlaybackAuthResponse authorizePlayback(String accountIdStr, String profileIdStr, PlaybackAuthRequest request) {
        UUID accountId = UUID.fromString(accountIdStr);
        UUID profileId = UUID.fromString(profileIdStr);
        UUID contentId = UUID.fromString(request.getContentId());

        long activeStreams = repository.countByAccountIdAndStatus(accountId, PlaybackStatus.ACTIVE);
        if (activeStreams >= request.getMaxConcurrentStreams()) {
            throw new BadRequestException("Maximum concurrent stream limit (" + request.getMaxConcurrentStreams() + ") reached for subscription.");
        }

        PlaybackSession session = new PlaybackSession();
        session.setAccountId(accountId);
        session.setProfileId(profileId);
        session.setContentId(contentId);
        session.setDeviceId(request.getDeviceId());
        session.setStatus(PlaybackStatus.ACTIVE);

        PlaybackSession saved = repository.save(session);
        log.info("Created playback session {} for contentId {}", saved.getId(), contentId);

        String streamUrl = "/api/v1/media/" + contentId + "/hls/master.m3u8?token=" + saved.getId();

        return new PlaybackAuthResponse(
                saved.getId().toString(),
                contentId.toString(),
                streamUrl,
                saved.getStatus(),
                saved.getExpiresAt()
        );
    }

    @Transactional
    public void heartbeat(String sessionIdStr) {
        UUID sessionId = UUID.fromString(sessionIdStr);
        PlaybackSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Playback session not found"));

        if (session.getStatus() == PlaybackStatus.ACTIVE) {
            session.setLastHeartbeat(LocalDateTime.now());
            repository.save(session);
        }
    }

    @Transactional
    public void endSession(String sessionIdStr) {
        UUID sessionId = UUID.fromString(sessionIdStr);
        PlaybackSession session = repository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Playback session not found"));

        session.setStatus(PlaybackStatus.ENDED);
        repository.save(session);
        log.info("Ended playback session {}", sessionIdStr);
    }
}
