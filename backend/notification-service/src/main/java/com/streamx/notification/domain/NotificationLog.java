package com.streamx.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "channel", nullable = false)
    private String channel; // EMAIL, SMS, IN_APP

    @Column(name = "template", nullable = false)
    private String template; // WELCOME, OTP, PAYMENT_SUCCESS, PAYMENT_FAILED

    @Column(name = "subject")
    private String subject;

    @Column(name = "body", length = 1000)
    private String body;

    @Column(name = "status", nullable = false)
    private String status; // SENT, FAILED, PENDING

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NotificationLog() {
    }

    public NotificationLog(UUID accountId, String recipient, String channel, String template, String subject, String body, String status) {
        this.accountId = accountId;
        this.recipient = recipient;
        this.channel = channel;
        this.template = template;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.createdAt = Instant.now();
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

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
