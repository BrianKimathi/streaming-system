package com.streamx.subscription.dto;

import com.streamx.subscription.domain.VideoResolution;

public class EntitlementsResponse {
    private String subscriptionId;
    private String status;
    private int maxProfiles;
    private int maxRegisteredDevices;
    private int maxConcurrentStreams;
    private VideoResolution maxResolution;
    private boolean hdrEnabled;
    private String audioQuality;
    private boolean downloadsEnabled;
    private int maxDownloadDevices;
    private boolean kidsProfilesEnabled;

    public EntitlementsResponse() {
    }

    public EntitlementsResponse(String subscriptionId, String status, int maxProfiles, int maxRegisteredDevices,
                                int maxConcurrentStreams, VideoResolution maxResolution, boolean hdrEnabled,
                                String audioQuality, boolean downloadsEnabled, int maxDownloadDevices,
                                boolean kidsProfilesEnabled) {
        this.subscriptionId = subscriptionId;
        this.status = status;
        this.maxProfiles = maxProfiles;
        this.maxRegisteredDevices = maxRegisteredDevices;
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.maxResolution = maxResolution;
        this.hdrEnabled = hdrEnabled;
        this.audioQuality = audioQuality;
        this.downloadsEnabled = downloadsEnabled;
        this.maxDownloadDevices = maxDownloadDevices;
        this.kidsProfilesEnabled = kidsProfilesEnabled;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getMaxProfiles() {
        return maxProfiles;
    }

    public void setMaxProfiles(int maxProfiles) {
        this.maxProfiles = maxProfiles;
    }

    public int getMaxRegisteredDevices() {
        return maxRegisteredDevices;
    }

    public void setMaxRegisteredDevices(int maxRegisteredDevices) {
        this.maxRegisteredDevices = maxRegisteredDevices;
    }

    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    public VideoResolution getMaxResolution() {
        return maxResolution;
    }

    public void setMaxResolution(VideoResolution maxResolution) {
        this.maxResolution = maxResolution;
    }

    public boolean isHdrEnabled() {
        return hdrEnabled;
    }

    public void setHdrEnabled(boolean hdrEnabled) {
        this.hdrEnabled = hdrEnabled;
    }

    public String getAudioQuality() {
        return audioQuality;
    }

    public void setAudioQuality(String audioQuality) {
        this.audioQuality = audioQuality;
    }

    public boolean isDownloadsEnabled() {
        return downloadsEnabled;
    }

    public void setDownloadsEnabled(boolean downloadsEnabled) {
        this.downloadsEnabled = downloadsEnabled;
    }

    public int getMaxDownloadDevices() {
        return maxDownloadDevices;
    }

    public void setMaxDownloadDevices(int maxDownloadDevices) {
        this.maxDownloadDevices = maxDownloadDevices;
    }

    public boolean isKidsProfilesEnabled() {
        return kidsProfilesEnabled;
    }

    public void setKidsProfilesEnabled(boolean kidsProfilesEnabled) {
        this.kidsProfilesEnabled = kidsProfilesEnabled;
    }
}
