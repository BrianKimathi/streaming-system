package com.streamx.auth.dto;

import java.util.List;

public class AuthResponse {
    private String accountId;
    private String email;
    private String phoneNumber;
    private boolean emailVerified;
    private boolean phoneVerified;
    private List<String> roles;
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";

    public AuthResponse() {
    }

    public AuthResponse(String accountId, String email, String phoneNumber, boolean emailVerified,
                        boolean phoneVerified, List<String> roles, String accessToken,
                        String refreshToken, String tokenType) {
        this.accountId = accountId;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.emailVerified = emailVerified;
        this.phoneVerified = phoneVerified;
        this.roles = roles;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType != null ? tokenType : "Bearer";
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

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}
