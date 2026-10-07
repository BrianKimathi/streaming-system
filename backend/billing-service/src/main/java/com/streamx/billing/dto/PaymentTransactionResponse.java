package com.streamx.billing.dto;

import com.streamx.billing.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentTransactionResponse {
    private String id;
    private String accountId;
    private String subscriptionId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String paymentMethod;
    private String externalTransactionId;
    private LocalDateTime createdAt;

    public PaymentTransactionResponse() {
    }

    public PaymentTransactionResponse(String id, String accountId, String subscriptionId, BigDecimal amount,
                                      String currency, PaymentStatus status, String paymentMethod,
                                      String externalTransactionId, LocalDateTime createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.subscriptionId = subscriptionId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.externalTransactionId = externalTransactionId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
