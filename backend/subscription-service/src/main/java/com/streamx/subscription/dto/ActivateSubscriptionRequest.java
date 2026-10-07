package com.streamx.subscription.dto;

import jakarta.validation.constraints.NotBlank;

public class ActivateSubscriptionRequest {

    @NotBlank(message = "Account ID is required")
    private String accountId;

    @NotBlank(message = "Plan ID is required")
    private String planId;

    @NotBlank(message = "Payment transaction ID is required")
    private String paymentTransactionId;

    public ActivateSubscriptionRequest() {
    }

    public ActivateSubscriptionRequest(String accountId, String planId, String paymentTransactionId) {
        this.accountId = accountId;
        this.planId = planId;
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }
}
