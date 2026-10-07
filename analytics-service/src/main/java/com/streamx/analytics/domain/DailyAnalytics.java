package com.streamx.analytics.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_analytics")
public class DailyAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "date", nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "daily_active_users")
    private long dailyActiveUsers;

    @Column(name = "monthly_active_users")
    private long monthlyActiveUsers;

    @Column(name = "total_watch_time_seconds")
    private long totalWatchTimeSeconds;

    @Column(name = "average_completion_rate")
    private double averageCompletionRate;

    @Column(name = "total_streams_started")
    private long totalStreamsStarted;

    @Column(name = "total_subscriptions_active")
    private long totalSubscriptionsActive;

    @Column(name = "total_revenue")
    private double totalRevenue;

    public DailyAnalytics() {
    }

    public DailyAnalytics(LocalDate date) {
        this.date = date;
        this.dailyActiveUsers = 0;
        this.monthlyActiveUsers = 0;
        this.totalWatchTimeSeconds = 0;
        this.averageCompletionRate = 0.0;
        this.totalStreamsStarted = 0;
        this.totalSubscriptionsActive = 0;
        this.totalRevenue = 0.0;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public long getTotalWatchTimeSeconds() {
        return totalWatchTimeSeconds;
    }

    public void setTotalWatchTimeSeconds(long totalWatchTimeSeconds) {
        this.totalWatchTimeSeconds = totalWatchTimeSeconds;
    }

    public double getAverageCompletionRate() {
        return averageCompletionRate;
    }

    public void setAverageCompletionRate(double averageCompletionRate) {
        this.averageCompletionRate = averageCompletionRate;
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
