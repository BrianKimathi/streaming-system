package com.streamx.subscription.domain;

public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    PAST_DUE,
    GRACE_PERIOD,
    CANCELLED,
    EXPIRED,
    SUSPENDED
}
