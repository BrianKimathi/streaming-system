package com.streamx.analytics.service;

import com.streamx.analytics.domain.DailyAnalytics;
import com.streamx.analytics.dto.AnalyticsDashboardDto;
import com.streamx.analytics.dto.RecordStreamEventDto;
import com.streamx.analytics.repository.DailyAnalyticsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private DailyAnalyticsRepository repository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getDashboard_WithExistingData_ReturnsDto() {
        LocalDate today = LocalDate.now();
        DailyAnalytics analytics = new DailyAnalytics(today);
        analytics.setDailyActiveUsers(500);
        analytics.setTotalWatchTimeSeconds(7200); // 2 hours

        when(repository.findByDate(today)).thenReturn(Optional.of(analytics));

        AnalyticsDashboardDto dto = analyticsService.getDashboard(today);

        assertNotNull(dto);
        assertEquals(today, dto.getDate());
        assertEquals(500, dto.getDailyActiveUsers());
        assertEquals(2.0, dto.getTotalWatchTimeHours());
    }

    @Test
    void getDashboard_WithNoData_ReturnsDefaultFallback() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        when(repository.findByDate(date)).thenReturn(Optional.empty());
        when(repository.findFirstByOrderByDateDesc()).thenReturn(Optional.empty());

        AnalyticsDashboardDto dto = analyticsService.getDashboard(date);

        assertNotNull(dto);
        assertEquals(1250, dto.getDailyActiveUsers());
        assertEquals(15400, dto.getMonthlyActiveUsers());
    }

    @Test
    void recordStreamEvent_UpdatesMetricsCorrectly() {
        LocalDate today = LocalDate.now();
        DailyAnalytics analytics = new DailyAnalytics(today);

        RecordStreamEventDto event = new RecordStreamEventDto(1800, 80.0, true, 9.99);

        when(repository.findByDate(today)).thenReturn(Optional.of(analytics));
        when(repository.save(any(DailyAnalytics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnalyticsDashboardDto dto = analyticsService.recordStreamEvent(event);

        assertEquals(1, dto.getDailyActiveUsers());
        assertEquals(1, dto.getTotalStreamsStarted());
        assertEquals(0.5, dto.getTotalWatchTimeHours());
        assertEquals(80.0, dto.getAverageCompletionRatePercentage());
        assertEquals(9.99, dto.getTotalRevenue());
    }
}
