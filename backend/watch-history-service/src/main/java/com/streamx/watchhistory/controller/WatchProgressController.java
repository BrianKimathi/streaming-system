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
            @RequestHeader(value = SecurityConstants.HEADER_X_PROFILE_ID, required = false) String profileId,
            @Valid @RequestBody RecordProgressRequest request) {
        WatchProgressResponse response = watchProgressService.recordProgress(accountId, profileId, request);
        return ResponseEntity.ok(ApiResponse.success("Watch progress recorded", response));
    }

    @GetMapping("/continue-watching")
    public ResponseEntity<ApiResponse<List<WatchProgressResponse>>> getContinueWatching(
            @RequestHeader(value = SecurityConstants.HEADER_X_PROFILE_ID, required = false) String profileId) {
        return ResponseEntity.ok(ApiResponse.success(watchProgressService.getContinueWatching(profileId)));
    }

    @GetMapping("/titles/{titleId}")
    public ResponseEntity<ApiResponse<List<WatchProgressResponse>>> getTitleProgress(
            @RequestHeader(value = SecurityConstants.HEADER_X_PROFILE_ID, required = false) String profileId,
            @PathVariable("titleId") String titleId) {
        return ResponseEntity.ok(ApiResponse.success(watchProgressService.getTitleProgress(profileId, titleId)));
    }

    @DeleteMapping("/titles/{titleId}")
    public ResponseEntity<ApiResponse<Void>> removeTitle(
            @RequestHeader(value = SecurityConstants.HEADER_X_PROFILE_ID, required = false) String profileId,
            @PathVariable("titleId") String titleId) {
        watchProgressService.removeTitle(profileId, titleId);
        return ResponseEntity.ok(ApiResponse.success("Removed from Continue Watching", null));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<WatchProgressResponse>> getProgress(
            @RequestHeader(value = SecurityConstants.HEADER_X_PROFILE_ID, required = false) String profileId,
            @PathVariable("contentId") String contentId) {
        return ResponseEntity.ok(ApiResponse.success(watchProgressService.getProgress(profileId, contentId)));
    }
}
