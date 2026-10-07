package com.streamx.analytics.service;

import com.streamx.analytics.domain.DailyAnalytics;
import com.streamx.analytics.dto.AnalyticsDashboardDto;
import com.streamx.analytics.dto.RecordStreamEventDto;
import com.streamx.analytics.repository.DailyAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class AnalyticsService {

    private final DailyAnalyticsRepository repository;

    public AnalyticsService(DailyAnalyticsRepository repository) {
        this.repository = repository;
    }

    public AnalyticsDashboardDto getDashboard(LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        DailyAnalytics analytics = repository.findByDate(targetDate)
                .orElseGet(() -> repository.findFirstByOrderByDateDesc()
                        .orElseGet(() -> createDefaultAnalytics(targetDate)));

        return mapToDto(analytics);
    }

    @Transactional
    public AnalyticsDashboardDto recordStreamEvent(RecordStreamEventDto request) {
        LocalDate today = LocalDate.now();
        DailyAnalytics analytics = repository.findByDate(today)
                .orElseGet(() -> new DailyAnalytics(today));

        analytics.setTotalStreamsStarted(analytics.getTotalStreamsStarted() + 1);
        analytics.setTotalWatchTimeSeconds(analytics.getTotalWatchTimeSeconds() + request.getWatchTimeSeconds());

        if (request.isNewActiveUser()) {
            analytics.setDailyActiveUsers(analytics.getDailyActiveUsers() + 1);
            analytics.setMonthlyActiveUsers(analytics.getMonthlyActiveUsers() + 1);
        }

        if (request.getSubscriptionPaymentAmount() > 0) {
            analytics.setTotalRevenue(analytics.getTotalRevenue() + request.getSubscriptionPaymentAmount());
            analytics.setTotalSubscriptionsActive(analytics.getTotalSubscriptionsActive() + 1);
        }

        // Recalculate average completion rate
        long streams = analytics.getTotalStreamsStarted();
        double currentAvg = analytics.getAverageCompletionRate();
        double newAvg = ((currentAvg * (streams - 1)) + request.getCompletionPercentage()) / streams;
        analytics.setAverageCompletionRate(newAvg);

        DailyAnalytics saved = repository.save(analytics);
        return mapToDto(saved);
    }

    private DailyAnalytics createDefaultAnalytics(LocalDate date) {
        DailyAnalytics analytics = new DailyAnalytics(date);
        analytics.setDailyActiveUsers(1250);
        analytics.setMonthlyActiveUsers(15400);
        analytics.setTotalWatchTimeSeconds(4500000);
        analytics.setAverageCompletionRate(78.5);
        analytics.setTotalStreamsStarted(3200);
        analytics.setTotalSubscriptionsActive(14200);
        analytics.setTotalRevenue(141858.0);
        return analytics;
    }

    private AnalyticsDashboardDto mapToDto(DailyAnalytics analytics) {
        double watchTimeHours = Math.round((analytics.getTotalWatchTimeSeconds() / 3600.0) * 100.0) / 100.0;
        return new AnalyticsDashboardDto(
                analytics.getDate(),
                analytics.getDailyActiveUsers(),
                analytics.getMonthlyActiveUsers(),
                watchTimeHours,
                analytics.getAverageCompletionRate(),
                analytics.getTotalStreamsStarted(),
                analytics.getTotalSubscriptionsActive(),
                analytics.getTotalRevenue()
        );
    }
}
