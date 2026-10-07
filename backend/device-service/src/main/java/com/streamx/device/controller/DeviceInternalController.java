package com.streamx.device.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.device.dto.DeviceStatusResponse;
import com.streamx.device.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Service-to-service endpoints; the gateway refuses /internal/** so these are only reachable on the Docker network. */
@RestController
@RequestMapping("/api/v1/devices/internal")
public class DeviceInternalController {

    private final DeviceService deviceService;

    public DeviceInternalController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping("/{deviceId}/status")
    public ResponseEntity<ApiResponse<DeviceStatusResponse>> getDeviceStatus(
            @PathVariable("deviceId") String deviceId,
            @RequestParam(value = "accountId", required = false) String accountId) {
        return ResponseEntity.ok(ApiResponse.success(deviceService.getDeviceStatus(deviceId, accountId)));
    }
}
