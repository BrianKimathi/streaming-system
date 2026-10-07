package com.streamx.device.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.device.client.DeviceEntitlement;
import com.streamx.device.client.SubscriptionClient;
import com.streamx.device.domain.Device;
import com.streamx.device.domain.DeviceStatus;
import com.streamx.device.dto.DeviceResponse;
import com.streamx.device.dto.DeviceStatusResponse;
import com.streamx.device.dto.RegisterDeviceRequest;
import com.streamx.device.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    /** Accounts without a usable subscription may still register one device (to sign in and subscribe). */
    static final int DEFAULT_DEVICE_LIMIT = 1;
    private static final Set<String> USABLE_STATUSES = Set.of("ACTIVE", "TRIAL", "GRACE_PERIOD");

    private final DeviceRepository deviceRepository;
    private final SubscriptionClient subscriptionClient;

    public DeviceService(DeviceRepository deviceRepository, SubscriptionClient subscriptionClient) {
        this.deviceRepository = deviceRepository;
        this.subscriptionClient = subscriptionClient;
    }

    @Transactional
    public DeviceResponse registerDevice(String accountIdStr, RegisterDeviceRequest request) {
        UUID accountId = UUID.fromString(accountIdStr);
        String fingerprint = request.getDeviceFingerprint().trim();

        Optional<Device> existingOpt = deviceRepository.findByAccountIdAndDeviceFingerprint(accountId, fingerprint);

        if (existingOpt.isPresent()) {
            Device device = existingOpt.get();
            if (device.getStatus() != DeviceStatus.ACTIVE) {
                enforceDeviceLimit(accountId);
                device.setStatus(DeviceStatus.ACTIVE);
                log.info("Re-activated revoked device {} for account {}", device.getId(), accountIdStr);
            }
            applyDetails(device, request);
            device.setLastSeenAt(LocalDateTime.now());
            return mapToResponse(deviceRepository.save(device));
        }

        enforceDeviceLimit(accountId);

        Device device = new Device();
        device.setAccountId(accountId);
        device.setDeviceFingerprint(fingerprint);
        applyDetails(device, request);
        device.setStatus(DeviceStatus.ACTIVE);

        Device saved = deviceRepository.save(device);
        log.info("Registered new device {} for account {}", saved.getId(), accountIdStr);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> getAccountDevices(String accountIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        return deviceRepository.findByAccountIdOrderByRegisteredAtDesc(accountId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void revokeDevice(String accountIdStr, String deviceIdStr) {
        Device device = findOwnedDevice(UUID.fromString(accountIdStr), deviceIdStr);
        device.setStatus(DeviceStatus.REVOKED);
        deviceRepository.save(device);
        log.info("Revoked device {} for account {}", deviceIdStr, accountIdStr);
    }

    @Transactional
    public void deleteDevice(String accountIdStr, String deviceIdStr) {
        Device device = findOwnedDevice(UUID.fromString(accountIdStr), deviceIdStr);
        deviceRepository.delete(device);
        log.info("Deleted device {} for account {}", deviceIdStr, accountIdStr);
    }

    /** Internal lookup used by playback: a device owned by another account is reported as not found. */
    @Transactional(readOnly = true)
    public DeviceStatusResponse getDeviceStatus(String deviceIdStr, String accountIdStr) {
        UUID accountId;
        try {
            accountId = UUID.fromString(accountIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResourceNotFoundException("Device not found");
        }
        return new DeviceStatusResponse(findOwnedDevice(accountId, deviceIdStr).getStatus());
    }

    int resolveDeviceLimit(UUID accountId) {
        return subscriptionClient.findEntitlements(accountId)
                .filter(entitlement -> USABLE_STATUSES.contains(entitlement.status()))
                .map(DeviceEntitlement::maxRegisteredDevices)
                .map(max -> Math.max(DEFAULT_DEVICE_LIMIT, max))
                .orElse(DEFAULT_DEVICE_LIMIT);
    }

    private void enforceDeviceLimit(UUID accountId) {
        int limit = resolveDeviceLimit(accountId);
        long activeCount = deviceRepository.countByAccountIdAndStatus(accountId, DeviceStatus.ACTIVE);
        if (activeCount >= limit) {
            throw new BadRequestException("Device limit reached (" + limit + "). Remove a device in Account \u2192 Devices.");
        }
    }

    private Device findOwnedDevice(UUID accountId, String deviceIdStr) {
        UUID deviceId;
        try {
            deviceId = UUID.fromString(deviceIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResourceNotFoundException("Device not found");
        }
        return deviceRepository.findById(deviceId)
                .filter(device -> device.getAccountId().equals(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Device not found"));
    }

    private static void applyDetails(Device device, RegisterDeviceRequest request) {
        device.setDeviceName(request.getDeviceName().trim());
        device.setDeviceType(request.getDeviceType());
        device.setPlatform(request.getPlatform());
        device.setAppVersion(request.getAppVersion());
    }

    DeviceResponse mapToResponse(Device device) {
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
