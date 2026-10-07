package com.streamx.billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resolves payments the user stopped polling for and retries failed subscription activations.
 */
@Component
public class BillingMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(BillingMaintenanceJob.class);

    private final BillingService billingService;

    public BillingMaintenanceJob(BillingService billingService) {
        this.billingService = billingService;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void reconcileStalePayments() {
        try {
            billingService.reconcileStaleTransactions();
        } catch (Exception e) {
            log.error("Payment reconciliation job failed", e);
        }
    }

    @Scheduled(fixedDelay = 120_000L, initialDelay = 45_000L)
    public void retryActivations() {
        try {
            billingService.retryPendingActivations();
        } catch (Exception e) {
            log.error("Subscription activation retry job failed", e);
        }
    }
}
