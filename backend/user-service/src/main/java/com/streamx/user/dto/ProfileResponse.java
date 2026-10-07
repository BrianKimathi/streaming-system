package com.streamx.user.dto;

import com.streamx.user.domain.MaturityRating;
import com.streamx.user.domain.ProfileType;

import java.time.LocalDateTime;

public class ProfileResponse {
    private String id;
    private String accountId;
    private String name;
    private String avatarUrl;
    private ProfileType type;
    private MaturityRating maturityRating;
    private String language;
    private String preferredAudio;
    private String preferredSubtitle;
    private boolean autoplayNext;
    private boolean pinProtected;
    private LocalDateTime createdAt;

    public ProfileResponse() {
    }

    public ProfileResponse(String id, String accountId, String name, String avatarUrl, ProfileType type,
                           MaturityRating maturityRating, String language, String preferredAudio,
                           String preferredSubtitle, boolean autoplayNext, boolean pinProtected,
                           LocalDateTime createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.type = type;
        this.maturityRating = maturityRating;
        this.language = language;
        this.preferredAudio = preferredAudio;
        this.preferredSubtitle = preferredSubtitle;
        this.autoplayNext = autoplayNext;
        this.pinProtected = pinProtected;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
