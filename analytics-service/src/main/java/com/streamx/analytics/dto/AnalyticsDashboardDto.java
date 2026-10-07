package com.streamx.analytics.dto;

import java.time.LocalDate;

public class AnalyticsDashboardDto {
    private LocalDate date;
    private long dailyActiveUsers;
    private long monthlyActiveUsers;
    private double totalWatchTimeHours;
    private double averageCompletionRatePercentage;
    private long totalStreamsStarted;
    private long totalSubscriptionsActive;
    private double totalRevenue;

    public AnalyticsDashboardDto() {
    }

    public AnalyticsDashboardDto(LocalDate date, long dailyActiveUsers, long monthlyActiveUsers,
                                double totalWatchTimeHours, double averageCompletionRatePercentage,
                                long totalStreamsStarted, long totalSubscriptionsActive, double totalRevenue) {
        this.date = date;
        this.dailyActiveUsers = dailyActiveUsers;
        this.monthlyActiveUsers = monthlyActiveUsers;
        this.totalWatchTimeHours = totalWatchTimeHours;
        this.averageCompletionRatePercentage = averageCompletionRatePercentage;
        this.totalStreamsStarted = totalStreamsStarted;
        this.totalSubscriptionsActive = totalSubscriptionsActive;
        this.totalRevenue = totalRevenue;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public long getDailyActiveUsers() {
        return dailyActiveUsers;
    }

    public void setDailyActiveUsers(long dailyActiveUsers) {
        this.dailyActiveUsers = dailyActiveUsers;
    }

    public long getMonthlyActiveUsers() {
        return monthlyActiveUsers;
    }

    public void setMonthlyActiveUsers(long monthlyActiveUsers) {
        this.monthlyActiveUsers = monthlyActiveUsers;
    }

    public double getTotalWatchTimeHours() {
        return totalWatchTimeHours;
    }

    public void setTotalWatchTimeHours(double totalWatchTimeHours) {
        this.totalWatchTimeHours = totalWatchTimeHours;
    }

    public double getAverageCompletionRatePercentage() {
        return averageCompletionRatePercentage;
    }

    public void setAverageCompletionRatePercentage(double averageCompletionRatePercentage) {
        this.averageCompletionRatePercentage = averageCompletionRatePercentage;
    }

    public long getTotalStreamsStarted() {
        return totalStreamsStarted;
    }

    public void setTotalStreamsStarted(long totalStreamsStarted) {
        this.totalStreamsStarted = totalStreamsStarted;
    }

    public long getTotalSubscriptionsActive() {
        return totalSubscriptionsActive;
    }

    public void setTotalSubscriptionsActive(long totalSubscriptionsActive) {
        this.totalSubscriptionsActive = totalSubscriptionsActive;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }
}
