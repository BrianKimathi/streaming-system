package com.streamx.auth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.auth.exception.ServiceUnavailableException;
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
public class UserServiceProfileClient implements ProfileDirectoryClient {

    private static final String UNAVAILABLE = "Profile service is unavailable. Please try again shortly.";

    private final RestClient restClient;

    @Autowired
    public UserServiceProfileClient(@Value("${services.user-url:http://localhost:8082}") String userServiceUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().baseUrl(userServiceUrl).requestFactory(factory).build();
    }

    UserServiceProfileClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<UUID> findOwnerAccountId(UUID profileId) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/api/v1/profiles/internal/{profileId}/owner", profileId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(UNAVAILABLE, e);
        }

        String accountId = body == null ? "" : body.path("data").path("accountId").asText("");
        try {
            return Optional.of(UUID.fromString(accountId));
        } catch (IllegalArgumentException e) {
            throw new ServiceUnavailableException(UNAVAILABLE);
        }
    }
}
