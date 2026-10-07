package com.streamx.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyPinRequest {
    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "^\\d{4}$", message = "PIN code must be 4 digits")
    private String pin;

    public VerifyPinRequest() {
    }

    public VerifyPinRequest(String pin) {
        this.pin = pin;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
