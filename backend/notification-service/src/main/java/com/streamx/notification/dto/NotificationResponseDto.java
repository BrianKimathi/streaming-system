package com.streamx.notification.dto;

import java.time.Instant;
import java.util.UUID;

public class NotificationResponseDto {
    private UUID id;
    private UUID accountId;
    private String recipient;
    private String channel;
    private String template;
    private String subject;
    private String body;
    private String status;
    private Instant createdAt;

    public NotificationResponseDto() {
    }

    public NotificationResponseDto(UUID id, UUID accountId, String recipient, String channel,
                                   String template, String subject, String body, String status, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.recipient = recipient;
        this.channel = channel;
        this.template = template;
        this.subject = subject;
        this.body = body;
        this.status = status;
        this.createdAt = createdAt;
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
