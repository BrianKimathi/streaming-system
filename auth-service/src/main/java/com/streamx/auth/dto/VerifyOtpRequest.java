package com.streamx.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class VerifyOtpRequest {
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must contain numeric digits only")
    private String code;

    public VerifyOtpRequest() {
    }

    public VerifyOtpRequest(String phoneNumber, String code) {
        this.phoneNumber = phoneNumber;
        this.code = code;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
