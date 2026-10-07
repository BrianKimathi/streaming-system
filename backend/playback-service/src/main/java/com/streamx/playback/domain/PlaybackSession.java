package com.streamx.playback.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "playback_sessions")
public class PlaybackSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private UUID profileId;

    @Column(nullable = false)
    private UUID contentId;

    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaybackStatus status = PlaybackStatus.ACTIVE;

    private LocalDateTime startTime;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime expiresAt;
    private String terminationReason;

    public PlaybackSession() {
    }

    public PlaybackSession(UUID id, UUID accountId, UUID profileId, UUID contentId, String deviceId,
                           PlaybackStatus status, LocalDateTime startTime, LocalDateTime lastHeartbeat,
                           LocalDateTime expiresAt, String terminationReason) {
        this.id = id;
        this.accountId = accountId;
        this.profileId = profileId;
        this.contentId = contentId;
        this.deviceId = deviceId;
        this.status = status != null ? status : PlaybackStatus.ACTIVE;
        this.startTime = startTime;
        this.lastHeartbeat = lastHeartbeat;
        this.expiresAt = expiresAt;
        this.terminationReason = terminationReason;
    }

    @PrePersist
    protected void onCreate() {
        startTime = LocalDateTime.now();
        lastHeartbeat = LocalDateTime.now();
        expiresAt = LocalDateTime.now().plusHours(4); // Default 4 hour expiration
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public PlaybackStatus getStatus() {
        return status;
    }

    public void setStatus(PlaybackStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(LocalDateTime lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }
}
