package com.streamx.playback.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.playback.dto.PlaybackAuthRequest;
import com.streamx.playback.dto.PlaybackAuthResponse;
import com.streamx.playback.service.PlaybackService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/playback")
public class PlaybackController {

    private final PlaybackService playbackService;

    public PlaybackController(PlaybackService playbackService) {
        this.playbackService = playbackService;
    }

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<PlaybackAuthResponse>> authorizePlayback(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @RequestHeader(SecurityConstants.HEADER_X_PROFILE_ID) String profileId,
            @Valid @RequestBody PlaybackAuthRequest request) {
        PlaybackAuthResponse response = playbackService.authorizePlayback(accountId, profileId, request);
        return ResponseEntity.ok(ApiResponse.success("Playback authorized", response));
    }

    @PostMapping("/sessions/{id}/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(@PathVariable("id") String sessionId) {
        playbackService.heartbeat(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Heartbeat recorded", null));
    }

    @PostMapping("/sessions/{id}/stop")
    public ResponseEntity<ApiResponse<Void>> endSession(@PathVariable("id") String sessionId) {
        playbackService.endSession(sessionId);
        return ResponseEntity.ok(ApiResponse.success("Playback session ended", null));
    }
}
