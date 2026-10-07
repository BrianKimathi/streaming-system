package com.streamx.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.admin.domain.AuditLog;
import com.streamx.admin.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @Test
    void getAuditLogs_Returns200() throws Exception {
        AuditLog log = new AuditLog(
                UUID.randomUUID(), "admin@streamx.io", "SUPER_ADMIN", "USER_SUSPENDED",
                "USER", "user-123", "Reason", "127.0.0.1", "corr-1", "Details"
        );

        when(adminService.getAuditLogs()).thenReturn(List.of(log));

        mockMvc.perform(get("/api/v1/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].action").value("USER_SUSPENDED"));
    }
}
