package com.streamx.admin.service;

import com.streamx.admin.domain.AuditLog;
import com.streamx.admin.domain.FeatureFlag;
import com.streamx.admin.repository.AuditLogRepository;
import com.streamx.admin.repository.FeatureFlagRepository;
import com.streamx.admin.repository.IncidentRepository;
import com.streamx.admin.repository.SupportTicketRepository;
import com.streamx.common.events.AuditLogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    void recordAuditLog_SavesAndReturnsAuditEntry() {
        AuditLogEvent event = new AuditLogEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "admin@streamx.io",
                "SUPER_ADMIN", "USER_SUSPENDED", "USER", "user-123", "Policy violation",
                "127.0.0.1", "corr-123", "Suspended for 24h"
        );

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog saved = adminService.recordAuditLog(event);

        assertNotNull(saved);
        assertEquals("USER_SUSPENDED", saved.getAction());
        assertEquals("user-123", saved.getTargetId());
        assertEquals("admin@streamx.io", saved.getAdministratorEmail());
    }

    @Test
    void toggleFeatureFlag_UpdatesFlagAndLogsAudit() {
        FeatureFlag flag = new FeatureFlag("NEW_PLAYER", "HTML5 HLS Player", false, 0);

        when(featureFlagRepository.findByFlagKey("NEW_PLAYER")).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        FeatureFlag updated = adminService.toggleFeatureFlag("NEW_PLAYER", true, 50, "admin@streamx.io", "Rollout to 50%");

        assertTrue(updated.isEnabled());
        assertEquals(50, updated.getTargetPercentage());
    }
}
