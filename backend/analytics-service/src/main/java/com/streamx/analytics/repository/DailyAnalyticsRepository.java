package com.streamx.analytics.repository;

import com.streamx.analytics.domain.DailyAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyAnalyticsRepository extends JpaRepository<DailyAnalytics, UUID> {
    Optional<DailyAnalytics> findByDate(LocalDate date);
    Optional<DailyAnalytics> findFirstByOrderByDateDesc();
}
