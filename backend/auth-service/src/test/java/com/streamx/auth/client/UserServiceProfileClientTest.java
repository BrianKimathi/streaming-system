package com.streamx.auth.client;

import com.streamx.auth.exception.ServiceUnavailableException;
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

class UserServiceProfileClientTest {

    private MockRestServiceServer server;
    private UserServiceProfileClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://user-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UserServiceProfileClient(builder.build());
    }

    @Test
    void returnsOwnerAccountId() {
        UUID profileId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        server.expect(requestTo("http://user-service/api/v1/profiles/internal/" + profileId + "/owner"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"accountId\":\"" + accountId + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(accountId), client.findOwnerAccountId(profileId));
    }

    @Test
    void returnsEmptyWhenProfileDoesNotExist() {
        UUID profileId = UUID.randomUUID();
        server.expect(requestTo("http://user-service/api/v1/profiles/internal/" + profileId + "/owner"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\":false,\"message\":\"Profile not found\"}"));

        assertTrue(client.findOwnerAccountId(profileId).isEmpty());
    }

    @Test
    void serverErrorIsReportedAsUnavailable() {
        UUID profileId = UUID.randomUUID();
        server.expect(requestTo("http://user-service/api/v1/profiles/internal/" + profileId + "/owner"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ServiceUnavailableException.class, () -> client.findOwnerAccountId(profileId));
    }
}
