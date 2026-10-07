package com.streamx.device.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.device.domain.Device;
import com.streamx.device.domain.DeviceStatus;
import com.streamx.device.domain.DeviceType;
import com.streamx.device.dto.DeviceResponse;
import com.streamx.device.repository.DeviceRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DeviceAdminService {

    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;

    public DeviceAdminService(DeviceRepository deviceRepository, DeviceService deviceService) {
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listDevices(String accountId) {
        List<Device> devices = (accountId == null || accountId.isBlank())
                ? deviceRepository.findAll(Sort.by(Sort.Direction.DESC, "registeredAt"))
                : deviceRepository.findByAccountIdOrderByRegisteredAtDesc(parseId(accountId));
        return devices.stream().map(deviceService::mapToResponse).toList();
    }

    @Transactional
    public DeviceResponse revokeDevice(String deviceId) {
        Device device = deviceRepository.findById(parseId(deviceId))
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
        device.setStatus(DeviceStatus.REVOKED);
        return deviceService.mapToResponse(deviceRepository.save(device));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<String, Long> byType = new LinkedHashMap<>();
        for (DeviceType type : DeviceType.values()) {
            byType.put(type.name(), 0L);
        }
        for (Device device : deviceRepository.findAll()) {
            if (device.getStatus() == DeviceStatus.ACTIVE) {
                byType.merge(device.getDeviceType().name(), 1L, Long::sum);
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalDevices", deviceRepository.count());
        stats.put("activeDevices", deviceRepository.countByStatus(DeviceStatus.ACTIVE));
        stats.put("revokedDevices", deviceRepository.countByStatus(DeviceStatus.REVOKED));
        stats.put("activeDevicesByType", byType);
        return stats;
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid ID: " + id);
        }
    }
}
