package com.streamx.trending.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.trending.dto.RecordEventRequestDto;
import com.streamx.trending.dto.TrendingItemResponseDto;
import com.streamx.trending.service.TrendingService;
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
            @RequestParam(defaultValue = "10") int limit) {
        List<TrendingItemResponseDto> items = trendingService.getTopTrending(limit);
        return ResponseEntity.ok(ApiResponse.success("Top trending items retrieved successfully", items));
    }

    @PostMapping("/record")
    public ResponseEntity<ApiResponse<TrendingItemResponseDto>> recordEvent(
            @RequestBody RecordEventRequestDto request) {
        TrendingItemResponseDto response = trendingService.recordEvent(request);
        return ResponseEntity.ok(ApiResponse.success("Event recorded successfully", response));
    }
}
