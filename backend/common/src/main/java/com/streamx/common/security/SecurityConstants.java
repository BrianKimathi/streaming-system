package com.streamx.common.security;

public class SecurityConstants {
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String CLAIM_ACCOUNT_ID = "accountId";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_PROFILE_ID = "profileId";
    
    // Header passed by API Gateway to downstream microservices after authentication
    public static final String HEADER_X_ACCOUNT_ID = "X-Account-Id";
    public static final String HEADER_X_PROFILE_ID = "X-Profile-Id";
    public static final String HEADER_X_USER_ROLES = "X-User-Roles";
}
