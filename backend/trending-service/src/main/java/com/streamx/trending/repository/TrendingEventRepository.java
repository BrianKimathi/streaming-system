package com.streamx.trending.repository;

import com.streamx.trending.domain.TrendingEvent;
import com.streamx.trending.domain.TrendingEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrendingEventRepository extends JpaRepository<TrendingEvent, UUID> {

    Duration VIEWS_SHORT_WINDOW = Duration.ofHours(1);
    Duration VIEWS_LONG_WINDOW = Duration.ofHours(6);
    Duration COMPLETIONS_WINDOW = Duration.ofHours(24);
    Duration RETENTION = Duration.ofHours(48);

    String ACTIVITY_SELECT = "SELECT new com.streamx.trending.repository.TitleActivity(e.titleId, "
            + "SUM(CASE WHEN e.eventType = :view AND e.occurredAt >= :since1h THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN e.eventType = :view AND e.occurredAt >= :since6h THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN e.eventType = :completion AND e.occurredAt >= :since24h THEN 1L ELSE 0L END)) "
            + "FROM TrendingEvent e WHERE e.occurredAt >= :since48h ";

    @Query(ACTIVITY_SELECT + "GROUP BY e.titleId")
    List<TitleActivity> aggregateActivity(@Param("view") TrendingEventType view,
                                          @Param("completion") TrendingEventType completion,
                                          @Param("since1h") Instant since1h,
                                          @Param("since6h") Instant since6h,
                                          @Param("since24h") Instant since24h,
                                          @Param("since48h") Instant since48h);

    @Query(ACTIVITY_SELECT + "AND e.titleId = :titleId GROUP BY e.titleId")
    List<TitleActivity> aggregateActivityForTitle(@Param("titleId") UUID titleId,
                                                  @Param("view") TrendingEventType view,
                                                  @Param("completion") TrendingEventType completion,
                                                  @Param("since1h") Instant since1h,
                                                  @Param("since6h") Instant since6h,
                                                  @Param("since24h") Instant since24h,
                                                  @Param("since48h") Instant since48h);

    /** Activity of every title with at least one event in the retention window. */
    default List<TitleActivity> activitySince(Instant now) {
        return aggregateActivity(TrendingEventType.VIEW, TrendingEventType.COMPLETION,
                now.minus(VIEWS_SHORT_WINDOW), now.minus(VIEWS_LONG_WINDOW), now.minus(COMPLETIONS_WINDOW),
                now.minus(RETENTION));
    }

    /** Empty when the title has no events in the retention window. */
    default Optional<TitleActivity> activityFor(UUID titleId, Instant now) {
        return aggregateActivityForTitle(titleId, TrendingEventType.VIEW, TrendingEventType.COMPLETION,
                now.minus(VIEWS_SHORT_WINDOW), now.minus(VIEWS_LONG_WINDOW), now.minus(COMPLETIONS_WINDOW),
                now.minus(RETENTION)).stream().findFirst();
    }

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TrendingEvent e WHERE e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TrendingEvent e WHERE e.titleId = :titleId")
    int deleteByTitle(@Param("titleId") UUID titleId);
}
