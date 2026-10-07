package com.streamx.user.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.user.dto.ProfileOwnerResponse;
import com.streamx.user.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service endpoints; the gateway refuses /internal/** so these are only reachable on the Docker network. */
@RestController
@RequestMapping("/api/v1/profiles/internal")
public class ProfileInternalController {

    private final ProfileService profileService;

    public ProfileInternalController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{profileId}/owner")
    public ResponseEntity<ApiResponse<ProfileOwnerResponse>> getProfileOwner(@PathVariable("profileId") String profileId) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getProfileOwner(profileId)));
    }
}
