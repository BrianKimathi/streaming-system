package com.streamx.billing.controller;

import com.streamx.billing.dto.MpesaConnectionTestResponse;
import com.streamx.billing.dto.MpesaSettingsResponse;
import com.streamx.billing.dto.UpdateMpesaSettingsRequest;
import com.streamx.billing.service.MpesaSettingsService;
import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only (enforced by the gateway) management of the Daraja credentials.
 */
@RestController
@RequestMapping("/api/v1/billing/admin/mpesa-settings")
public class MpesaSettingsController {

    private final MpesaSettingsService mpesaSettingsService;

    public MpesaSettingsController(MpesaSettingsService mpesaSettingsService) {
        this.mpesaSettingsService = mpesaSettingsService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MpesaSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(mpesaSettingsService.getSettings()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<MpesaSettingsResponse>> updateSettings(
            @RequestHeader(value = SecurityConstants.HEADER_X_USER_EMAIL, required = false) String userEmail,
            @Valid @RequestBody UpdateMpesaSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("M-Pesa settings saved",
                mpesaSettingsService.updateSettings(request, userEmail)));
    }

    @DeleteMapping("/secrets")
    public ResponseEntity<ApiResponse<MpesaSettingsResponse>> clearStoredSecrets(
            @RequestHeader(value = SecurityConstants.HEADER_X_USER_EMAIL, required = false) String userEmail) {
        return ResponseEntity.ok(ApiResponse.success("Stored M-Pesa credentials cleared",
                mpesaSettingsService.clearStoredSecrets(userEmail)));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<MpesaConnectionTestResponse>> testConnection() {
        MpesaConnectionTestResponse result = mpesaSettingsService.testConnection();
        return ResponseEntity.ok(ApiResponse.success(result.message(), result));
    }
}
