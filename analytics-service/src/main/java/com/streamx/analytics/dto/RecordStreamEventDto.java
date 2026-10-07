package com.streamx.analytics.dto;

public class RecordStreamEventDto {
    private long watchTimeSeconds;
    private double completionPercentage;
    private boolean newActiveUser;
    private double subscriptionPaymentAmount;

    public RecordStreamEventDto() {
    }

    public RecordStreamEventDto(long watchTimeSeconds, double completionPercentage, boolean newActiveUser, double subscriptionPaymentAmount) {
        this.watchTimeSeconds = watchTimeSeconds;
        this.completionPercentage = completionPercentage;
        this.newActiveUser = newActiveUser;
        this.subscriptionPaymentAmount = subscriptionPaymentAmount;
    }

    public long getWatchTimeSeconds() {
        return watchTimeSeconds;
    }

    public void setWatchTimeSeconds(long watchTimeSeconds) {
        this.watchTimeSeconds = watchTimeSeconds;
    }

    public double getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(double completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public boolean isNewActiveUser() {
        return newActiveUser;
    }

    public void setNewActiveUser(boolean newActiveUser) {
        this.newActiveUser = newActiveUser;
    }

    public double getSubscriptionPaymentAmount() {
        return subscriptionPaymentAmount;
    }

    public void setSubscriptionPaymentAmount(double subscriptionPaymentAmount) {
        this.subscriptionPaymentAmount = subscriptionPaymentAmount;
    }
}
