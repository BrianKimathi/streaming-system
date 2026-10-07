package com.streamx.common.security;

import java.util.Collection;
import java.util.Set;

public class SecurityConstants {
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String CLAIM_ACCOUNT_ID = "accountId";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PROFILE_ID = "profileId";
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String CLAIM_CONTENT_ID = "cid";
    public static final String CLAIM_SESSION_ID = "sid";
    public static final String TOKEN_TYPE_STREAM = "stream";

    // Header passed by API Gateway to downstream microservices after authentication
    public static final String HEADER_X_ACCOUNT_ID = "X-Account-Id";
    public static final String HEADER_X_PROFILE_ID = "X-Profile-Id";
    public static final String HEADER_X_USER_ROLES = "X-User-Roles";
    public static final String HEADER_X_USER_EMAIL = "X-User-Email";

    public static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_SUPER_ADMIN",
            "ROLE_ADMIN",
            "ROLE_CONTENT_MANAGER",
            "ROLE_FINANCE_MANAGER",
            "ROLE_SUPPORT_AGENT",
            "ROLE_MODERATOR",
            "ROLE_ANALYST"
    );

    public static boolean hasAdminRole(Collection<String> roles) {
        return roles != null && roles.stream().anyMatch(ADMIN_ROLES::contains);
    }
}
