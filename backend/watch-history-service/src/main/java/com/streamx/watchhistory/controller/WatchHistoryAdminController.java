package com.streamx.watchhistory.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.watchhistory.service.WatchHistoryAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/watch-history/admin")
public class WatchHistoryAdminController {

    private final WatchHistoryAdminService watchHistoryAdminService;

    public WatchHistoryAdminController(WatchHistoryAdminService watchHistoryAdminService) {
        this.watchHistoryAdminService = watchHistoryAdminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(watchHistoryAdminService.getStats()));
    }
}
