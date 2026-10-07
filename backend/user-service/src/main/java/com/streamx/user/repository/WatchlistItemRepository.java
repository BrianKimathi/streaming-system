package com.streamx.user.repository;

import com.streamx.user.domain.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, UUID> {
    List<WatchlistItem> findByProfileIdOrderByAddedAtDesc(UUID profileId);
    Optional<WatchlistItem> findByProfileIdAndTitleId(UUID profileId, UUID titleId);
    void deleteByProfileId(UUID profileId);
}
