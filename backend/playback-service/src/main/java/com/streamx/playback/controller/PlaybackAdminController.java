package com.streamx.playback.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.playback.service.PlaybackAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/playback/admin")
public class PlaybackAdminController {

    private final PlaybackAdminService playbackAdminService;

    public PlaybackAdminController(PlaybackAdminService playbackAdminService) {
        this.playbackAdminService = playbackAdminService;
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(playbackAdminService.getStats()));
    }
}
