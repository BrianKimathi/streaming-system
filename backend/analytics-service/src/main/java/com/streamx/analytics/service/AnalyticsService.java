package com.streamx.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Builds the admin dashboard from live data owned by each microservice.
 * Every section reports whether it could be loaded so the UI never shows invented numbers.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, String> sources = new LinkedHashMap<>();

    public AnalyticsService(ObjectMapper objectMapper,
                            @Value("${services.auth-url}") String authUrl,
                            @Value("${services.catalog-url}") String catalogUrl,
                            @Value("${services.subscription-url}") String subscriptionUrl,
                            @Value("${services.billing-url}") String billingUrl,
                            @Value("${services.device-url}") String deviceUrl,
                            @Value("${services.media-url}") String mediaUrl,
                            @Value("${services.playback-url}") String playbackUrl,
                            @Value("${services.watch-history-url}") String watchHistoryUrl,
                            @Value("${services.notification-url}") String notificationUrl) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(6000);
        this.restClient = RestClient.builder().requestFactory(factory).build();

        sources.put("users", authUrl + "/api/v1/auth/admin/stats");
        sources.put("catalog", catalogUrl + "/api/v1/catalog/admin/stats");
        sources.put("subscriptions", subscriptionUrl + "/api/v1/subscriptions/admin/stats");
        sources.put("billing", billingUrl + "/api/v1/billing/admin/stats");
        sources.put("devices", deviceUrl + "/api/v1/devices/admin/stats");
        sources.put("media", mediaUrl + "/api/v1/media/admin/stats");
        sources.put("playback", playbackUrl + "/api/v1/playback/admin/stats");
        sources.put("watchHistory", watchHistoryUrl + "/api/v1/watch-history/admin/stats");
        sources.put("notifications", notificationUrl + "/api/v1/notifications/admin/stats");
    }

    public Map<String, Object> getDashboard() {
        Map<String, CompletableFuture<Map<String, Object>>> futures = new LinkedHashMap<>();
        sources.forEach((section, url) ->
                futures.put(section, CompletableFuture.supplyAsync(() -> fetchSection(section, url), executor)));

        Map<String, Object> sections = new LinkedHashMap<>();
        futures.forEach((section, future) -> {
            try {
                sections.put(section, future.get(8, TimeUnit.SECONDS));
            } catch (Exception e) {
                sections.put(section, unavailable("Timed out"));
            }
        });

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("generatedAt", Instant.now().toString());
        dashboard.put("sections", sections);
        return dashboard;
    }

    private Map<String, Object> fetchSection(String section, String url) {
        try {
            JsonNode body = restClient.get().uri(url).retrieve().body(JsonNode.class);
            JsonNode data = body == null ? null : body.get("data");
            if (data == null || data.isNull()) {
                return unavailable("Service returned no data");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", true);
            result.put("data", objectMapper.convertValue(data, Map.class));
            return result;
        } catch (Exception e) {
            log.warn("Could not load {} stats from {}: {}", section, url, e.getMessage());
            return unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static Map<String, Object> unavailable(String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", false);
        result.put("error", error);
        return result;
    }
}
