package com.streamx.billing.dto;

import jakarta.validation.constraints.NotBlank;

public class CheckoutRequest {

    @NotBlank(message = "Plan ID is required")
    private String planId;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    public CheckoutRequest() {
    }

    public CheckoutRequest(String planId, String phoneNumber) {
        this.planId = planId;
        this.phoneNumber = phoneNumber;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
