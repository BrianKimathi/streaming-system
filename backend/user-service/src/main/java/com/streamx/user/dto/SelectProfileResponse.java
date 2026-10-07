package com.streamx.user.dto;

public class SelectProfileResponse {
    private String profileAccessToken;
    private ProfileResponse profile;

    public SelectProfileResponse() {
    }

    public SelectProfileResponse(String profileAccessToken, ProfileResponse profile) {
        this.profileAccessToken = profileAccessToken;
        this.profile = profile;
    }

    public String getProfileAccessToken() {
        return profileAccessToken;
    }

    public void setProfileAccessToken(String profileAccessToken) {
        this.profileAccessToken = profileAccessToken;
    }

    public ProfileResponse getProfile() {
        return profile;
    }

    public void setProfile(ProfileResponse profile) {
        this.profile = profile;
    }
}
