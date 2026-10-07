package com.streamx.billing.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.billing.exception.ServiceUnavailableException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Calls subscription-service internal endpoints on the service network.
 */
@Component
public class SubscriptionClient {

    private final RestClient restClient;

    public SubscriptionClient(@Value("${services.subscription-url}") String subscriptionUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(subscriptionUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * @throws ResourceNotFoundException if the plan does not exist
     * @throws ServiceUnavailableException if subscription-service cannot be reached
     */
    public PlanDetails getPlan(UUID planId) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/api/v1/subscriptions/internal/plans/{planId}", planId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Plan not found");
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Subscription plans are temporarily unavailable. Please try again shortly.");
        }
        JsonNode data = body == null ? null : body.get("data");
        if (data == null || data.isNull()) {
            throw new ResourceNotFoundException("Plan not found");
        }
        BigDecimal price = data.hasNonNull("price") ? data.get("price").decimalValue() : null;
        return new PlanDetails(
                UUID.fromString(data.path("id").asText()),
                data.path("name").asText(null),
                price,
                data.path("currency").asText(null),
                data.path("active").asBoolean(false));
    }

    /**
     * Activates or renews the subscription for a completed payment. Idempotent per payment transaction id.
     *
     * @throws RestClientException if subscription-service fails; callers retry later
     */
    public ActivatedSubscription activate(UUID accountId, UUID planId, UUID paymentTransactionId) {
        Map<String, String> request = new LinkedHashMap<>();
        request.put("accountId", accountId.toString());
        request.put("planId", planId.toString());
        request.put("paymentTransactionId", paymentTransactionId.toString());

        JsonNode body = restClient.post()
                .uri("/api/v1/subscriptions/internal/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        JsonNode data = body == null ? null : body.get("data");
        if (data == null || !data.hasNonNull("id")) {
            throw new IllegalStateException("Subscription service returned no subscription");
        }
        return new ActivatedSubscription(
                UUID.fromString(data.get("id").asText()),
                data.path("plan").path("name").asText(null),
                data.path("currentPeriodEnd").asText(null));
    }
}
