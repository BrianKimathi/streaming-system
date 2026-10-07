package com.streamx.admin.service;

import com.streamx.admin.domain.*;
import com.streamx.admin.repository.*;
import com.streamx.admin.security.AdminIdentity;
import com.streamx.common.events.AuditLogEvent;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final Set<String> SEVERITIES = Set.of("SEV1", "SEV2", "SEV3", "SEV4");
    private static final Set<String> INCIDENT_STATUSES = Set.of("OPEN", "INVESTIGATING", "MITIGATED", "RESOLVED", "CLOSED");
    private static final Set<String> TICKET_STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    private static final Set<String> TICKET_PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final AuditLogRepository auditLogRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final IncidentRepository incidentRepository;
    private final SupportTicketRepository supportTicketRepository;

    public AdminService(AuditLogRepository auditLogRepository,
                        FeatureFlagRepository featureFlagRepository,
                        IncidentRepository incidentRepository,
                        SupportTicketRepository supportTicketRepository) {
        this.auditLogRepository = auditLogRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.incidentRepository = incidentRepository;
        this.supportTicketRepository = supportTicketRepository;
    }

    // --- Audit Logging ---
    public List<AuditLog> getAuditLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }

    /**
     * Records an action performed by the calling administrator. Identity fields always come from the
     * authenticated request, never from the client payload.
     */
    @Transactional
    public AuditLog recordAuditLog(AdminIdentity admin, AuditLogEvent event) {
        if (event.getAction() == null || event.getAction().isBlank()) {
            throw new BadRequestException("action is required");
        }
        return audit(admin, event.getAction(), event.getTargetType(), event.getTargetId(), event.getReason(), event.getDetails());
    }

    private AuditLog audit(AdminIdentity admin, String action, String targetType, String targetId, String reason, String details) {
        AuditLog audit = new AuditLog(
                admin.accountId(),
                admin.displayName(),
                admin.primaryRole(),
                action,
                targetType == null || targetType.isBlank() ? "SYSTEM" : targetType,
                targetId,
                reason,
                admin.ipAddress(),
                UUID.randomUUID().toString(),
                details
        );
        log.info("Audit: {} on {} {} by {}", action, targetType, targetId, admin.displayName());
        return auditLogRepository.save(audit);
    }

    // --- Feature Flags ---
    public List<FeatureFlag> getFeatureFlags() {
        return featureFlagRepository.findAll().stream()
                .sorted(Comparator.comparing(FeatureFlag::getFlagKey))
                .toList();
    }

    @Transactional
    public FeatureFlag createFeatureFlag(AdminIdentity admin, String flagKey, String description, boolean enabled, int percentage) {
        String key = normalizeFlagKey(flagKey);
        if (featureFlagRepository.findByFlagKey(key).isPresent()) {
            throw new BadRequestException("Feature flag " + key + " already exists");
        }
        FeatureFlag flag = new FeatureFlag(key, description, enabled, clampPercentage(percentage));
        flag.setLastModifiedBy(admin.displayName());
        FeatureFlag saved = featureFlagRepository.save(flag);
        audit(admin, "FEATURE_FLAG_CREATED", "FEATURE_FLAG", key, null,
                "Enabled: " + enabled + ", Percentage: " + saved.getTargetPercentage());
        return saved;
    }

    @Transactional
    public FeatureFlag toggleFeatureFlag(AdminIdentity admin, String flagKey, boolean enabled, int percentage, String reason) {
        FeatureFlag flag = featureFlagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> new ResourceNotFoundException("Feature flag not found: " + flagKey));

        flag.setEnabled(enabled);
        flag.setTargetPercentage(clampPercentage(percentage));
        flag.setLastModifiedBy(admin.displayName());
        flag.setLastModifiedAt(Instant.now());
        FeatureFlag saved = featureFlagRepository.save(flag);

        audit(admin, "FEATURE_FLAG_CHANGED", "FEATURE_FLAG", flagKey, reason,
                "Enabled: " + enabled + ", Percentage: " + saved.getTargetPercentage());
        return saved;
    }

    // --- Incident Management ---
    public List<Incident> getIncidents() {
        return incidentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Incident createIncident(AdminIdentity admin, String title, String description, String severity, String affectedServices) {
        if (title == null || title.isBlank()) {
            throw new BadRequestException("title is required");
        }
        String sev = severity == null ? "SEV3" : severity.toUpperCase(Locale.ROOT);
        if (!SEVERITIES.contains(sev)) {
            throw new BadRequestException("severity must be one of " + SEVERITIES);
        }
        Incident saved = incidentRepository.save(
                new Incident(title.trim(), description, sev, "OPEN", affectedServices, admin.displayName()));
        audit(admin, "INCIDENT_CREATED", "INCIDENT", saved.getId().toString(), null, sev + ": " + title);
        return saved;
    }

    @Transactional
    public Incident updateIncidentStatus(AdminIdentity admin, UUID incidentId, String status) {
        String next = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!INCIDENT_STATUSES.contains(next)) {
            throw new BadRequestException("status must be one of " + INCIDENT_STATUSES);
        }
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found"));
        String previous = incident.getStatus();
        incident.setStatus(next);
        if ((next.equals("RESOLVED") || next.equals("CLOSED")) && incident.getResolvedAt() == null) {
            incident.setResolvedAt(Instant.now());
        } else if (!next.equals("RESOLVED") && !next.equals("CLOSED")) {
            incident.setResolvedAt(null);
        }
        Incident saved = incidentRepository.save(incident);
        audit(admin, "INCIDENT_STATUS_CHANGED", "INCIDENT", incidentId.toString(), null, previous + " -> " + next);
        return saved;
    }

    // --- Support Ticketing ---
    public List<SupportTicket> getSupportTickets() {
        return supportTicketRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public SupportTicket createSupportTicket(AdminIdentity admin, UUID accountId, String userEmail, String category,
                                             String priority, String subject, String body) {
        if (subject == null || subject.isBlank()) {
            throw new BadRequestException("subject is required");
        }
        String prio = priority == null ? "MEDIUM" : priority.toUpperCase(Locale.ROOT);
        if (!TICKET_PRIORITIES.contains(prio)) {
            throw new BadRequestException("priority must be one of " + TICKET_PRIORITIES);
        }
        SupportTicket saved = supportTicketRepository.save(
                new SupportTicket(accountId, userEmail, category, prio, "OPEN", subject.trim(), body));
        audit(admin, "TICKET_CREATED", "SUPPORT_TICKET", saved.getId().toString(), null, subject);
        return saved;
    }

    @Transactional
    public SupportTicket updateSupportTicket(AdminIdentity admin, UUID ticketId, String status, String assignedAgentEmail) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found"));
        List<String> changes = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            String next = status.toUpperCase(Locale.ROOT);
            if (!TICKET_STATUSES.contains(next)) {
                throw new BadRequestException("status must be one of " + TICKET_STATUSES);
            }
            changes.add("status " + ticket.getStatus() + " -> " + next);
            ticket.setStatus(next);
        }
        if (assignedAgentEmail != null) {
            String agent = assignedAgentEmail.isBlank() ? null : assignedAgentEmail.trim();
            changes.add("assignee -> " + (agent == null ? "unassigned" : agent));
            ticket.setAssignedAgentEmail(agent);
        }
        if (changes.isEmpty()) {
            throw new BadRequestException("Nothing to update");
        }
        ticket.setUpdatedAt(Instant.now());
        SupportTicket saved = supportTicketRepository.save(ticket);
        audit(admin, "TICKET_UPDATED", "SUPPORT_TICKET", ticketId.toString(), null, String.join(", ", changes));
        return saved;
    }

    private static int clampPercentage(int percentage) {
        return Math.max(0, Math.min(100, percentage));
    }

    private static String normalizeFlagKey(String flagKey) {
        if (flagKey == null || flagKey.isBlank()) {
            throw new BadRequestException("flagKey is required");
        }
        String key = flagKey.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        if (key.length() > 100) {
            throw new BadRequestException("flagKey is too long");
        }
        return key;
    }
}
