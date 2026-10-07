package com.streamx.device.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.device.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

@Component
public class SubscriptionServiceClient implements SubscriptionClient {

    private static final String UNAVAILABLE = "Subscription service is unavailable. Please try again shortly.";

    private final RestClient restClient;

    @Autowired
    public SubscriptionServiceClient(@Value("${services.subscription-url:http://localhost:8084}") String subscriptionUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().baseUrl(subscriptionUrl).requestFactory(factory).build();
    }

    SubscriptionServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<DeviceEntitlement> findEntitlements(UUID accountId) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/api/v1/subscriptions/internal/entitlements/{accountId}", accountId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(UNAVAILABLE, e);
        }

        JsonNode data = body == null ? null : body.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new DeviceEntitlement(
                data.path("status").asText(""),
                data.path("maxRegisteredDevices").asInt(0)));
    }
}
