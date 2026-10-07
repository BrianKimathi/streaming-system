package com.streamx.device.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.device.dto.DeviceResponse;
import com.streamx.device.dto.RegisterDeviceRequest;
import com.streamx.device.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<DeviceResponse>> registerDevice(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @Valid @RequestBody RegisterDeviceRequest request) {
        DeviceResponse response = deviceService.registerDevice(accountId, request);
        return ResponseEntity.ok(ApiResponse.success("Device registered successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> getAccountDevices(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId) {
        List<DeviceResponse> response = deviceService.getAccountDevices(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeDevice(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @PathVariable("id") String deviceId) {
        deviceService.revokeDevice(accountId, deviceId);
        return ResponseEntity.ok(ApiResponse.success("Device revoked successfully", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @PathVariable("id") String deviceId) {
        deviceService.deleteDevice(accountId, deviceId);
        return ResponseEntity.ok(ApiResponse.success("Device deleted successfully", null));
    }
}
