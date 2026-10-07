package com.streamx.trending.dto;

import java.time.Instant;
import java.util.UUID;

public class TrendingItemResponseDto {
    private UUID contentId;
    private String title;
    private String contentType;
    private long views1h;
    private long views6h;
    private long completions24h;
    private long likes24h;
    private double velocityScore;
    private Instant updatedAt;

    public TrendingItemResponseDto() {
    }

    public TrendingItemResponseDto(UUID contentId, String title, String contentType, long views1h, long views6h,
                                 long completions24h, long likes24h, double velocityScore, Instant updatedAt) {
        this.contentId = contentId;
        this.title = title;
        this.contentType = contentType;
        this.views1h = views1h;
        this.views6h = views6h;
        this.completions24h = completions24h;
        this.likes24h = likes24h;
        this.velocityScore = velocityScore;
        this.updatedAt = updatedAt;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getViews1h() {
        return views1h;
    }

    public void setViews1h(long views1h) {
        this.views1h = views1h;
    }

    public long getViews6h() {
        return views6h;
    }

    public void setViews6h(long views6h) {
        this.views6h = views6h;
    }

    public long getCompletions24h() {
        return completions24h;
    }

    public void setCompletions24h(long completions24h) {
        this.completions24h = completions24h;
    }

    public long getLikes24h() {
        return likes24h;
    }

    public void setLikes24h(long likes24h) {
        this.likes24h = likes24h;
    }

    public double getVelocityScore() {
        return velocityScore;
    }

    public void setVelocityScore(double velocityScore) {
        this.velocityScore = velocityScore;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
