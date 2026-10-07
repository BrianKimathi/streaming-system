package com.streamx.subscription.dto;

import com.streamx.subscription.domain.SubscriptionStatus;

import java.time.LocalDateTime;

public class SubscriptionResponse {
    private String id;
    private String accountId;
    private PlanResponse plan;
    private SubscriptionStatus status;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private boolean cancelAtPeriodEnd;
    private EntitlementsResponse entitlements;

    public SubscriptionResponse() {
    }

    public SubscriptionResponse(String id, String accountId, PlanResponse plan, SubscriptionStatus status,
                                LocalDateTime currentPeriodStart, LocalDateTime currentPeriodEnd,
                                boolean cancelAtPeriodEnd, EntitlementsResponse entitlements) {
        this.id = id;
        this.accountId = accountId;
        this.plan = plan;
        this.status = status;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.entitlements = entitlements;
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

    public PlanResponse getPlan() {
        return plan;
    }

    public void setPlan(PlanResponse plan) {
        this.plan = plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public void setCurrentPeriodStart(LocalDateTime currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public LocalDateTime getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(LocalDateTime currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public EntitlementsResponse getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(EntitlementsResponse entitlements) {
        this.entitlements = entitlements;
    }
}
