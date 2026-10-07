package com.streamx.billing.client;

import java.util.UUID;

/**
 * The subset of subscription-service's SubscriptionResponse returned after activation.
 */
public record ActivatedSubscription(UUID id, String planName, String currentPeriodEnd) {
}
