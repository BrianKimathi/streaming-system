package com.streamx.playback.repository;

import com.streamx.playback.domain.PlaybackSession;
import com.streamx.playback.domain.PlaybackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlaybackSessionRepository extends JpaRepository<PlaybackSession, UUID> {
    long countByAccountIdAndStatus(UUID accountId, PlaybackStatus status);
    List<PlaybackSession> findByAccountIdAndStatus(UUID accountId, PlaybackStatus status);
}
