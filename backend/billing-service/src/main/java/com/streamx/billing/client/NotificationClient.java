package com.streamx.billing.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends in-app notifications through notification-service. Failures are logged and swallowed:
 * a notification problem must never affect payment processing.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient restClient;

    public NotificationClient(@Value("${services.notification-url}") String notificationUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(notificationUrl)
                .requestFactory(factory)
                .build();
    }

    public void sendInApp(UUID accountId, String template, String subject, String body) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("accountId", accountId.toString());
        request.put("recipient", accountId.toString());
        request.put("channel", "IN_APP");
        request.put("template", template);
        request.put("subject", subject);
        request.put("body", body);
        try {
            restClient.post()
                    .uri("/api/v1/notifications/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Could not send {} notification to account {}: {}", template, accountId, e.getMessage());
        }
    }
}
