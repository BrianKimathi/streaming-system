package com.streamx.playback.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.streamx.playback.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class HttpDeviceClient implements DeviceClient {

    private final RestClient restClient;

    public HttpDeviceClient(@Value("${services.device-url:http://localhost:8086}") String deviceUrl) {
        this.restClient = ServiceRestClients.create(deviceUrl);
    }

    @Override
    public DeviceStatus deviceStatus(UUID accountId, UUID deviceId) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/api/v1/devices/internal/{deviceId}/status?accountId={accountId}", deviceId, accountId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return DeviceStatus.NOT_FOUND;
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Device verification is temporarily unavailable. Try again shortly.", e);
        }

        String status = payload(body).path("status").asText("");
        return switch (status) {
            case "ACTIVE" -> DeviceStatus.ACTIVE;
            case "REVOKED" -> DeviceStatus.REVOKED;
            default -> throw new ServiceUnavailableException(
                    "Device verification is temporarily unavailable. Try again shortly.");
        };
    }

    static JsonNode payload(JsonNode body) {
        if (body == null) {
            return MissingNode.getInstance();
        }
        JsonNode data = body.path("data");
        return data.isObject() ? data : body;
    }
}
