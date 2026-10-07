package com.streamx.media.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID contentId;

    private String originalFilename;
    private String storagePath;
    private String masterPlaylistUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaProcessingStatus status = MediaProcessingStatus.UPLOADING;

    private Integer durationSeconds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MediaAsset() {
    }

    public MediaAsset(UUID id, UUID contentId, String originalFilename, String storagePath,
                      String masterPlaylistUrl, MediaProcessingStatus status, Integer durationSeconds,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.contentId = contentId;
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
        this.masterPlaylistUrl = masterPlaylistUrl;
        this.status = status != null ? status : MediaProcessingStatus.UPLOADING;
        this.durationSeconds = durationSeconds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
