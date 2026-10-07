package com.streamx.device.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.device.domain.Device;
import com.streamx.device.domain.DeviceStatus;
import com.streamx.device.dto.DeviceResponse;
import com.streamx.device.dto.RegisterDeviceRequest;
import com.streamx.device.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public DeviceResponse registerDevice(String accountIdStr, RegisterDeviceRequest request) {
        UUID accountId = UUID.fromString(accountIdStr);

        Optional<Device> existingOpt = deviceRepository.findByAccountIdAndDeviceFingerprint(accountId, request.getDeviceFingerprint());

        if (existingOpt.isPresent()) {
            Device device = existingOpt.get();
            device.setDeviceName(request.getDeviceName());
            device.setDeviceType(request.getDeviceType());
            device.setPlatform(request.getPlatform());
            device.setAppVersion(request.getAppVersion());
            device.setStatus(DeviceStatus.ACTIVE);
            device.setLastSeenAt(LocalDateTime.now());
            Device updated = deviceRepository.save(device);
            log.info("Re-registered/updated existing device {} for account {}", updated.getId(), accountIdStr);
            return mapToResponse(updated);
        }

        long activeCount = deviceRepository.countByAccountIdAndStatus(accountId, DeviceStatus.ACTIVE);
        if (activeCount >= request.getMaxAllowedDevices()) {
            throw new BadRequestException("Maximum registered device limit (" + request.getMaxAllowedDevices() + ") reached for subscription.");
        }

        Device device = new Device();
        device.setAccountId(accountId);
        device.setDeviceFingerprint(request.getDeviceFingerprint());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setPlatform(request.getPlatform());
        device.setAppVersion(request.getAppVersion());
        device.setStatus(DeviceStatus.ACTIVE);

        Device saved = deviceRepository.save(device);
        log.info("Registered new device {} for account {}", saved.getId(), accountIdStr);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAccountDevices(String accountIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        return deviceRepository.findByAccountIdAndStatus(accountId, DeviceStatus.ACTIVE).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void revokeDevice(String accountIdStr, String deviceIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        UUID deviceId = UUID.fromString(deviceIdStr);

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        if (!device.getAccountId().equals(accountId)) {
            throw new BadRequestException("Device does not belong to this account");
        }

        device.setStatus(DeviceStatus.REVOKED);
        deviceRepository.save(device);
        log.info("Revoked device {} for account {}", deviceIdStr, accountIdStr);
    }

    @Transactional
    public void deleteDevice(String accountIdStr, String deviceIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        UUID deviceId = UUID.fromString(deviceIdStr);

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));

        if (!device.getAccountId().equals(accountId)) {
            throw new BadRequestException("Device does not belong to this account");
        }

        deviceRepository.delete(device);
        log.info("Deleted device {} for account {}", deviceIdStr, accountIdStr);
    }

    private DeviceResponse mapToResponse(Device device) {
        return new DeviceResponse(
                device.getId().toString(),
                device.getAccountId().toString(),
                device.getDeviceFingerprint(),
                device.getDeviceName(),
                device.getDeviceType(),
                device.getPlatform(),
                device.getAppVersion(),
                device.getStatus(),
                device.getRegisteredAt(),
                device.getLastSeenAt()
        );
    }
}
