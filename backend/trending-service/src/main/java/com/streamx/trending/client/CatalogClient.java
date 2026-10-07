package com.streamx.trending.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.streamx.trending.exception.CatalogUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder, @Value("${services.catalog-url}") String catalogUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder.baseUrl(catalogUrl).requestFactory(requestFactory).build();
    }

    /**
     * Resolves a movie, show or episode id via catalog's internal endpoint (any status).
     *
     * @return empty when the catalog does not know the id
     * @throws CatalogUnavailableException when the catalog cannot be reached or answers unexpectedly
     */
    public Optional<CatalogTitle> findTitle(UUID id) {
        try {
            Envelope body = restClient.get()
                    .uri("/api/v1/catalog/internal/titles/{id}", id)
                    .retrieve()
                    .body(Envelope.class);
            if (body == null || body.data() == null) {
                throw new CatalogUnavailableException("Catalog returned an empty title response", null);
            }
            return Optional.of(body.data());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new CatalogUnavailableException("Catalog service is unavailable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(CatalogTitle data) {
    }
}
