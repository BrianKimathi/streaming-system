package com.streamx.subscription.dto;

import com.streamx.subscription.domain.BillingInterval;
import com.streamx.subscription.domain.VideoResolution;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreatePlanRequest {

    @NotBlank(message = "Plan name is required")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    private String currency = "KES";
    private BillingInterval billingInterval = BillingInterval.MONTHLY;

    @Min(value = 1)
    private int maxProfiles = 1;

    @Min(value = 1)
    private int maxRegisteredDevices = 2;

    @Min(value = 1)
    private int maxConcurrentStreams = 1;

    private VideoResolution maxResolution = VideoResolution.SD_720P;
    private boolean hdrEnabled = false;
    private String audioQuality = "STANDARD";
    private boolean downloadsEnabled = false;
    private int maxDownloadDevices = 1;
    private boolean kidsProfilesEnabled = true;

    public CreatePlanRequest() {
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
