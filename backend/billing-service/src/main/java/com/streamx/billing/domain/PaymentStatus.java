package com.streamx.billing.domain;

public enum PaymentStatus {
    INITIATED,
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED,
    CANCELLED
}
