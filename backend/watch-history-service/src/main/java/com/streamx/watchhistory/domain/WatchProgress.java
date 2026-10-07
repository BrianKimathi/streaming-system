package com.streamx.watchhistory.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Progress of one profile on one playable item: contentId is the movie id or the episode id, titleId is the movie id
 * or the show id. episodeId is kept for older readers and equals contentId for series episodes.
 */
@Entity
@Table(name = "watch_progress",
        uniqueConstraints = @UniqueConstraint(name = "uk_watch_progress_profile_content",
                columnNames = {"profile_id", "content_id"}),
        indexes = @Index(name = "idx_watch_progress_profile_title", columnList = "profile_id, title_id"))
public class WatchProgress {

    public static final double COMPLETION_THRESHOLD_PERCENT = 90.0;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private UUID contentId;

    // Nullable at the schema level so rows created before titles existed can be migrated in place.
    private UUID titleId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TitleType titleType;

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

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (lastWatchedAt == null) {
            lastWatchedAt = LocalDateTime.now();
        }
    }

    /** Records the latest playback position; completion follows the position, so rewatching from the start resets it. */
    public void applyPosition(long positionSeconds, long durationSeconds) {
        this.durationSeconds = Math.max(0, durationSeconds);
        this.positionSeconds = Math.max(0, Math.min(positionSeconds, this.durationSeconds));
        this.percentage = this.durationSeconds > 0
                ? Math.min(100.0, ((double) this.positionSeconds / this.durationSeconds) * 100.0)
                : 0.0;
        this.completed = this.percentage >= COMPLETION_THRESHOLD_PERCENT;
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

    public UUID getTitleId() {
        return titleId;
    }

    public void setTitleId(UUID titleId) {
        this.titleId = titleId;
    }

    public TitleType getTitleType() {
        return titleType;
    }

    public void setTitleType(TitleType titleType) {
        this.titleType = titleType;
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
