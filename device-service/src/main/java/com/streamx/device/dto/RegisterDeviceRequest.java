package com.streamx.device.dto;

import com.streamx.device.domain.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RegisterDeviceRequest {

    @NotBlank(message = "Device fingerprint is required")
    private String deviceFingerprint;

    @NotBlank(message = "Device name is required")
    private String deviceName;

    @NotNull(message = "Device type is required")
    private DeviceType deviceType;

    private String platform;
    private String appVersion;
    private int maxAllowedDevices = 2; // Passed or evaluated against subscription

    public RegisterDeviceRequest() {
    }

    public RegisterDeviceRequest(String deviceFingerprint, String deviceName, DeviceType deviceType, String platform, String appVersion, int maxAllowedDevices) {
        this.deviceFingerprint = deviceFingerprint;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.platform = platform;
        this.appVersion = appVersion;
        this.maxAllowedDevices = maxAllowedDevices;
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

    public int getMaxAllowedDevices() {
        return maxAllowedDevices;
    }

    public void setMaxAllowedDevices(int maxAllowedDevices) {
        this.maxAllowedDevices = maxAllowedDevices;
    }
}
