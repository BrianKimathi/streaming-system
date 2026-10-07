package com.streamx.trending.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trending_items")
public class TrendingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content_id", nullable = false, unique = true)
    private UUID contentId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "views_1h")
    private long views1h;

    @Column(name = "views_6h")
    private long views6h;

    @Column(name = "completions_24h")
    private long completions24h;

    @Column(name = "likes_24h")
    private long likes24h;

    @Column(name = "velocity_score")
    private double velocityScore;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TrendingItem() {
    }

    public TrendingItem(UUID contentId, String title, String contentType) {
        this.contentId = contentId;
        this.title = title;
        this.contentType = contentType;
        this.views1h = 0;
        this.views6h = 0;
        this.completions24h = 0;
        this.likes24h = 0;
        this.velocityScore = 0.0;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
