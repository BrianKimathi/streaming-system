package com.streamx.billing.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Admin-managed Daraja settings (single row). Secret columns hold AES-GCM ciphertext, never plain values.
 */
@Entity
@Table(name = "mpesa_settings")
public class MpesaSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    private String environment;
    private String shortcode;
    private String transactionType;

    @Column(length = 500)
    private String callbackBaseUrl;

    @Column(length = 1024)
    private String consumerKeyEnc;

    @Column(length = 1024)
    private String consumerSecretEnc;

    @Column(length = 1024)
    private String passkeyEnc;

    @Column(length = 1024)
    private String callbackTokenEnc;

    private LocalDateTime updatedAt;
    private String updatedBy;

    public MpesaSettings() {
    }

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getShortcode() {
        return shortcode;
    }

    public void setShortcode(String shortcode) {
        this.shortcode = shortcode;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }

    public void setCallbackBaseUrl(String callbackBaseUrl) {
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public String getConsumerKeyEnc() {
        return consumerKeyEnc;
    }

    public void setConsumerKeyEnc(String consumerKeyEnc) {
        this.consumerKeyEnc = consumerKeyEnc;
    }

    public String getConsumerSecretEnc() {
        return consumerSecretEnc;
    }

    public void setConsumerSecretEnc(String consumerSecretEnc) {
        this.consumerSecretEnc = consumerSecretEnc;
    }

    public String getPasskeyEnc() {
        return passkeyEnc;
    }

    public void setPasskeyEnc(String passkeyEnc) {
        this.passkeyEnc = passkeyEnc;
    }

    public String getCallbackTokenEnc() {
        return callbackTokenEnc;
    }

    public void setCallbackTokenEnc(String callbackTokenEnc) {
        this.callbackTokenEnc = callbackTokenEnc;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
