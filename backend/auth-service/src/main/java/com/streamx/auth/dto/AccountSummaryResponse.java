package com.streamx.auth.dto;

import com.streamx.auth.domain.Account;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccountSummaryResponse {
    private String id;
    private String email;
    private String phoneNumber;
    private List<String> roles;
    private String status;
    private boolean blocked;
    private boolean emailVerified;
    private boolean phoneVerified;
    private LocalDateTime createdAt;

    public static AccountSummaryResponse from(Account account) {
        AccountSummaryResponse r = new AccountSummaryResponse();
        r.id = account.getId().toString();
        r.email = account.getEmail();
        r.phoneNumber = account.getPhoneNumber();
        r.roles = new ArrayList<>(account.getRoles());
        r.status = account.getStatus().name();
        r.blocked = account.getStatus() != Account.AccountStatus.ACTIVE;
        r.emailVerified = account.isEmailVerified();
        r.phoneVerified = account.isPhoneVerified();
        r.createdAt = account.getCreatedAt();
        return r;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public List<String> getRoles() {
        return roles;
    }

    public String getStatus() {
        return status;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
