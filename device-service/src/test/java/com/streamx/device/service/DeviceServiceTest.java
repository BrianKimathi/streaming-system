package com.streamx.device.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.device.domain.DeviceStatus;
import com.streamx.device.domain.DeviceType;
import com.streamx.device.dto.DeviceResponse;
import com.streamx.device.dto.RegisterDeviceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceServiceTest {

    @Autowired
    private DeviceService deviceService;

    @Test
    void testRegisterDeviceSuccess() {
        String accountId = UUID.randomUUID().toString();
        RegisterDeviceRequest req = new RegisterDeviceRequest("FINGERPRINT_1", "Living Room TV", DeviceType.TV, "Tizen OS", "v2.1", 3);

        DeviceResponse response = deviceService.registerDevice(accountId, req);

        assertNotNull(response.getId());
        assertEquals("Living Room TV", response.getDeviceName());
        assertEquals(DeviceType.TV, response.getDeviceType());
        assertEquals(DeviceStatus.ACTIVE, response.getStatus());

        List<DeviceResponse> devices = deviceService.getAccountDevices(accountId);
        assertEquals(1, devices.size());
    }

    @Test
    void testRegisterDeviceExceedingLimitThrowsException() {
        String accountId = UUID.randomUUID().toString();
        RegisterDeviceRequest req1 = new RegisterDeviceRequest("FP_1", "TV 1", DeviceType.TV, "WebOS", "1.0", 2);
        RegisterDeviceRequest req2 = new RegisterDeviceRequest("FP_2", "Phone 1", DeviceType.PHONE, "Android", "1.0", 2);
        RegisterDeviceRequest req3 = new RegisterDeviceRequest("FP_3", "Tablet 1", DeviceType.TABLET, "iOS", "1.0", 2);

        deviceService.registerDevice(accountId, req1);
        deviceService.registerDevice(accountId, req2);

        // 3rd device attempt when limit is 2 should throw BadRequestException
        assertThrows(BadRequestException.class, () -> deviceService.registerDevice(accountId, req3));
    }

    @Test
    void testRevokeDevice() {
        String accountId = UUID.randomUUID().toString();
        RegisterDeviceRequest req = new RegisterDeviceRequest("FP_REVOKE", "Old Laptop", DeviceType.LAPTOP, "Windows", "1.0", 3);

        DeviceResponse response = deviceService.registerDevice(accountId, req);
        deviceService.revokeDevice(accountId, response.getId());

        List<DeviceResponse> activeDevices = deviceService.getAccountDevices(accountId);
        assertTrue(activeDevices.isEmpty());
    }
}
