package com.streamx.watchhistory.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class HttpTrendingClient implements TrendingClient, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HttpTrendingClient.class);

    private final RestClient restClient;
    // Bounded so a trending outage can never pile up threads or memory in watch-history-service.
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(500),
            runnable -> {
                Thread thread = new Thread(runnable, "trending-events");
                thread.setDaemon(true);
                return thread;
            },
            (runnable, pool) -> log.warn("Dropping trending event: queue is full"));

    public HttpTrendingClient(@Value("${services.trending-url:http://localhost:8090}") String trendingUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().baseUrl(trendingUrl).requestFactory(factory).build();
    }

    @Override
    public void recordCompletion(UUID titleId) {
        if (titleId == null) {
            return;
        }
        try {
            executor.execute(() -> send(titleId));
        } catch (RuntimeException e) {
            log.warn("Could not queue trending COMPLETION for {}: {}", titleId, e.getMessage());
        }
    }

    private void send(UUID titleId) {
        try {
            restClient.post()
                    .uri("/api/v1/trending/internal/record")
                    .body(Map.of("titleId", titleId.toString(), "eventType", "COMPLETION"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Trending COMPLETION for {} was not recorded: {}", titleId, e.getMessage());
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
