package com.streamx.device.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.device.client.DeviceEntitlement;
import com.streamx.device.client.SubscriptionClient;
import com.streamx.device.domain.DeviceStatus;
import com.streamx.device.domain.DeviceType;
import com.streamx.device.dto.DeviceResponse;
import com.streamx.device.dto.RegisterDeviceRequest;
import com.streamx.device.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceServiceTest {

    @Autowired
    private DeviceService deviceService;

    @MockitoBean
    private SubscriptionClient subscriptionClient;

    private void entitled(UUID accountId, String status, int maxDevices) {
        when(subscriptionClient.findEntitlements(accountId))
                .thenReturn(Optional.of(new DeviceEntitlement(status, maxDevices)));
    }

    private static RegisterDeviceRequest device(String fingerprint, String name) {
        return new RegisterDeviceRequest(fingerprint, name, DeviceType.PHONE, "Android", "1.0");
    }

    @Test
    void testRegisterDeviceSuccess() {
        UUID accountId = UUID.randomUUID();
        entitled(accountId, "ACTIVE", 3);
        RegisterDeviceRequest req = new RegisterDeviceRequest("FINGERPRINT_1", "Living Room TV", DeviceType.TV, "Tizen OS", "v2.1");

        DeviceResponse response = deviceService.registerDevice(accountId.toString(), req);

        assertNotNull(response.getId());
        assertEquals("Living Room TV", response.getDeviceName());
        assertEquals(DeviceType.TV, response.getDeviceType());
        assertEquals(DeviceStatus.ACTIVE, response.getStatus());
        assertEquals(1, deviceService.getAccountDevices(accountId.toString()).size());
    }

    @Test
    void testLimitComesFromEntitlements() {
        UUID accountId = UUID.randomUUID();
        entitled(accountId, "ACTIVE", 2);

        deviceService.registerDevice(accountId.toString(), device("FP_1", "Phone 1"));
        deviceService.registerDevice(accountId.toString(), device("FP_2", "Phone 2"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> deviceService.registerDevice(accountId.toString(), device("FP_3", "Phone 3")));
        assertEquals("Device limit reached (2). Remove a device in Account \u2192 Devices.", ex.getMessage());
    }

    @Test
    void testNoSubscriptionAllowsOneDevice() {
        UUID accountId = UUID.randomUUID();
        when(subscriptionClient.findEntitlements(accountId)).thenReturn(Optional.empty());

        deviceService.registerDevice(accountId.toString(), device("FP_A", "Phone A"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> deviceService.registerDevice(accountId.toString(), device("FP_B", "Phone B")));
        assertTrue(ex.getMessage().startsWith("Device limit reached (1)."));
    }

    @Test
    void testUnusableSubscriptionStatusAllowsOneDevice() {
        UUID accountId = UUID.randomUUID();
        entitled(accountId, "EXPIRED", 4);

        deviceService.registerDevice(accountId.toString(), device("FP_A", "Phone A"));

        assertThrows(BadRequestException.class,
                () -> deviceService.registerDevice(accountId.toString(), device("FP_B", "Phone B")));
    }

    @Test
    void testUsableStatusesUsePlanLimit() {
        for (String status : List.of("ACTIVE", "TRIAL", "GRACE_PERIOD")) {
            UUID accountId = UUID.randomUUID();
            entitled(accountId, status, 4);
            assertEquals(4, deviceService.resolveDeviceLimit(accountId), status);
        }
    }

    @Test
    void testSubscriptionServiceDownIsUnavailable() {
        UUID accountId = UUID.randomUUID();
        when(subscriptionClient.findEntitlements(any()))
                .thenThrow(new ServiceUnavailableException("Subscription service is unavailable"));

        assertThrows(ServiceUnavailableException.class,
                () -> deviceService.registerDevice(accountId.toString(), device("FP_X", "Phone X")));
    }

    @Test
    void testReRegisteringSameFingerprintUpdatesExistingDevice() {
        UUID accountId = UUID.randomUUID();
        entitled(accountId, "ACTIVE", 1);

        DeviceResponse first = deviceService.registerDevice(accountId.toString(), device("FP_SAME", "Old Name"));
        DeviceResponse again = deviceService.registerDevice(accountId.toString(),
                new RegisterDeviceRequest("FP_SAME", "New Name", DeviceType.PHONE, "Android", "2.0"));

        assertEquals(first.getId(), again.getId());
        assertEquals("New Name", again.getDeviceName());
        assertEquals("2.0", again.getAppVersion());
        assertEquals(1, deviceService.getAccountDevices(accountId.toString()).size());
    }

    @Test
    void testRevokedDeviceReactivatesOnlyUnderLimit() {
        UUID accountId = UUID.randomUUID();
        entitled(accountId, "ACTIVE", 1);

        DeviceResponse old = deviceService.registerDevice(accountId.toString(), device("FP_OLD", "Old Phone"));
        deviceService.revokeDevice(accountId.toString(), old.getId());
        deviceService.registerDevice(accountId.toString(), device("FP_NEW", "New Phone"));

        assertThrows(BadRequestException.class,
                () -> deviceService.registerDevice(accountId.toString(), device("FP_OLD", "Old Phone")));

        entitled(accountId, "ACTIVE", 2);
        DeviceResponse reactivated = deviceService.registerDevice(accountId.toString(), device("FP_OLD", "Old Phone"));
        assertEquals(old.getId(), reactivated.getId());
        assertEquals(DeviceStatus.ACTIVE, reactivated.getStatus());
    }

    @Test
    void testListIncludesRevokedDevicesNewestFirst() throws InterruptedException {
        UUID accountId = UUID.randomUUID();
        entitled(accountId, "ACTIVE", 3);

        DeviceResponse older = deviceService.registerDevice(accountId.toString(), device("FP_1", "Older"));
        Thread.sleep(20);
        DeviceResponse newer = deviceService.registerDevice(accountId.toString(), device("FP_2", "Newer"));
        deviceService.revokeDevice(accountId.toString(), older.getId());

        List<DeviceResponse> devices = deviceService.getAccountDevices(accountId.toString());
        assertEquals(List.of(newer.getId(), older.getId()), devices.stream().map(DeviceResponse::getId).toList());
        assertEquals(DeviceStatus.REVOKED, devices.get(1).getStatus());
    }

    @Test
    void testCannotRevokeOrDeleteAnotherAccountsDevice() {
        UUID owner = UUID.randomUUID();
        entitled(owner, "ACTIVE", 2);
        DeviceResponse device = deviceService.registerDevice(owner.toString(), device("FP_OWN", "Mine"));
        String stranger = UUID.randomUUID().toString();

        assertThrows(ResourceNotFoundException.class, () -> deviceService.revokeDevice(stranger, device.getId()));
        assertThrows(ResourceNotFoundException.class, () -> deviceService.deleteDevice(stranger, device.getId()));
    }

    @Test
    void testInternalDeviceStatusChecksOwnership() {
        UUID owner = UUID.randomUUID();
        entitled(owner, "ACTIVE", 2);
        DeviceResponse device = deviceService.registerDevice(owner.toString(), device("FP_STATUS", "Phone"));

        assertEquals(DeviceStatus.ACTIVE, deviceService.getDeviceStatus(device.getId(), owner.toString()).status());

        deviceService.revokeDevice(owner.toString(), device.getId());
        assertEquals(DeviceStatus.REVOKED, deviceService.getDeviceStatus(device.getId(), owner.toString()).status());

        assertThrows(ResourceNotFoundException.class,
                () -> deviceService.getDeviceStatus(device.getId(), UUID.randomUUID().toString()));
        assertThrows(ResourceNotFoundException.class,
                () -> deviceService.getDeviceStatus(UUID.randomUUID().toString(), owner.toString()));
        assertThrows(ResourceNotFoundException.class,
                () -> deviceService.getDeviceStatus(device.getId(), null));
    }
}
