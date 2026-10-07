package com.streamx.playback.dto;

import jakarta.validation.constraints.NotBlank;

public class PlaybackAuthRequest {

    @NotBlank(message = "Content ID is required")
    private String contentId;

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    /** Movie or show id used for trending; defaults to contentId. */
    private String titleId;

    public PlaybackAuthRequest() {
    }

    public PlaybackAuthRequest(String contentId, String deviceId) {
        this.contentId = contentId;
        this.deviceId = deviceId;
    }

    public PlaybackAuthRequest(String contentId, String deviceId, String titleId) {
        this.contentId = contentId;
        this.deviceId = deviceId;
        this.titleId = titleId;
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

    public String getTitleId() {
        return titleId;
    }

    public void setTitleId(String titleId) {
        this.titleId = titleId;
    }
}
