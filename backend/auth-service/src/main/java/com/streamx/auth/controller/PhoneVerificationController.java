package com.streamx.auth.controller;

import com.streamx.auth.dto.SendOtpRequest;
import com.streamx.auth.dto.VerifyOtpRequest;
import com.streamx.auth.service.PhoneVerificationService;
import com.streamx.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/phone")
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    public PhoneVerificationController(PhoneVerificationService phoneVerificationService) {
        this.phoneVerificationService = phoneVerificationService;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        phoneVerificationService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok(ApiResponse.success("OTP verification code sent successfully", request.getPhoneNumber()));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean verified = phoneVerificationService.verifyOtp(request.getPhoneNumber(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Phone number verified successfully", verified));
    }
}
