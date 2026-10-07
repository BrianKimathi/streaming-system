package com.streamx.watchhistory.dto;

import java.time.LocalDateTime;

public class WatchProgressResponse {
    private String id;
    private String profileId;
    private String contentId;
    private String episodeId;
    private long positionSeconds;
    private long durationSeconds;
    private double percentage;
    private boolean completed;
    private LocalDateTime lastWatchedAt;

    public WatchProgressResponse() {
    }

    public WatchProgressResponse(String id, String profileId, String contentId, String episodeId,
                                 long positionSeconds, long durationSeconds, double percentage,
                                 boolean completed, LocalDateTime lastWatchedAt) {
        this.id = id;
        this.profileId = profileId;
        this.contentId = contentId;
        this.episodeId = episodeId;
        this.positionSeconds = positionSeconds;
        this.durationSeconds = durationSeconds;
        this.percentage = percentage;
        this.completed = completed;
        this.lastWatchedAt = lastWatchedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(String episodeId) {
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
