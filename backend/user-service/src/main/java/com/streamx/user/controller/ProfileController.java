package com.streamx.user.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.user.dto.*;
import com.streamx.user.service.ProfileService;
import com.streamx.user.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profileService;
    private final WatchlistService watchlistService;

    public ProfileController(ProfileService profileService, WatchlistService watchlistService) {
        this.profileService = profileService;
        this.watchlistService = watchlistService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> createProfile(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @Valid @RequestBody CreateProfileRequest request) {
        ProfileResponse response = profileService.createProfile(accountId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProfileResponse>>> getAccountProfiles(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId) {
        List<ProfileResponse> profiles = profileService.getAccountProfiles(accountId);
        return ResponseEntity.ok(ApiResponse.success(profiles));
    }

    @GetMapping("/me/watchlist")
    public ResponseEntity<ApiResponse<List<WatchlistItemResponse>>> getWatchlist(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @RequestHeader(value = SecurityConstants.HEADER_X_PROFILE_ID, required = false) String profileId) {
        return ResponseEntity.ok(ApiResponse.success(watchlistService.getWatchlist(accountId, profileId)));
    }

    @PutMapping("/me/watchlist/{titleId}")
    public ResponseEntity<ApiResponse<WatchlistItemResponse>> addToWatchlist(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @RequestHeader(value = SecurityConstants.HEADER_X_PROFILE_ID, required = false) String profileId,
            @PathVariable("titleId") String titleId,
            @Valid @RequestBody AddWatchlistItemRequest request) {
        WatchlistItemResponse response = watchlistService.addToWatchlist(accountId, profileId, titleId, request.getTitleType());
        return ResponseEntity.ok(ApiResponse.success("Added to My List", response));
    }

    @DeleteMapping("/me/watchlist/{titleId}")
    public ResponseEntity<ApiResponse<Void>> removeFromWatchlist(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @RequestHeader(value = SecurityConstants.HEADER_X_PROFILE_ID, required = false) String profileId,
            @PathVariable("titleId") String titleId) {
        watchlistService.removeFromWatchlist(accountId, profileId, titleId);
        return ResponseEntity.ok(ApiResponse.success("Removed from My List", null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @PathVariable("id") String profileId) {
        ProfileResponse response = profileService.getProfile(accountId, profileId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @PathVariable("id") String profileId,
            @Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse response = profileService.updateProfile(accountId, profileId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @PathVariable("id") String profileId) {
        profileService.deleteProfile(accountId, profileId);
        return ResponseEntity.ok(ApiResponse.success("Profile deleted successfully", null));
    }

    @PostMapping("/{id}/verify-pin")
    public ResponseEntity<ApiResponse<Boolean>> verifyPin(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @PathVariable("id") String profileId,
            @Valid @RequestBody VerifyPinRequest request) {
        boolean verified = profileService.verifyPin(accountId, profileId, request.getPin());
        return ResponseEntity.ok(ApiResponse.success("PIN verified successfully", verified));
    }

    @PostMapping("/{id}/select")
    public ResponseEntity<ApiResponse<SelectProfileResponse>> selectProfile(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @RequestHeader(value = SecurityConstants.HEADER_X_USER_EMAIL, required = false) String email,
            @RequestHeader(value = SecurityConstants.HEADER_X_USER_ROLES, required = false) String rolesHeader,
            @PathVariable("id") String profileId,
            @RequestParam(value = "pin", required = false) String pin) {
        SelectProfileResponse response = profileService.selectProfile(accountId, email, parseRoles(rolesHeader), profileId, pin);
        return ResponseEntity.ok(ApiResponse.success("Profile selected successfully", response));
    }

    static List<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return List.of("ROLE_USER");
        }
        List<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .distinct()
                .toList();
        return roles.isEmpty() ? List.of("ROLE_USER") : roles;
    }
}
