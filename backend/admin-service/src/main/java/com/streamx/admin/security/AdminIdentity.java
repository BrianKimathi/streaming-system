package com.streamx.admin.security;

import com.streamx.common.exception.UnauthorizedException;
import com.streamx.common.security.SecurityConstants;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * The administrator performing a request, as asserted by the API gateway after JWT validation.
 */
public record AdminIdentity(UUID accountId, String email, String roles, String ipAddress) {

    public static AdminIdentity from(HttpServletRequest request) {
        String accountId = request.getHeader(SecurityConstants.HEADER_X_ACCOUNT_ID);
        if (accountId == null || accountId.isBlank()) {
            throw new UnauthorizedException("Missing administrator identity");
        }
        String email = request.getHeader(SecurityConstants.HEADER_X_USER_EMAIL);
        String roles = request.getHeader(SecurityConstants.HEADER_X_USER_ROLES);
        String cloudflareIp = request.getHeader("CF-Connecting-IP");
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip;
        if (cloudflareIp != null && !cloudflareIp.isBlank()) {
            ip = cloudflareIp.trim();
        } else if (forwarded != null && !forwarded.isBlank()) {
            ip = forwarded.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        try {
            return new AdminIdentity(UUID.fromString(accountId), email, roles, ip);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid administrator identity");
        }
    }

    public String displayName() {
        return email != null && !email.isBlank() ? email : accountId.toString();
    }

    public String primaryRole() {
        if (roles == null || roles.isBlank()) {
            return "UNKNOWN";
        }
        return roles.split(",")[0].trim();
    }
}
