package com.streamx.watchhistory.dto;

import com.streamx.watchhistory.domain.TitleType;

import java.time.LocalDateTime;

public class WatchProgressResponse {
    private String id;
    private String profileId;
    private String contentId;
    private String titleId;
    private TitleType titleType;
    private long positionSeconds;
    private long durationSeconds;
    private double percentage;
    private boolean completed;
    private LocalDateTime lastWatchedAt;

    public WatchProgressResponse() {
    }

    public WatchProgressResponse(String id, String profileId, String contentId, String titleId, TitleType titleType,
                                 long positionSeconds, long durationSeconds, double percentage,
                                 boolean completed, LocalDateTime lastWatchedAt) {
        this.id = id;
        this.profileId = profileId;
        this.contentId = contentId;
        this.titleId = titleId;
        this.titleType = titleType;
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

    public String getTitleId() {
        return titleId;
    }

    public void setTitleId(String titleId) {
        this.titleId = titleId;
    }

    public TitleType getTitleType() {
        return titleType;
    }

    public void setTitleType(TitleType titleType) {
        this.titleType = titleType;
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
