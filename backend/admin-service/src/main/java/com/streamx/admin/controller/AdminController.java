package com.streamx.admin.controller;

import com.streamx.admin.domain.*;
import com.streamx.admin.service.AdminService;
import com.streamx.common.dto.ApiResponse;
import com.streamx.common.events.AuditLogEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // --- Audit Logs ---
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<AuditLog>>> getAuditLogs() {
        List<AuditLog> logs = adminService.getAuditLogs();
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved successfully", logs));
    }

    @PostMapping("/audit-logs")
    public ResponseEntity<ApiResponse<AuditLog>> recordAuditLog(@RequestBody AuditLogEvent event) {
        AuditLog log = adminService.recordAuditLog(event);
        return ResponseEntity.ok(ApiResponse.success("Audit log recorded successfully", log));
    }

    // --- Feature Flags ---
    @GetMapping("/feature-flags")
    public ResponseEntity<ApiResponse<List<FeatureFlag>>> getFeatureFlags() {
        List<FeatureFlag> flags = adminService.getFeatureFlags();
        return ResponseEntity.ok(ApiResponse.success("Feature flags retrieved successfully", flags));
    }

    @PostMapping("/feature-flags/toggle")
    public ResponseEntity<ApiResponse<FeatureFlag>> toggleFeatureFlag(
            @RequestParam String flagKey,
            @RequestParam boolean enabled,
            @RequestParam(defaultValue = "100") int targetPercentage,
            @RequestParam(defaultValue = "admin@streamx.io") String adminEmail,
            @RequestParam(defaultValue = "Administrative update") String reason) {
        FeatureFlag flag = adminService.toggleFeatureFlag(flagKey, enabled, targetPercentage, adminEmail, reason);
        return ResponseEntity.ok(ApiResponse.success("Feature flag updated successfully", flag));
    }

    // --- Incidents ---
    @GetMapping("/incidents")
    public ResponseEntity<ApiResponse<List<Incident>>> getIncidents() {
        List<Incident> incidents = adminService.getIncidents();
        return ResponseEntity.ok(ApiResponse.success("Platform incidents retrieved successfully", incidents));
    }

    @PostMapping("/incidents")
    public ResponseEntity<ApiResponse<Incident>> createIncident(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam(defaultValue = "SEV2") String severity,
            @RequestParam(defaultValue = "All Services") String affectedServices,
            @RequestParam(defaultValue = "admin@streamx.io") String createdBy) {
        Incident incident = adminService.createIncident(title, description, severity, affectedServices, createdBy);
        return ResponseEntity.ok(ApiResponse.success("Incident created successfully", incident));
    }

    // --- Support Tickets ---
    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<List<SupportTicket>>> getSupportTickets() {
        List<SupportTicket> tickets = adminService.getSupportTickets();
        return ResponseEntity.ok(ApiResponse.success("Support tickets retrieved successfully", tickets));
    }

    @PostMapping("/tickets")
    public ResponseEntity<ApiResponse<SupportTicket>> createSupportTicket(
            @RequestParam UUID accountId,
            @RequestParam String userEmail,
            @RequestParam String category,
            @RequestParam String priority,
            @RequestParam String subject,
            @RequestParam String body) {
        SupportTicket ticket = adminService.createSupportTicket(accountId, userEmail, category, priority, subject, body);
        return ResponseEntity.ok(ApiResponse.success("Support ticket created successfully", ticket));
    }

    // --- System Health ---
    @GetMapping("/system/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemHealth() {
        Map<String, Object> health = adminService.getSystemHealth();
        return ResponseEntity.ok(ApiResponse.success("System health operational status", health));
    }
}
