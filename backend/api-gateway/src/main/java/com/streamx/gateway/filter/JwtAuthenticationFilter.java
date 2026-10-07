package com.streamx.gateway.filter;

import com.streamx.common.security.JwtUtils;
import com.streamx.common.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtils jwtUtils;

    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/phone/send-otp",
            "/api/v1/auth/phone/verify-otp",
            "/api/v1/catalog/movies",
            "/api/v1/catalog/genres",
            "/api/v1/subscriptions/plans",
            "/api/v1/trending"
    );

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

        if (isPublicRoute(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(SecurityConstants.TOKEN_PREFIX)) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(SecurityConstants.TOKEN_PREFIX.length());

        if (!jwtUtils.validateToken(token)) {
            log.warn("Invalid JWT token for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String accountId = jwtUtils.getAccountIdFromToken(token);
            List<String> roles = jwtUtils.getRolesFromToken(token);
            String profileId = jwtUtils.getProfileIdFromToken(token);

            ServerHttpRequest.Builder builder = request.mutate()
                    .header(SecurityConstants.HEADER_X_ACCOUNT_ID, accountId)
                    .header(SecurityConstants.HEADER_X_USER_ROLES, roles != null ? String.join(",", roles) : "ROLE_USER");

            if (profileId != null) {
                builder.header(SecurityConstants.HEADER_X_PROFILE_ID, profileId);
            }

            ServerHttpRequest mutatedRequest = builder.build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.error("Error processing JWT claims: {}", e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(path::equalsIgnoreCase);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
