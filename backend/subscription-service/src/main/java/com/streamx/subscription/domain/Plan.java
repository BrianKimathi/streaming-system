package com.streamx.subscription.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String currency = "KES";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillingInterval billingInterval = BillingInterval.MONTHLY;

    @Column(nullable = false)
    private int version = 1;

    private boolean active = true;

    // --- Entitlement Attributes ---
    private int maxProfiles = 1;
    private int maxRegisteredDevices = 2;
    private int maxConcurrentStreams = 1;

    @Enumerated(EnumType.STRING)
    private VideoResolution maxResolution = VideoResolution.SD_720P;

    private boolean hdrEnabled = false;
    private String audioQuality = "STANDARD";
    private boolean downloadsEnabled = false;
    private int maxDownloadDevices = 1;
    private boolean kidsProfilesEnabled = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Plan() {
    }

    public Plan(UUID id, String name, String description, BigDecimal price, String currency,
                BillingInterval billingInterval, int version, boolean active, int maxProfiles,
                int maxRegisteredDevices, int maxConcurrentStreams, VideoResolution maxResolution,
                boolean hdrEnabled, String audioQuality, boolean downloadsEnabled, int maxDownloadDevices,
                boolean kidsProfilesEnabled, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.currency = currency != null ? currency : "KES";
        this.billingInterval = billingInterval != null ? billingInterval : BillingInterval.MONTHLY;
        this.version = version;
        this.active = active;
        this.maxProfiles = maxProfiles;
        this.maxRegisteredDevices = maxRegisteredDevices;
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.maxResolution = maxResolution != null ? maxResolution : VideoResolution.SD_720P;
        this.hdrEnabled = hdrEnabled;
        this.audioQuality = audioQuality != null ? audioQuality : "STANDARD";
        this.downloadsEnabled = downloadsEnabled;
        this.maxDownloadDevices = maxDownloadDevices;
        this.kidsProfilesEnabled = kidsProfilesEnabled;
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
