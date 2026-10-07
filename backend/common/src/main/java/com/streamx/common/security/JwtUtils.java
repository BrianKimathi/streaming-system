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
