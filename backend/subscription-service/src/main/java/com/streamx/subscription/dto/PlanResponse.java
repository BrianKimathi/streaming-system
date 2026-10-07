package com.streamx.subscription.dto;

import com.streamx.subscription.domain.BillingInterval;
import com.streamx.subscription.domain.VideoResolution;

import java.math.BigDecimal;

public class PlanResponse {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private BillingInterval billingInterval;
    private int version;
    private boolean active;
    private int maxProfiles;
    private int maxRegisteredDevices;
    private int maxConcurrentStreams;
    private VideoResolution maxResolution;
    private boolean hdrEnabled;
    private String audioQuality;
    private boolean downloadsEnabled;
    private int maxDownloadDevices;
    private boolean kidsProfilesEnabled;

    public PlanResponse() {
    }

    public PlanResponse(String id, String name, String description, BigDecimal price, String currency,
                        BillingInterval billingInterval, int version, boolean active, int maxProfiles,
                        int maxRegisteredDevices, int maxConcurrentStreams, VideoResolution maxResolution,
                        boolean hdrEnabled, String audioQuality, boolean downloadsEnabled,
                        int maxDownloadDevices, boolean kidsProfilesEnabled) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.billingInterval = billingInterval;
        this.version = version;
        this.active = active;
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BillingInterval getBillingInterval() {
        return billingInterval;
    }

    public void setBillingInterval(BillingInterval billingInterval) {
        this.billingInterval = billingInterval;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
