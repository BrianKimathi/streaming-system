package com.streamx.device.client;

import com.streamx.device.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SubscriptionServiceClientTest {

    private MockRestServiceServer server;
    private SubscriptionServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://subscription-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SubscriptionServiceClient(builder.build());
    }

    @Test
    void parsesEntitlements() {
        UUID accountId = UUID.randomUUID();
        server.expect(requestTo("http://subscription-service/api/v1/subscriptions/internal/entitlements/" + accountId))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"status\":\"ACTIVE\",\"maxRegisteredDevices\":4}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(new DeviceEntitlement("ACTIVE", 4)), client.findEntitlements(accountId));
    }

    @Test
    void noSubscriptionIsEmpty() {
        UUID accountId = UUID.randomUUID();
        server.expect(requestTo("http://subscription-service/api/v1/subscriptions/internal/entitlements/" + accountId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"message\":\"No subscription found\"}"));

        assertTrue(client.findEntitlements(accountId).isEmpty());
    }

    @Test
    void serverErrorIsUnavailable() {
        UUID accountId = UUID.randomUUID();
        server.expect(requestTo("http://subscription-service/api/v1/subscriptions/internal/entitlements/" + accountId))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(ServiceUnavailableException.class, () -> client.findEntitlements(accountId));
    }
}
