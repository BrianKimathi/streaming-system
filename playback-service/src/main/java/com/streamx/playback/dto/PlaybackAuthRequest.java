package com.streamx.playback.dto;

import jakarta.validation.constraints.NotBlank;

public class PlaybackAuthRequest {

    @NotBlank(message = "Content ID is required")
    private String contentId;

    private String deviceId;
    private int maxConcurrentStreams = 2; // Default evaluated against subscription entitlement

    public PlaybackAuthRequest() {
    }

    public PlaybackAuthRequest(String contentId, String deviceId, int maxConcurrentStreams) {
        this.contentId = contentId;
        this.deviceId = deviceId;
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }
}
