package com.streamx.device.dto;

import com.streamx.device.domain.DeviceStatus;
import com.streamx.device.domain.DeviceType;

import java.time.LocalDateTime;

public class DeviceResponse {
    private String id;
    private String accountId;
    private String deviceFingerprint;
    private String deviceName;
    private DeviceType deviceType;
    private String platform;
    private String appVersion;
    private DeviceStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime lastSeenAt;

    public DeviceResponse() {
    }

    public DeviceResponse(String id, String accountId, String deviceFingerprint, String deviceName,
                          DeviceType deviceType, String platform, String appVersion, DeviceStatus status,
                          LocalDateTime registeredAt, LocalDateTime lastSeenAt) {
        this.id = id;
        this.accountId = accountId;
        this.deviceFingerprint = deviceFingerprint;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.platform = platform;
        this.appVersion = appVersion;
        this.status = status;
        this.registeredAt = registeredAt;
        this.lastSeenAt = lastSeenAt;
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

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceStatus status) {
        this.status = status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
