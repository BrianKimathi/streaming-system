package com.streamx.admin.service;

import com.streamx.admin.domain.AuditLog;
import com.streamx.admin.domain.FeatureFlag;
import com.streamx.admin.domain.SupportTicket;
import com.streamx.admin.repository.AuditLogRepository;
import com.streamx.admin.repository.FeatureFlagRepository;
import com.streamx.admin.repository.IncidentRepository;
import com.streamx.admin.repository.SupportTicketRepository;
import com.streamx.admin.security.AdminIdentity;
import com.streamx.common.events.AuditLogEvent;
import com.streamx.common.exception.ResourceNotFoundException;
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

    private final AdminIdentity admin = new AdminIdentity(UUID.randomUUID(), "ops@example.com", "ROLE_SUPER_ADMIN", "10.0.0.1");

    @Test
    void recordAuditLog_UsesAuthenticatedIdentityNotPayload() {
        AuditLogEvent event = new AuditLogEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "spoofed@evil.com",
                "SUPER_ADMIN", "USER_SUSPENDED", "USER", "user-123", "Policy violation",
                "1.2.3.4", "corr-123", "Suspended for 24h"
        );
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLog saved = adminService.recordAuditLog(admin, event);

        assertEquals("USER_SUSPENDED", saved.getAction());
        assertEquals("user-123", saved.getTargetId());
        assertEquals("ops@example.com", saved.getAdministratorEmail());
        assertEquals(admin.accountId(), saved.getAdministratorId());
    }

    @Test
    void getFeatureFlags_DoesNotSeedFakeFlags() {
        when(featureFlagRepository.findAll()).thenReturn(List.of());
        assertTrue(adminService.getFeatureFlags().isEmpty());
    }

    @Test
    void toggleFeatureFlag_UpdatesFlagAndLogsAudit() {
        FeatureFlag flag = new FeatureFlag("NEW_PLAYER", "HTML5 HLS Player", false, 0);
        when(featureFlagRepository.findByFlagKey("NEW_PLAYER")).thenReturn(Optional.of(flag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        FeatureFlag updated = adminService.toggleFeatureFlag(admin, "NEW_PLAYER", true, 50, "Rollout to 50%");

        assertTrue(updated.isEnabled());
        assertEquals(50, updated.getTargetPercentage());
        assertEquals("ops@example.com", updated.getLastModifiedBy());
    }

    @Test
    void toggleFeatureFlag_UnknownFlagIsNotSilentlyCreated() {
        when(featureFlagRepository.findByFlagKey("MISSING")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> adminService.toggleFeatureFlag(admin, "MISSING", true, 100, null));
    }

    @Test
    void updateSupportTicket_ChangesStatusAndAssignee() {
        UUID id = UUID.randomUUID();
        SupportTicket ticket = new SupportTicket(UUID.randomUUID(), "user@example.com", "BILLING", "HIGH", "OPEN", "Charged twice", "...");
        ticket.setId(id);
        when(supportTicketRepository.findById(id)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

        SupportTicket updated = adminService.updateSupportTicket(admin, id, "resolved", "agent@example.com");

        assertEquals("RESOLVED", updated.getStatus());
        assertEquals("agent@example.com", updated.getAssignedAgentEmail());
    }
}
