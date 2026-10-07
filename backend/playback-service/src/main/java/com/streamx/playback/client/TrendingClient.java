package com.streamx.playback.client;

import java.util.UUID;

public interface TrendingClient {

    /** Fire-and-forget: must return immediately and never throw, even if trending-service is down. */
    void recordView(UUID titleId);
}
