package com.streamx.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Probes every platform component on demand. A service counts as UP when it answers HTTP at all
 * (even 401/403/404): that proves the JVM and web server are serving requests.
 */
@Service
public class SystemHealthService {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, String> serviceUrls;
    private final String redisHost;
    private final int redisPort;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SystemHealthService(JdbcTemplate jdbcTemplate,
                               @Value("${health.services:}") String servicesSpec,
                               @Value("${health.redis-host:}") String redisHost,
                               @Value("${health.redis-port:6379}") int redisPort) {
        this.jdbcTemplate = jdbcTemplate;
        this.serviceUrls = parseServices(servicesSpec);
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    static Map<String, String> parseServices(String spec) {
        Map<String, String> services = new LinkedHashMap<>();
        if (spec == null) {
            return services;
        }
        for (String entry : spec.split(",")) {
            String trimmed = entry.trim();
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                services.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }
        return services;
    }

    public Map<String, Object> getSystemHealth() {
        Map<String, CompletableFuture<Map<String, Object>>> serviceChecks = new LinkedHashMap<>();
        serviceUrls.forEach((name, url) ->
                serviceChecks.put(name, CompletableFuture.supplyAsync(() -> probeHttp(url), executor)));
        CompletableFuture<Map<String, Object>> redisCheck = CompletableFuture.supplyAsync(this::probeRedis, executor);

        List<Map<String, Object>> services = new ArrayList<>();
        serviceChecks.forEach((name, future) -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", name);
            result.putAll(await(future));
            services.add(result);
        });

        List<Map<String, Object>> infrastructure = new ArrayList<>();
        Map<String, Object> postgres = new LinkedHashMap<>();
        postgres.put("name", "postgres");
        postgres.putAll(probePostgres());
        infrastructure.add(postgres);
        if (redisHost != null && !redisHost.isBlank()) {
            Map<String, Object> redis = new LinkedHashMap<>();
            redis.put("name", "redis");
            redis.putAll(await(redisCheck));
            infrastructure.add(redis);
        }

        long down = services.stream().filter(s -> !"UP".equals(s.get("status"))).count()
                + infrastructure.stream().filter(s -> !"UP".equals(s.get("status"))).count();

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", down == 0 ? "UP" : "DEGRADED");
        health.put("componentsDown", down);
        health.put("checkedAt", Instant.now().toString());
        health.put("services", services);
        health.put("infrastructure", infrastructure);
        return health;
    }

    private Map<String, Object> await(CompletableFuture<Map<String, Object>> future) {
        try {
            return future.get(6, TimeUnit.SECONDS);
        } catch (Exception e) {
            return down("Health check timed out", null);
        }
    }

    private Map<String, Object> probeHttp(String baseUrl) {
        long start = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            long latency = (System.nanoTime() - start) / 1_000_000;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", response.statusCode() >= 500 ? "DOWN" : "UP");
            result.put("latencyMs", latency);
            result.put("httpStatus", response.statusCode());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return down("Interrupted", null);
        } catch (Exception e) {
            return down(e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""),
                    (System.nanoTime() - start) / 1_000_000);
        }
    }

    private Map<String, Object> probePostgres() {
        long start = System.nanoTime();
        try {
            Integer connections = jdbcTemplate.queryForObject("select count(*) from pg_stat_activity", Integer.class);
            String maxConnections = jdbcTemplate.queryForObject("show max_connections", String.class);
            String version = jdbcTemplate.queryForObject("show server_version", String.class);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "UP");
            result.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
            result.put("connections", connections);
            result.put("maxConnections", maxConnections == null ? null : Integer.parseInt(maxConnections));
            result.put("version", version);
            return result;
        } catch (Exception e) {
            log.warn("Postgres health check failed: {}", e.getMessage());
            return down(e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }

    private Map<String, Object> probeRedis() {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost, redisPort), 2000);
            socket.setSoTimeout(2000);
            OutputStream out = socket.getOutputStream();
            out.write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[64];
            int read = in.read(buffer);
            String reply = read > 0 ? new String(buffer, 0, read, StandardCharsets.US_ASCII).trim() : "";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", reply.startsWith("+PONG") ? "UP" : "DOWN");
            result.put("latencyMs", (System.nanoTime() - start) / 1_000_000);
            if (!reply.startsWith("+PONG")) {
                result.put("error", "Unexpected reply: " + reply);
            }
            return result;
        } catch (Exception e) {
            return down(e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }

    private static Map<String, Object> down(String error, Long latencyMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "DOWN");
        if (latencyMs != null) {
            result.put("latencyMs", latencyMs);
        }
        result.put("error", error);
        return result;
    }
}
