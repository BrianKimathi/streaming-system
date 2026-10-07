package com.streamx.subscription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionExpiryJob(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void expireOverdueSubscriptions() {
        try {
            subscriptionService.expireOverdueSubscriptions();
        } catch (Exception e) {
            log.error("Subscription expiry job failed", e);
        }
    }
}
