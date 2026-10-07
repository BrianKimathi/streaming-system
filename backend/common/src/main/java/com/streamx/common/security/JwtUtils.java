package com.streamx.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class JwtUtils {

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtUtils(String secretKey, long accessTokenExpirationMs, long refreshTokenExpirationMs) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(String accountId, String email, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(SecurityConstants.CLAIM_ACCOUNT_ID, accountId);
        claims.put(SecurityConstants.CLAIM_EMAIL, email);
        claims.put(SecurityConstants.CLAIM_ROLES, roles);
        return buildToken(claims, accountId, accessTokenExpirationMs);
    }

    public String generateProfileAccessToken(String accountId, String email, List<String> roles, String profileId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(SecurityConstants.CLAIM_ACCOUNT_ID, accountId);
        claims.put(SecurityConstants.CLAIM_EMAIL, email);
        claims.put(SecurityConstants.CLAIM_ROLES, roles);
        claims.put(SecurityConstants.CLAIM_PROFILE_ID, profileId);
        return buildToken(claims, accountId, accessTokenExpirationMs);
    }

    /**
     * Short-lived, content-scoped token embedded in HLS stream URLs. It is not an access token:
     * the gateway refuses it as a bearer token and media-service only accepts it for the content it names.
     */
    public String generateStreamToken(String accountId, String contentId, String sessionId, long ttlMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(SecurityConstants.CLAIM_TOKEN_TYPE, SecurityConstants.TOKEN_TYPE_STREAM);
        claims.put(SecurityConstants.CLAIM_CONTENT_ID, contentId);
        claims.put(SecurityConstants.CLAIM_SESSION_ID, sessionId);
        return buildToken(claims, accountId, ttlMs);
    }

    /** Returns the claims if the token is a valid, unexpired stream token for the given content, otherwise null. */
    public Claims parseStreamToken(String token, String contentId) {
        try {
            Claims claims = extractAllClaims(token);
            if (!SecurityConstants.TOKEN_TYPE_STREAM.equals(claims.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class))) {
                return null;
            }
            if (contentId == null || !contentId.equalsIgnoreCase(claims.get(SecurityConstants.CLAIM_CONTENT_ID, String.class))) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isStreamToken(Claims claims) {
        return SecurityConstants.TOKEN_TYPE_STREAM.equals(claims.get(SecurityConstants.CLAIM_TOKEN_TYPE, String.class));
    }

    public String generateRefreshToken(String accountId) {
        return buildToken(new HashMap<>(), accountId, refreshTokenExpirationMs);
    }

    private String buildToken(Map<String, Object> claims, String subject, long expirationMs) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getAccountIdFromToken(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get(SecurityConstants.CLAIM_ROLES, List.class);
    }

    public String getEmailFromToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get(SecurityConstants.CLAIM_EMAIL, String.class);
    }

    public String getProfileIdFromToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get(SecurityConstants.CLAIM_PROFILE_ID, String.class);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }
}
