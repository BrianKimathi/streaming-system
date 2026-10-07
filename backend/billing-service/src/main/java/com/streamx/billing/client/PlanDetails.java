package com.streamx.billing.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The subset of subscription-service's PlanResponse that billing needs to price a checkout.
 */
public record PlanDetails(UUID id, String name, BigDecimal price, String currency, boolean active) {
}
