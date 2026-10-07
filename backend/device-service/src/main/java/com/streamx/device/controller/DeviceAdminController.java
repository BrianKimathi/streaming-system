package com.streamx.device.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.device.dto.DeviceResponse;
import com.streamx.device.service.DeviceAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/devices/admin")
public class DeviceAdminController {

    private final DeviceAdminService deviceAdminService;

    public DeviceAdminController(DeviceAdminService deviceAdminService) {
        this.deviceAdminService = deviceAdminService;
    }

    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> listDevices(
            @RequestParam(value = "accountId", required = false) String accountId) {
        return ResponseEntity.ok(ApiResponse.success(deviceAdminService.listDevices(accountId)));
    }

    @PostMapping("/devices/{id}/revoke")
    public ResponseEntity<ApiResponse<DeviceResponse>> revokeDevice(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success("Device revoked", deviceAdminService.revokeDevice(id)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(deviceAdminService.getStats()));
    }
}
