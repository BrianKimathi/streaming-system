package com.streamx.trending.repository;

import com.streamx.trending.domain.TrendingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrendingItemRepository extends JpaRepository<TrendingItem, UUID> {
    Optional<TrendingItem> findByContentId(UUID contentId);

    @Query("SELECT t FROM TrendingItem t ORDER BY t.velocityScore DESC")
    List<TrendingItem> findTopTrending();
}
