package com.streamx.analytics.controller;

import com.streamx.analytics.dto.AnalyticsDashboardDto;
import com.streamx.analytics.dto.RecordStreamEventDto;
import com.streamx.analytics.service.AnalyticsService;
import com.streamx.common.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AnalyticsDashboardDto>> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        AnalyticsDashboardDto dashboard = analyticsService.getDashboard(date);
        return ResponseEntity.ok(ApiResponse.success("Analytics dashboard data retrieved successfully", dashboard));
    }

    @PostMapping("/record")
    public ResponseEntity<ApiResponse<AnalyticsDashboardDto>> recordEvent(
            @RequestBody RecordStreamEventDto request) {
        AnalyticsDashboardDto updated = analyticsService.recordStreamEvent(request);
        return ResponseEntity.ok(ApiResponse.success("Stream event recorded for analytics successfully", updated));
    }
}
