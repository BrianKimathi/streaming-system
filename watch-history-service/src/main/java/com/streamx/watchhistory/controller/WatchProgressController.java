package com.streamx.watchhistory.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.watchhistory.dto.RecordProgressRequest;
import com.streamx.watchhistory.dto.WatchProgressResponse;
import com.streamx.watchhistory.service.WatchProgressService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watch-history")
public class WatchProgressController {

    private final WatchProgressService watchProgressService;

    public WatchProgressController(WatchProgressService watchProgressService) {
        this.watchProgressService = watchProgressService;
    }

    @PostMapping("/progress")
    public ResponseEntity<ApiResponse<WatchProgressResponse>> recordProgress(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @RequestHeader(SecurityConstants.HEADER_X_PROFILE_ID) String profileId,
            @Valid @RequestBody RecordProgressRequest request) {
        WatchProgressResponse response = watchProgressService.recordProgress(accountId, profileId, request);
        return ResponseEntity.ok(ApiResponse.success("Watch progress recorded", response));
    }

    @GetMapping("/continue-watching")
    public ResponseEntity<ApiResponse<List<WatchProgressResponse>>> getContinueWatching(
            @RequestHeader(SecurityConstants.HEADER_X_PROFILE_ID) String profileId) {
        List<WatchProgressResponse> response = watchProgressService.getContinueWatching(profileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<WatchProgressResponse>> getProgress(
            @RequestHeader(SecurityConstants.HEADER_X_PROFILE_ID) String profileId,
            @PathVariable("contentId") String contentId) {
        WatchProgressResponse response = watchProgressService.getProgress(profileId, contentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
