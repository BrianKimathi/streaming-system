package com.streamx.playback.client;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTrendingClientTest {

    @Test
    void unreachableTrendingNeverBlocksOrFailsTheCaller() throws Exception {
        HttpTrendingClient client = new HttpTrendingClient("http://127.0.0.1:1");
        try {
            long start = System.nanoTime();
            assertDoesNotThrow(() -> client.recordView(UUID.randomUUID()));
            assertDoesNotThrow(() -> client.recordView(null));
            assertTrue((System.nanoTime() - start) / 1_000_000 < 500, "recordView must return immediately");
        } finally {
            client.destroy();
        }
    }
}
