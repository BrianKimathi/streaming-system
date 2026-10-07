package com.streamx.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public class SubscribeRequest {
    @NotBlank(message = "Plan ID is required")
    private String planId;

    public SubscribeRequest() {
    }

    public SubscribeRequest(String planId) {
        this.planId = planId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }
}
