package com.streamx.playback.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.playback.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

@Component
public class HttpMediaClient implements MediaClient {

    private final RestClient restClient;

    public HttpMediaClient(@Value("${services.media-url:http://localhost:8087}") String mediaUrl) {
        this.restClient = ServiceRestClients.create(mediaUrl);
    }

    @Override
    public Optional<MediaStatus> mediaStatus(UUID contentId) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri("/api/v1/media/internal/{contentId}/status", contentId)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Video service is temporarily unavailable. Try again shortly.", e);
        }

        JsonNode data = HttpDeviceClient.payload(body);
        String status = data.path("status").asText(null);
        if (status == null) {
            throw new ServiceUnavailableException("Video service returned an invalid response. Try again shortly.");
        }
        JsonNode duration = data.path("durationSeconds");
        Integer durationSeconds = duration.isNumber() ? duration.asInt() : null;
        return Optional.of(new MediaStatus(status, durationSeconds));
    }
}
