package com.streamx.notification.dto;

import java.util.UUID;

public class SendNotificationRequestDto {
    private UUID accountId;
    private String recipient;
    private String channel; // EMAIL, SMS
    private String template; // WELCOME, OTP, PAYMENT_SUCCESS, PAYMENT_FAILED
    private String subject;
    private String body;

    public SendNotificationRequestDto() {
    }

    public SendNotificationRequestDto(UUID accountId, String recipient, String channel, String template, String subject, String body) {
        this.accountId = accountId;
        this.recipient = recipient;
        this.channel = channel;
        this.template = template;
        this.subject = subject;
        this.body = body;
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
}
