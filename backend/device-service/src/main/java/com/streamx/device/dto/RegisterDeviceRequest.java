package com.streamx.device.dto;

import com.streamx.device.domain.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterDeviceRequest {

    @NotBlank(message = "Device fingerprint is required")
    @Size(max = 255, message = "Device fingerprint is too long")
    private String deviceFingerprint;

    @NotBlank(message = "Device name is required")
    @Size(max = 255, message = "Device name is too long")
    private String deviceName;

    @NotNull(message = "Device type is required")
    private DeviceType deviceType;

    private String platform;
    private String appVersion;

    public RegisterDeviceRequest() {
    }

    public RegisterDeviceRequest(String deviceFingerprint, String deviceName, DeviceType deviceType, String platform, String appVersion) {
        this.deviceFingerprint = deviceFingerprint;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.platform = platform;
        this.appVersion = appVersion;
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
}
