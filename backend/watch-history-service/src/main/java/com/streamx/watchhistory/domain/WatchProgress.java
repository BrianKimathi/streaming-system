package com.streamx.watchhistory.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "watch_progress")
public class WatchProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private UUID contentId;

    private UUID episodeId;

    @Column(nullable = false)
    private long positionSeconds = 0;

    @Column(nullable = false)
    private long durationSeconds = 0;

    private double percentage = 0.0;
    private boolean completed = false;

    private LocalDateTime lastWatchedAt;

    public WatchProgress() {
    }

    public WatchProgress(UUID id, UUID profileId, UUID accountId, UUID contentId, UUID episodeId,
                         long positionSeconds, long durationSeconds, double percentage, boolean completed,
                         LocalDateTime lastWatchedAt) {
        this.id = id;
        this.profileId = profileId;
        this.accountId = accountId;
        this.contentId = contentId;
        this.episodeId = episodeId;
        this.positionSeconds = positionSeconds;
        this.durationSeconds = durationSeconds;
        this.percentage = percentage;
        this.completed = completed;
        this.lastWatchedAt = lastWatchedAt;
    }

    @PrePersist
    @PreUpdate
    protected void onSave() {
        lastWatchedAt = LocalDateTime.now();
        if (durationSeconds > 0) {
            this.percentage = Math.min(100.0, ((double) positionSeconds / durationSeconds) * 100.0);
            if (this.percentage >= 90.0) {
                this.completed = true;
            }
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public UUID getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(UUID episodeId) {
        this.episodeId = episodeId;
    }

    public long getPositionSeconds() {
        return positionSeconds;
    }

    public void setPositionSeconds(long positionSeconds) {
        this.positionSeconds = positionSeconds;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public double getPercentage() {
        return percentage;
    }

    public void setPercentage(double percentage) {
        this.percentage = percentage;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public LocalDateTime getLastWatchedAt() {
        return lastWatchedAt;
    }

    public void setLastWatchedAt(LocalDateTime lastWatchedAt) {
        this.lastWatchedAt = lastWatchedAt;
    }
}
