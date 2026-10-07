package com.streamx.device.controller;

import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.device.domain.DeviceStatus;
import com.streamx.device.dto.DeviceStatusResponse;
import com.streamx.device.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceService deviceService;

    @Test
    void returnsStatusForOwnedDevice() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();
        when(deviceService.getDeviceStatus(deviceId, accountId)).thenReturn(new DeviceStatusResponse(DeviceStatus.REVOKED));

        mockMvc.perform(get("/api/v1/devices/internal/{id}/status", deviceId).param("accountId", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    void returns404ForForeignDevice() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();
        when(deviceService.getDeviceStatus(deviceId, accountId)).thenThrow(new ResourceNotFoundException("Device not found"));

        mockMvc.perform(get("/api/v1/devices/internal/{id}/status", deviceId).param("accountId", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
