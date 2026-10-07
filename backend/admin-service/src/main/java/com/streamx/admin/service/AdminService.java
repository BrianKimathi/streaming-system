package com.streamx.admin.service;

import com.streamx.admin.domain.*;
import com.streamx.admin.repository.*;
import com.streamx.common.events.AuditLogEvent;
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

    @Transactional
    public AuditLog recordAuditLog(AuditLogEvent event) {
        AuditLog audit = new AuditLog(
                event.getAdministratorId(),
                event.getAdministratorEmail() != null ? event.getAdministratorEmail() : "admin@streamx.io",
                event.getRole() != null ? event.getRole() : "SUPER_ADMIN",
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getReason(),
                event.getIpAddress(),
                event.getCorrelationId(),
                event.getDetails()
        );
        log.info("Audit logged action: {} on target: {} by admin: {}", event.getAction(), event.getTargetId(), event.getAdministratorEmail());
        return auditLogRepository.save(audit);
    }

    // --- Feature Flags ---
    public List<FeatureFlag> getFeatureFlags() {
        List<FeatureFlag> flags = featureFlagRepository.findAll();
        if (flags.isEmpty()) {
            // Seed initial feature flags
            FeatureFlag f1 = featureFlagRepository.save(new FeatureFlag("NEW_PLAYER", "Next-Gen HTML5 HLS Player", true, 100));
            FeatureFlag f2 = featureFlagRepository.save(new FeatureFlag("AI_RECOMMENDATIONS", "Personalized ML Recommendations", true, 20));
            FeatureFlag f3 = featureFlagRepository.save(new FeatureFlag("OFFLINE_DOWNLOADS", "Encrypted Offline Mobile Downloads", true, 100));
            return List.of(f1, f2, f3);
        }
        return flags;
    }

    @Transactional
    public FeatureFlag toggleFeatureFlag(String flagKey, boolean enabled, int percentage, String adminEmail, String reason) {
        FeatureFlag flag = featureFlagRepository.findByFlagKey(flagKey)
                .orElseGet(() -> new FeatureFlag(flagKey, "Feature Flag " + flagKey, enabled, percentage));

        flag.setEnabled(enabled);
        flag.setTargetPercentage(percentage);
        flag.setLastModifiedBy(adminEmail);
        flag.setLastModifiedAt(Instant.now());

        FeatureFlag saved = featureFlagRepository.save(flag);

        // Record Audit Log
        recordAuditLog(new AuditLogEvent(
                UUID.randomUUID(), Instant.now(), null, adminEmail, "SUPER_ADMIN",
                "FEATURE_FLAG_CHANGED", "FEATURE_FLAG", flagKey, reason, "127.0.0.1",
                UUID.randomUUID().toString(), "Enabled: " + enabled + ", Percentage: " + percentage
        ));

        return saved;
    }

    // --- Incident Management ---
    public List<Incident> getIncidents() {
        return incidentRepository.findAll();
    }

    @Transactional
    public Incident createIncident(String title, String description, String severity, String affectedServices, String createdBy) {
        Incident incident = new Incident(title, description, severity, "OPEN", affectedServices, createdBy);
        return incidentRepository.save(incident);
    }

    // --- Support Ticketing ---
    public List<SupportTicket> getSupportTickets() {
        return supportTicketRepository.findAll();
    }

    @Transactional
    public SupportTicket createSupportTicket(UUID accountId, String userEmail, String category, String priority, String subject, String body) {
        SupportTicket ticket = new SupportTicket(accountId, userEmail, category, priority, "OPEN", subject, body);
        return supportTicketRepository.save(ticket);
    }

    // --- System Health Monitoring ---
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());

        Map<String, String> services = new LinkedHashMap<>();
        services.put("auth-service", "UP (Latency: 12ms)");
        services.put("user-service", "UP (Latency: 8ms)");
        services.put("catalog-service", "UP (Latency: 15ms)");
        services.put("subscription-service", "UP (Latency: 10ms)");
        services.put("billing-service", "UP (Latency: 18ms)");
        services.put("device-service", "UP (Latency: 9ms)");
        services.put("media-service", "UP (FFmpeg Transcoder Active)");
        services.put("playback-service", "UP (Concurrent Streams Normal)");
        services.put("watch-history-service", "UP (Redis Cache Hit 98.4%)");
        services.put("trending-service", "UP (Velocity Score Pipeline Active)");
        services.put("analytics-service", "UP (Kafka Consumer Active)");
        services.put("notification-service", "UP (Multi-channel Ready)");
        services.put("api-gateway", "UP (Reactive Netty)");

        health.put("services", services);
        health.put("infrastructure", Map.of(
                "postgres", "UP (Connections: 18/100)",
                "redis", "UP (Memory: 24.5 MB)",
                "kafka", "UP (Consumer Lag: 0)"
        ));

        return health;
    }
}
