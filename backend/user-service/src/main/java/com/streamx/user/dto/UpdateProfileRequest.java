package com.streamx.user.dto;

import com.streamx.user.domain.MaturityRating;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {
    @Size(min = 2, max = 30)
    private String name;
    private String avatarUrl;
    private MaturityRating maturityRating;
    private String language;
    private String preferredAudio;
    private String preferredSubtitle;
    private Boolean autoplayNext;
    private Boolean pinProtected;

    @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits")
    private String pin;

    public UpdateProfileRequest() {
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

    public Boolean getAutoplayNext() {
        return autoplayNext;
    }

    public void setAutoplayNext(Boolean autoplayNext) {
        this.autoplayNext = autoplayNext;
    }

    public Boolean getPinProtected() {
        return pinProtected;
    }

    public void setPinProtected(Boolean pinProtected) {
        this.pinProtected = pinProtected;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
