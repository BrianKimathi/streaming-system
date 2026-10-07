package com.streamx.media.dto;

import com.streamx.media.domain.MediaProcessingStatus;

import java.time.LocalDateTime;

public class MediaAssetResponse {
    private String id;
    private String contentId;
    private String originalFilename;
    private String masterPlaylistUrl;
    private MediaProcessingStatus status;
    private Integer durationSeconds;
    private LocalDateTime createdAt;

    public MediaAssetResponse() {
    }

    public MediaAssetResponse(String id, String contentId, String originalFilename, String masterPlaylistUrl,
                              MediaProcessingStatus status, Integer durationSeconds, LocalDateTime createdAt) {
        this.id = id;
        this.contentId = contentId;
        this.originalFilename = originalFilename;
        this.masterPlaylistUrl = masterPlaylistUrl;
        this.status = status;
        this.durationSeconds = durationSeconds;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getMasterPlaylistUrl() {
        return masterPlaylistUrl;
    }

    public void setMasterPlaylistUrl(String masterPlaylistUrl) {
        this.masterPlaylistUrl = masterPlaylistUrl;
    }

    public MediaProcessingStatus getStatus() {
        return status;
    }

    public void setStatus(MediaProcessingStatus status) {
        this.status = status;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
