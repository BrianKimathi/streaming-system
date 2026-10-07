package com.streamx.gateway.filter;

import com.streamx.common.security.JwtUtils;
import com.streamx.common.security.SecurityConstants;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final List<String> IDENTITY_HEADERS = List.of(
            SecurityConstants.HEADER_X_ACCOUNT_ID,
            SecurityConstants.HEADER_X_USER_ROLES,
            SecurityConstants.HEADER_X_PROFILE_ID,
            SecurityConstants.HEADER_X_USER_EMAIL
    );

    private static final List<Pattern> PUBLIC_POST_ROUTES = List.of(
            Pattern.compile("^/api/v1/auth/login$"),
            Pattern.compile("^/api/v1/auth/admin/login$"),
            Pattern.compile("^/api/v1/auth/register$"),
            Pattern.compile("^/api/v1/auth/refresh$"),
            Pattern.compile("^/api/v1/auth/phone/send-otp$"),
            Pattern.compile("^/api/v1/auth/phone/verify-otp$"),
            // Safaricom calls this; billing-service authenticates it with the secret path token.
            Pattern.compile("^/api/v1/billing/mpesa/callback/[^/]+$")
    );

    private static final List<Pattern> PUBLIC_GET_ROUTES = List.of(
            Pattern.compile("^/api/v1/catalog/movies(/[^/]+)?$"),
            Pattern.compile("^/api/v1/catalog/tv-shows(/[^/]+)?$"),
            Pattern.compile("^/api/v1/catalog/lookup$"),
            Pattern.compile("^/api/v1/catalog/genres$"),
            Pattern.compile("^/api/v1/subscriptions/plans$"),
            Pattern.compile("^/api/v1/trending$"),
            // HLS playlists/segments; media-service validates the stream token embedded in the path.
            Pattern.compile("^/api/v1/media/stream/[^/]+/[^/]+/[^/]+$")
    );

    // Service-to-service endpoints are only reachable on the internal Docker network.
    private static final Pattern INTERNAL_ROUTE = Pattern.compile("^/api/v1/[^/]+/internal(/.*)?$");

    private static final List<Pattern> ADMIN_ROUTES = List.of(
            Pattern.compile("^/api/v1/admin(/.*)?$"),
            Pattern.compile("^/api/v1/analytics(/.*)?$"),
            Pattern.compile("^/api/v1/[^/]+/admin(/.*)?$"),
            Pattern.compile("^/api/v1/notifications/send$"),
            Pattern.compile("^/api/v1/media/upload(/.*)?$"),
            Pattern.compile("^/api/v1/billing/refund(/.*)?$")
    );

    private static final List<Pattern> ADMIN_WRITE_ROUTES = List.of(
            Pattern.compile("^/api/v1/catalog(/.*)?$"),
            Pattern.compile("^/api/v1/subscriptions/plans(/.*)?$")
    );

    private final JwtUtils jwtUtils;

    public JwtAuthenticationFilter(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.jwtUtils = new JwtUtils(jwtSecret, accessTokenExpirationMs, refreshTokenExpirationMs);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        ServerHttpRequest.Builder builder = request.mutate()
                .headers(headers -> IDENTITY_HEADERS.forEach(headers::remove));

        if (INTERNAL_ROUTE.matcher(path).matches()) {
            return reject(exchange, HttpStatus.NOT_FOUND, "Not found");
        }

        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        boolean isPublic = isPublicRoute(method, path);
        String token = extractToken(request);

        if (token == null) {
            if (isPublic) {
                return chain.filter(exchange.mutate().request(builder.build()).build());
            }
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Claims claims;
        try {
            claims = jwtUtils.extractAllClaims(token);
            if (JwtUtils.isStreamToken(claims)) {
                throw new IllegalArgumentException("Stream tokens cannot be used as access tokens");
            }
        } catch (Exception e) {
            if (isPublic) {
                return chain.filter(exchange.mutate().request(builder.build()).build());
            }
            log.warn("Invalid JWT token for path: {}", path);
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        String accountId = claims.getSubject();
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get(SecurityConstants.CLAIM_ROLES, List.class);
        String email = claims.get(SecurityConstants.CLAIM_EMAIL, String.class);
        String profileId = claims.get(SecurityConstants.CLAIM_PROFILE_ID, String.class);

        if (!isPublic && requiresAdmin(method, path) && !SecurityConstants.hasAdminRole(roles)) {
            log.warn("Account {} without admin role denied access to {} {}", accountId, method, path);
            return reject(exchange, HttpStatus.FORBIDDEN, "Administrator role required");
        }

        builder.header(SecurityConstants.HEADER_X_ACCOUNT_ID, accountId)
                .header(SecurityConstants.HEADER_X_USER_ROLES, roles != null ? String.join(",", roles) : "ROLE_USER");
        if (email != null) {
            builder.header(SecurityConstants.HEADER_X_USER_EMAIL, email);
        }
        if (profileId != null) {
            builder.header(SecurityConstants.HEADER_X_PROFILE_ID, profileId);
        }

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(SecurityConstants.TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean isPublicRoute(HttpMethod method, String path) {
        if (HttpMethod.POST.equals(method)) {
            return PUBLIC_POST_ROUTES.stream().anyMatch(p -> p.matcher(path).matches());
        }
        if (HttpMethod.GET.equals(method)) {
            return PUBLIC_GET_ROUTES.stream().anyMatch(p -> p.matcher(path).matches());
        }
        return false;
    }

    private boolean requiresAdmin(HttpMethod method, String path) {
        if (ADMIN_ROUTES.stream().anyMatch(p -> p.matcher(path).matches())) {
            return true;
        }
        return !HttpMethod.GET.equals(method)
                && ADMIN_WRITE_ROUTES.stream().anyMatch(p -> p.matcher(path).matches());
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"message\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
