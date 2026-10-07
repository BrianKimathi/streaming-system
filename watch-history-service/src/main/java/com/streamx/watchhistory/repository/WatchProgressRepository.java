package com.streamx.watchhistory.repository;

import com.streamx.watchhistory.domain.WatchProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchProgressRepository extends JpaRepository<WatchProgress, UUID> {
    Optional<WatchProgress> findByProfileIdAndContentId(UUID profileId, UUID contentId);
    List<WatchProgress> findByProfileIdAndCompletedFalseOrderByLastWatchedAtDesc(UUID profileId);
    List<WatchProgress> findByProfileIdOrderByLastWatchedAtDesc(UUID profileId);
}
