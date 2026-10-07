package com.streamx.playback.client;

import java.util.UUID;

public interface EntitlementClient {

    /**
     * Returns the concurrent-stream allowance of the account's current subscription.
     * Throws a BadRequestException when the account has no subscription that permits playback.
     */
    int maxConcurrentStreams(UUID accountId);
}
