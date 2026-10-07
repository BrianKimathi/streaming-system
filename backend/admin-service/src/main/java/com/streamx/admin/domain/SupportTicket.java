package com.streamx.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "category", nullable = false)
    private String category; // ACCOUNT, BILLING, SUBSCRIPTION, PLAYBACK, DEVICE

    @Column(name = "priority", nullable = false)
    private String priority; // LOW, MEDIUM, HIGH, URGENT

    @Column(name = "status", nullable = false)
    private String status; // OPEN, IN_PROGRESS, RESOLVED, CLOSED

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", length = 2000)
    private String body;

    @Column(name = "assigned_agent_email")
    private String assignedAgentEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SupportTicket() {
    }

    public SupportTicket(UUID accountId, String userEmail, String category, String priority, String status, String subject, String body) {
        this.accountId = accountId;
        this.userEmail = userEmail;
        this.category = category;
        this.priority = priority;
        this.status = status;
        this.subject = subject;
        this.body = body;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getAssignedAgentEmail() {
        return assignedAgentEmail;
    }

    public void setAssignedAgentEmail(String assignedAgentEmail) {
        this.assignedAgentEmail = assignedAgentEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
