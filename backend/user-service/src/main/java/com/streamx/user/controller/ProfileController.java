package com.streamx.user.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.user.dto.*;
import com.streamx.user.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
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
            @RequestHeader(value = SecurityConstants.HEADER_X_USER_ROLES, required = false, defaultValue = "ROLE_USER") String rolesStr,
            @PathVariable("id") String profileId,
            @RequestParam(value = "pin", required = false) String pin) {
        SelectProfileResponse response = profileService.selectProfile(accountId, "user@streamx.com", Collections.singletonList(rolesStr), profileId, pin);
        return ResponseEntity.ok(ApiResponse.success("Profile selected successfully", response));
    }
}
