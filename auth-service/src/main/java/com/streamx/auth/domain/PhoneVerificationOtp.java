package com.streamx.auth.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "phone_verification_otps")
public class PhoneVerificationOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String hashedOtp;

    private int attempts = 0;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime resendCooldownUntil;

    private boolean verified = false;

    private LocalDateTime createdAt;

    public PhoneVerificationOtp() {
    }

    public PhoneVerificationOtp(UUID id, String phoneNumber, String hashedOtp, int attempts,
                                LocalDateTime expiresAt, LocalDateTime resendCooldownUntil,
                                boolean verified, LocalDateTime createdAt) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.hashedOtp = hashedOtp;
        this.attempts = attempts;
        this.expiresAt = expiresAt;
        this.resendCooldownUntil = resendCooldownUntil;
        this.verified = verified;
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getHashedOtp() {
        return hashedOtp;
    }

    public void setHashedOtp(String hashedOtp) {
        this.hashedOtp = hashedOtp;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getResendCooldownUntil() {
        return resendCooldownUntil;
    }

    public void setResendCooldownUntil(LocalDateTime resendCooldownUntil) {
        this.resendCooldownUntil = resendCooldownUntil;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
