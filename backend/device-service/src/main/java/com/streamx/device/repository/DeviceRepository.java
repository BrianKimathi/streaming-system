package com.streamx.device.repository;

import com.streamx.device.domain.Device;
import com.streamx.device.domain.DeviceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findByAccountIdAndStatus(UUID accountId, DeviceStatus status);
    long countByAccountIdAndStatus(UUID accountId, DeviceStatus status);
    Optional<Device> findByAccountIdAndDeviceFingerprint(UUID accountId, String deviceFingerprint);
}
