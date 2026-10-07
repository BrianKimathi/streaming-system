package com.streamx.admin.controller;

import com.streamx.admin.domain.*;
import com.streamx.admin.security.AdminIdentity;
import com.streamx.admin.service.AdminService;
import com.streamx.admin.service.SystemHealthService;
import com.streamx.common.dto.ApiResponse;
import com.streamx.common.events.AuditLogEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final SystemHealthService systemHealthService;

    public AdminController(AdminService adminService, SystemHealthService systemHealthService) {
        this.adminService = adminService;
        this.systemHealthService = systemHealthService;
    }

    // --- Audit Logs ---
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs() {
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved successfully", adminService.getAuditLogs()));
    }

    @PostMapping("/audit-logs")
    public ResponseEntity<ApiResponse<AuditLog>> recordAuditLog(HttpServletRequest request, @RequestBody AuditLogEvent event) {
        AuditLog log = adminService.recordAuditLog(AdminIdentity.from(request), event);
        return ResponseEntity.ok(ApiResponse.success("Audit log recorded successfully", log));
    }

    // --- Feature Flags ---
    @GetMapping("/feature-flags")
    public ResponseEntity<ApiResponse<List<FeatureFlag>>> getFeatureFlags() {
        return ResponseEntity.ok(ApiResponse.success("Feature flags retrieved successfully", adminService.getFeatureFlags()));
    }

    @PostMapping("/feature-flags")
    public ResponseEntity<ApiResponse<FeatureFlag>> createFeatureFlag(
            HttpServletRequest request,
            @RequestParam String flagKey,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(defaultValue = "0") int targetPercentage) {
        FeatureFlag flag = adminService.createFeatureFlag(AdminIdentity.from(request), flagKey, description, enabled, targetPercentage);
        return ResponseEntity.ok(ApiResponse.success("Feature flag created", flag));
    }

    @PostMapping("/feature-flags/toggle")
    public ResponseEntity<ApiResponse<FeatureFlag>> toggleFeatureFlag(
            HttpServletRequest request,
            @RequestParam String flagKey,
            @RequestParam boolean enabled,
            @RequestParam(defaultValue = "100") int targetPercentage,
            @RequestParam(required = false) String reason) {
        FeatureFlag flag = adminService.toggleFeatureFlag(AdminIdentity.from(request), flagKey, enabled, targetPercentage, reason);
        return ResponseEntity.ok(ApiResponse.success("Feature flag updated successfully", flag));
    }

    // --- Incidents ---
    @GetMapping("/incidents")
    public ResponseEntity<ApiResponse<List<Incident>>> getIncidents() {
        return ResponseEntity.ok(ApiResponse.success("Platform incidents retrieved successfully", adminService.getIncidents()));
    }

    @PostMapping("/incidents")
    public ResponseEntity<ApiResponse<Incident>> createIncident(
            HttpServletRequest request,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "SEV3") String severity,
            @RequestParam(required = false) String affectedServices) {
        Incident incident = adminService.createIncident(AdminIdentity.from(request), title, description, severity, affectedServices);
        return ResponseEntity.ok(ApiResponse.success("Incident created successfully", incident));
    }

    @PatchMapping("/incidents/{id}/status")
    public ResponseEntity<ApiResponse<Incident>> updateIncidentStatus(
            HttpServletRequest request,
            @PathVariable("id") UUID id,
            @RequestParam String status) {
        Incident incident = adminService.updateIncidentStatus(AdminIdentity.from(request), id, status);
        return ResponseEntity.ok(ApiResponse.success("Incident updated", incident));
    }

    // --- Support Tickets ---
    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<List<SupportTicket>>> getSupportTickets() {
        return ResponseEntity.ok(ApiResponse.success("Support tickets retrieved successfully", adminService.getSupportTickets()));
    }

    @PostMapping("/tickets")
    public ResponseEntity<ApiResponse<SupportTicket>> createSupportTicket(
            HttpServletRequest request,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String userEmail,
            @RequestParam(defaultValue = "ACCOUNT") String category,
            @RequestParam(defaultValue = "MEDIUM") String priority,
            @RequestParam String subject,
            @RequestParam(required = false) String body) {
        SupportTicket ticket = adminService.createSupportTicket(AdminIdentity.from(request), accountId, userEmail, category, priority, subject, body);
        return ResponseEntity.ok(ApiResponse.success("Support ticket created successfully", ticket));
    }

    @PatchMapping("/tickets/{id}")
    public ResponseEntity<ApiResponse<SupportTicket>> updateSupportTicket(
            HttpServletRequest request,
            @PathVariable("id") UUID id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedAgentEmail) {
        SupportTicket ticket = adminService.updateSupportTicket(AdminIdentity.from(request), id, status, assignedAgentEmail);
        return ResponseEntity.ok(ApiResponse.success("Support ticket updated", ticket));
    }

    // --- System Health ---
    @GetMapping("/system/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth() {
        return ResponseEntity.ok(ApiResponse.success("System health", systemHealthService.getSystemHealth()));
    }
}
