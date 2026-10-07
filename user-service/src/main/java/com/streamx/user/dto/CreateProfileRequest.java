package com.streamx.user.dto;

import com.streamx.user.domain.MaturityRating;
import com.streamx.user.domain.ProfileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateProfileRequest {

    @NotBlank(message = "Profile name is required")
    @Size(min = 2, max = 30, message = "Profile name must be between 2 and 30 characters")
    private String name;

    private String avatarUrl;
    private ProfileType type = ProfileType.ADULT;
    private MaturityRating maturityRating;
    private String language = "en";
    private String preferredAudio = "en";
    private String preferredSubtitle = "off";

    private boolean pinProtected;

    @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits")
    private String pin;

    public CreateProfileRequest() {
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

    public boolean isPinProtected() {
        return pinProtected;
    }

    public void setPinProtected(boolean pinProtected) {
        this.pinProtected = pinProtected;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
