package com.streamx.playback.repository;

import com.streamx.playback.domain.PlaybackSession;
import com.streamx.playback.domain.PlaybackStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlaybackSessionRepository extends JpaRepository<PlaybackSession, UUID> {
    long countByAccountIdAndStatus(UUID accountId, PlaybackStatus status);
    List<PlaybackSession> findByAccountIdAndStatus(UUID accountId, PlaybackStatus status);

    @Modifying
    @Query("update PlaybackSession s set s.status = com.streamx.playback.domain.PlaybackStatus.ENDED, " +
            "s.terminationReason = 'HEARTBEAT_TIMEOUT' " +
            "where s.status = com.streamx.playback.domain.PlaybackStatus.ACTIVE and s.lastHeartbeat < :cutoff")
    int expireStaleSessions(@Param("cutoff") LocalDateTime cutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PlaybackSession s set s.status = com.streamx.playback.domain.PlaybackStatus.ENDED, " +
            "s.terminationReason = 'REPLACED_ON_DEVICE' " +
            "where s.status = com.streamx.playback.domain.PlaybackStatus.ACTIVE " +
            "and s.accountId = :accountId and s.deviceId = :deviceId")
    int endActiveSessionsOnDevice(@Param("accountId") UUID accountId, @Param("deviceId") String deviceId);

    long countByStatusAndLastHeartbeatGreaterThanEqual(PlaybackStatus status, LocalDateTime cutoff);

    long countByStartTimeGreaterThanEqual(LocalDateTime since);

    @Query("select count(distinct s.accountId) from PlaybackSession s where s.startTime >= :since")
    long countDistinctAccountsSince(@Param("since") LocalDateTime since);

    @Query("select s.startTime from PlaybackSession s where s.startTime >= :since")
    List<LocalDateTime> findStartTimesSince(@Param("since") LocalDateTime since);

    @Query("select s.contentId, count(s) from PlaybackSession s where s.startTime >= :since " +
            "group by s.contentId order by count(s) desc")
    List<Object[]> topContentSince(@Param("since") LocalDateTime since, Pageable pageable);
}
