package com.streamx.trending.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.trending.dto.RecordTrendingEventRequest;
import com.streamx.trending.dto.TrendingItemResponseDto;
import com.streamx.trending.service.TrendingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trending")
public class TrendingController {

    private final TrendingService trendingService;

    public TrendingController(TrendingService trendingService) {
        this.trendingService = trendingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrendingItemResponseDto>>> getTopTrending(
            @RequestParam(defaultValue = "20") int limit) {
        int clamped = Math.clamp(limit, TrendingService.MIN_LIMIT, TrendingService.MAX_LIMIT);
        List<TrendingItemResponseDto> items = trendingService.getTopTrending(clamped);
        return ResponseEntity.ok(ApiResponse.success("Top trending items retrieved successfully", items));
    }

    /** Service-to-service only (playback, watch-history); the gateway returns 404 for {@code /trending/internal/**}. */
    @PostMapping("/internal/record")
    public ResponseEntity<ApiResponse<TrendingItemResponseDto>> recordEvent(
            @Valid @RequestBody RecordTrendingEventRequest request) {
        TrendingItemResponseDto response = trendingService.recordEvent(request);
        return ResponseEntity.ok(ApiResponse.success("Event recorded successfully", response));
    }
}
