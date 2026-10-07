package com.streamx.playback.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.security.SecurityConstants;
import com.streamx.playback.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Set;
import java.util.UUID;

@Component
public class SubscriptionEntitlementClient implements EntitlementClient {

    public static final String SUBSCRIPTION_REQUIRED = "An active subscription is required";

    private static final Set<String> PLAYABLE_STATUSES = Set.of("ACTIVE", "TRIAL", "GRACE_PERIOD");

    private final RestClient restClient;

    public SubscriptionEntitlementClient(@Value("${services.subscription-url:http://localhost:8084}") String subscriptionUrl) {
        this.restClient = ServiceRestClients.create(subscriptionUrl);
    }

    @Override
    public int maxConcurrentStreams(UUID accountId) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/api/v1/subscriptions/entitlements")
                    .header(SecurityConstants.HEADER_X_ACCOUNT_ID, accountId.toString())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BadRequestException(SUBSCRIPTION_REQUIRED);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Subscription check is temporarily unavailable. Try again shortly.", e);
        }

        JsonNode data = body == null ? null : body.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            throw new BadRequestException(SUBSCRIPTION_REQUIRED);
        }
        if (!PLAYABLE_STATUSES.contains(data.path("status").asText())) {
            throw new BadRequestException(SUBSCRIPTION_REQUIRED);
        }
        return Math.max(1, data.path("maxConcurrentStreams").asInt(1));
    }
}
