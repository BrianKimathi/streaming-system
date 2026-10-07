package com.streamx.watchhistory.repository;

import com.streamx.watchhistory.domain.WatchProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WatchProgressRepository extends JpaRepository<WatchProgress, UUID> {
    Optional<WatchProgress> findByProfileIdAndContentId(UUID profileId, UUID contentId);

    List<WatchProgress> findByProfileIdAndCompletedFalseOrderByLastWatchedAtDesc(UUID profileId, Pageable pageable);

    List<WatchProgress> findByProfileIdOrderByLastWatchedAtDesc(UUID profileId);

    List<WatchProgress> findByProfileIdAndTitleIdOrderByLastWatchedAtDesc(UUID profileId, UUID titleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WatchProgress w where w.profileId = :profileId and w.titleId = :titleId")
    int deleteByProfileAndTitle(@Param("profileId") UUID profileId, @Param("titleId") UUID titleId);

    @Modifying
    @Query("update WatchProgress w set w.titleId = w.contentId, " +
            "w.titleType = com.streamx.watchhistory.domain.TitleType.SERIES " +
            "where w.titleId is null and w.episodeId is not null")
    int backfillSeriesTitles();

    @Modifying
    @Query("update WatchProgress w set w.titleId = w.contentId, " +
            "w.titleType = com.streamx.watchhistory.domain.TitleType.MOVIE " +
            "where w.titleId is null")
    int backfillMovieTitles();

    long countByCompletedTrue();

    long countByLastWatchedAtGreaterThanEqual(LocalDateTime since);

    @Query("select coalesce(sum(w.positionSeconds), 0) from WatchProgress w")
    long sumPositionSeconds();

    @Query("select coalesce(sum(w.positionSeconds), 0) from WatchProgress w where w.lastWatchedAt >= :since")
    long sumPositionSecondsSince(@Param("since") LocalDateTime since);

    @Query("select count(distinct w.profileId) from WatchProgress w where w.lastWatchedAt >= :since")
    long countDistinctProfilesSince(@Param("since") LocalDateTime since);

    @Query("select coalesce(avg(w.percentage), 0) from WatchProgress w")
    double averagePercentage();

    /** Per title: distinct viewing profiles and completed items (movie completions or completed episodes). */
    @Query("select w.titleId, count(distinct w.profileId), sum(case when w.completed = true then 1 else 0 end) " +
            "from WatchProgress w where w.titleId is not null " +
            "group by w.titleId order by count(distinct w.profileId) desc")
    List<Object[]> topTitles(Pageable pageable);
}
