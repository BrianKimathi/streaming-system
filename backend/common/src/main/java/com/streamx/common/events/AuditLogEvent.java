package com.streamx.common.events;

import java.time.Instant;
import java.util.UUID;

public class AuditLogEvent {
    private UUID auditId;
    private Instant timestamp;
    private UUID administratorId;
    private String administratorEmail;
    private String role;
    private String action; // USER_SUSPENDED, REFUND_CREATED, CONTENT_PUBLISHED, etc.
    private String targetType; // USER, SUBSCRIPTION, PAYMENT, CONTENT, SYSTEM
    private String targetId;
    private String reason;
    private String ipAddress;
    private String correlationId;
    private String details;

    public AuditLogEvent() {
    }

    public AuditLogEvent(UUID auditId, Instant timestamp, UUID administratorId, String administratorEmail,
                         String role, String action, String targetType, String targetId,
                         String reason, String ipAddress, String correlationId, String details) {
        this.auditId = auditId;
        this.timestamp = timestamp;
        this.administratorId = administratorId;
        this.administratorEmail = administratorEmail;
        this.role = role;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.ipAddress = ipAddress;
        this.correlationId = correlationId;
        this.details = details;
    }

    public UUID getAuditId() {
        return auditId;
    }

    public void setAuditId(UUID auditId) {
        this.auditId = auditId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public UUID getAdministratorId() {
        return administratorId;
    }

    public void setAdministratorId(UUID administratorId) {
        this.administratorId = administratorId;
    }

    public String getAdministratorEmail() {
        return administratorEmail;
    }

    public void setAdministratorEmail(String administratorEmail) {
        this.administratorEmail = administratorEmail;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}
