package com.streamx.user.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String name;

    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileType type = ProfileType.ADULT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaturityRating maturityRating = MaturityRating.TV_MA;

    private String language = "en";
    private String preferredAudio = "en";
    private String preferredSubtitle = "off";
    private boolean autoplayNext = true;
    private boolean pinProtected = false;
    private String pinHash;

    private int failedPinAttempts = 0;
    private LocalDateTime pinLockedUntil;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Profile() {
    }

    public Profile(UUID id, UUID accountId, String name, String avatarUrl, ProfileType type,
                   MaturityRating maturityRating, String language, String preferredAudio,
                   String preferredSubtitle, boolean autoplayNext, boolean pinProtected,
                   String pinHash, int failedPinAttempts, LocalDateTime pinLockedUntil,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.type = type != null ? type : ProfileType.ADULT;
        this.maturityRating = maturityRating != null ? maturityRating : MaturityRating.TV_MA;
        this.language = language != null ? language : "en";
        this.preferredAudio = preferredAudio != null ? preferredAudio : "en";
        this.preferredSubtitle = preferredSubtitle != null ? preferredSubtitle : "off";
        this.autoplayNext = autoplayNext;
        this.pinProtected = pinProtected;
        this.pinHash = pinHash;
        this.failedPinAttempts = failedPinAttempts;
        this.pinLockedUntil = pinLockedUntil;
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

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public ProfileType getType() {
        return type;
    }

    public void setType(ProfileType type) {
        this.type = type;
    }

    public MaturityRating getMaturityRating() {
        return maturityRating;
    }

    public void setMaturityRating(MaturityRating maturityRating) {
        this.maturityRating = maturityRating;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPreferredAudio() {
        return preferredAudio;
    }

    public void setPreferredAudio(String preferredAudio) {
        this.preferredAudio = preferredAudio;
    }

    public String getPreferredSubtitle() {
        return preferredSubtitle;
    }

    public void setPreferredSubtitle(String preferredSubtitle) {
        this.preferredSubtitle = preferredSubtitle;
    }

    public boolean isAutoplayNext() {
        return autoplayNext;
    }

    public void setAutoplayNext(boolean autoplayNext) {
        this.autoplayNext = autoplayNext;
    }

    public boolean isPinProtected() {
        return pinProtected;
    }

    public void setPinProtected(boolean pinProtected) {
        this.pinProtected = pinProtected;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public int getFailedPinAttempts() {
        return failedPinAttempts;
    }

    public void setFailedPinAttempts(int failedPinAttempts) {
        this.failedPinAttempts = failedPinAttempts;
    }

    public LocalDateTime getPinLockedUntil() {
        return pinLockedUntil;
    }

    public void setPinLockedUntil(LocalDateTime pinLockedUntil) {
        this.pinLockedUntil = pinLockedUntil;
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
