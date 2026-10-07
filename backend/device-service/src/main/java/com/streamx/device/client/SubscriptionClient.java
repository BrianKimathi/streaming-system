package com.streamx.device.client;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionClient {

    /**
     * Returns the account's entitlements, or empty if the account has no subscription.
     * Throws {@link com.streamx.device.exception.ServiceUnavailableException} if subscription-service cannot be reached.
     */
    Optional<DeviceEntitlement> findEntitlements(UUID accountId);
}
