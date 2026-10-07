package com.streamx.auth.dto;

import com.streamx.auth.domain.Account;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccountResponse {
    private String accountId;
    private String email;
    private String phoneNumber;
    private boolean emailVerified;
    private boolean phoneVerified;
    private String status;
    private List<String> roles;
    private LocalDateTime createdAt;

    public AccountResponse() {
    }

    public static AccountResponse from(Account account) {
        AccountResponse r = new AccountResponse();
        r.accountId = account.getId().toString();
        r.email = account.getEmail();
        r.phoneNumber = account.getPhoneNumber();
        r.emailVerified = account.isEmailVerified();
        r.phoneVerified = account.isPhoneVerified();
        r.status = account.getStatus().name();
        r.roles = new ArrayList<>(account.getRoles());
        r.createdAt = account.getCreatedAt();
        return r;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public void setPhoneVerified(boolean phoneVerified) {
        this.phoneVerified = phoneVerified;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
