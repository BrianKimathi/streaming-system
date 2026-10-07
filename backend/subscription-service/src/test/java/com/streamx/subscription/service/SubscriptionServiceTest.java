package com.streamx.subscription.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.subscription.domain.BillingInterval;
import com.streamx.subscription.domain.Subscription;
import com.streamx.subscription.domain.SubscriptionStatus;
import com.streamx.subscription.domain.VideoResolution;
import com.streamx.subscription.dto.*;
import com.streamx.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionServiceTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private PlanResponse createPlan(String name, String price, BillingInterval interval) {
        CreatePlanRequest req = new CreatePlanRequest();
        req.setName(name);
        req.setPrice(new BigDecimal(price));
        req.setBillingInterval(interval);
        req.setMaxProfiles(3);
        req.setMaxRegisteredDevices(4);
        req.setMaxConcurrentStreams(2);
        req.setMaxResolution(VideoResolution.FHD_1080P);
        return subscriptionService.createPlan(req);
    }

    private SubscriptionResponse activate(String accountId, PlanResponse plan, UUID paymentId) {
        return subscriptionService.activate(new ActivateSubscriptionRequest(accountId, plan.getId(), paymentId.toString()));
    }

    private static void assertCloseTo(LocalDateTime expected, LocalDateTime actual) {
        assertTrue(Math.abs(ChronoUnit.SECONDS.between(expected, actual)) < 5,
                () -> "expected ~" + expected + " but was " + actual);
    }

    @Test
    void createPlanVersionsPreviousPlan() {
        PlanResponse v1 = createPlan("Premium", "999.00", BillingInterval.MONTHLY);
        assertEquals(1, v1.getVersion());
        assertTrue(v1.isActive());

        PlanResponse v2 = createPlan("Premium", "1199.00", BillingInterval.MONTHLY);
        assertEquals(2, v2.getVersion());
        assertTrue(v2.isActive());
        assertFalse(subscriptionService.getPlan(v1.getId()).isActive(), "internal lookup returns inactive versions too");
    }

    @Test
    void activePlansAreSortedByPrice() {
        createPlan("Zeta Premium", "1100.00", BillingInterval.MONTHLY);
        createPlan("Alpha Basic", "300.00", BillingInterval.MONTHLY);
        createPlan("Free Tier", "0", BillingInterval.MONTHLY);

        List<BigDecimal> prices = subscriptionService.getActivePlans().stream().map(PlanResponse::getPrice).toList();
        for (int i = 1; i < prices.size(); i++) {
            assertTrue(prices.get(i - 1).compareTo(prices.get(i)) <= 0, "plans must be sorted by price: " + prices);
        }
    }

    @Test
    void subscribeToFreePlanWorks() {
        PlanResponse free = createPlan("Free", "0.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();

        SubscriptionResponse sub = subscriptionService.subscribe(accountId, new SubscribeRequest(free.getId()));

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertEquals(free.getId(), sub.getPlan().getId());
        assertEquals(3, subscriptionService.getEntitlements(accountId).getMaxProfiles());
    }

    @Test
    void subscribeToPaidPlanIsRejected() {
        PlanResponse paid = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> subscriptionService.subscribe(accountId, new SubscribeRequest(paid.getId())));
        assertEquals("This plan requires payment. Pay with M-Pesa to subscribe.", ex.getMessage());
        assertTrue(subscriptionRepository.findByAccountId(UUID.fromString(accountId)).isEmpty());
    }

    @Test
    void subscribeToFreePlanWhilePaidPlanActiveIsRejected() {
        PlanResponse paid = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        PlanResponse free = createPlan("Free", "0", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();
        activate(accountId, paid, UUID.randomUUID());

        assertThrows(BadRequestException.class,
                () -> subscriptionService.subscribe(accountId, new SubscribeRequest(free.getId())));
    }

    @Test
    void activateCreatesNewSubscription() {
        PlanResponse plan = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();

        SubscriptionResponse sub = activate(accountId, plan, UUID.randomUUID());

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertFalse(sub.isCancelAtPeriodEnd());
        assertCloseTo(LocalDateTime.now(), sub.getCurrentPeriodStart());
        assertCloseTo(LocalDateTime.now().plusMonths(1), sub.getCurrentPeriodEnd());
    }

    @Test
    void activateSamePlanWhileActiveExtendsFromPeriodEnd() {
        PlanResponse plan = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();
        SubscriptionResponse first = activate(accountId, plan, UUID.randomUUID());
        subscriptionService.cancel(accountId);

        SubscriptionResponse renewed = activate(accountId, plan, UUID.randomUUID());

        assertEquals(first.getId(), renewed.getId());
        assertEquals(first.getCurrentPeriodStart(), renewed.getCurrentPeriodStart());
        assertEquals(first.getCurrentPeriodEnd().plusMonths(1), renewed.getCurrentPeriodEnd());
        assertFalse(renewed.isCancelAtPeriodEnd(), "a renewal payment clears a pending cancellation");
    }

    @Test
    void activateDifferentPlanStartsNewPeriodNow() {
        PlanResponse standard = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        PlanResponse yearly = createPlan("Premium Yearly", "9999.00", BillingInterval.YEARLY);
        String accountId = UUID.randomUUID().toString();
        activate(accountId, standard, UUID.randomUUID());

        SubscriptionResponse switched = activate(accountId, yearly, UUID.randomUUID());

        assertEquals(yearly.getId(), switched.getPlan().getId());
        assertCloseTo(LocalDateTime.now(), switched.getCurrentPeriodStart());
        assertCloseTo(LocalDateTime.now().plusYears(1), switched.getCurrentPeriodEnd());
    }

    @Test
    void activateAfterExpiryStartsNewPeriodNow() {
        PlanResponse plan = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();
        activate(accountId, plan, UUID.randomUUID());
        Subscription entity = subscriptionRepository.findByAccountId(UUID.fromString(accountId)).orElseThrow();
        entity.setCurrentPeriodStart(LocalDateTime.now().minusMonths(2));
        entity.setCurrentPeriodEnd(LocalDateTime.now().minusMonths(1));
        subscriptionRepository.saveAndFlush(entity);

        SubscriptionResponse renewed = activate(accountId, plan, UUID.randomUUID());

        assertEquals(SubscriptionStatus.ACTIVE, renewed.getStatus());
        assertCloseTo(LocalDateTime.now().plusMonths(1), renewed.getCurrentPeriodEnd());
    }

    @Test
    void activateIsIdempotentPerPaymentTransaction() {
        PlanResponse plan = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();
        UUID paymentId = UUID.randomUUID();

        SubscriptionResponse first = activate(accountId, plan, paymentId);
        SubscriptionResponse repeat = activate(accountId, plan, paymentId);

        assertEquals(first.getId(), repeat.getId());
        assertEquals(first.getCurrentPeriodEnd(), repeat.getCurrentPeriodEnd());
        assertEquals(paymentId,
                subscriptionRepository.findByAccountId(UUID.fromString(accountId)).orElseThrow().getLastPaymentTransactionId());
    }

    @Test
    void activateUnknownPlanIs404() {
        assertThrows(ResourceNotFoundException.class, () -> subscriptionService.activate(new ActivateSubscriptionRequest(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString())));
    }

    @Test
    void effectiveStatusComputation() {
        LocalDateTime now = LocalDateTime.now();
        Subscription sub = new Subscription();
        sub.setStatus(SubscriptionStatus.ACTIVE);

        sub.setCurrentPeriodEnd(now.plusDays(1));
        assertEquals(SubscriptionStatus.ACTIVE, SubscriptionService.effectiveStatus(sub, now));

        sub.setCurrentPeriodEnd(now.minusSeconds(1));
        assertEquals(SubscriptionStatus.EXPIRED, SubscriptionService.effectiveStatus(sub, now));

        sub.setCancelAtPeriodEnd(true);
        assertEquals(SubscriptionStatus.CANCELLED, SubscriptionService.effectiveStatus(sub, now));

        sub.setStatus(SubscriptionStatus.SUSPENDED);
        assertEquals(SubscriptionStatus.SUSPENDED, SubscriptionService.effectiveStatus(sub, now));
    }

    @Test
    void readingAnOverdueSubscriptionPersistsExpiry() {
        PlanResponse plan = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();
        activate(accountId, plan, UUID.randomUUID());
        Subscription entity = subscriptionRepository.findByAccountId(UUID.fromString(accountId)).orElseThrow();
        entity.setCurrentPeriodEnd(LocalDateTime.now().minusMinutes(1));
        subscriptionRepository.saveAndFlush(entity);

        assertEquals(SubscriptionStatus.EXPIRED, subscriptionService.getAccountSubscription(accountId).getStatus());
        assertEquals("EXPIRED", subscriptionService.getEntitlements(accountId).getStatus());
        assertEquals(SubscriptionStatus.EXPIRED,
                subscriptionRepository.findByAccountId(UUID.fromString(accountId)).orElseThrow().getStatus());
    }

    @Test
    void scheduledExpiryMarksCancelledOrExpired() {
        PlanResponse plan = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String expiring = UUID.randomUUID().toString();
        String cancelling = UUID.randomUUID().toString();
        activate(expiring, plan, UUID.randomUUID());
        activate(cancelling, plan, UUID.randomUUID());
        subscriptionService.cancel(cancelling);
        for (String acc : List.of(expiring, cancelling)) {
            Subscription entity = subscriptionRepository.findByAccountId(UUID.fromString(acc)).orElseThrow();
            entity.setCurrentPeriodEnd(LocalDateTime.now().minusMinutes(5));
            subscriptionRepository.saveAndFlush(entity);
        }

        assertEquals(2, subscriptionService.expireOverdueSubscriptions());

        assertEquals(SubscriptionStatus.EXPIRED,
                subscriptionRepository.findByAccountId(UUID.fromString(expiring)).orElseThrow().getStatus());
        assertEquals(SubscriptionStatus.CANCELLED,
                subscriptionRepository.findByAccountId(UUID.fromString(cancelling)).orElseThrow().getStatus());
    }

    @Test
    void cancelAndResume() {
        PlanResponse plan = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();
        activate(accountId, plan, UUID.randomUUID());

        SubscriptionResponse cancelled = subscriptionService.cancel(accountId);
        assertTrue(cancelled.isCancelAtPeriodEnd());
        assertEquals(SubscriptionStatus.ACTIVE, cancelled.getStatus(), "access continues until period end");

        SubscriptionResponse resumed = subscriptionService.resume(accountId);
        assertFalse(resumed.isCancelAtPeriodEnd());
        assertEquals(SubscriptionStatus.ACTIVE, resumed.getStatus());
    }

    @Test
    void resumeAfterPeriodEndedIsRejected() {
        PlanResponse plan = createPlan("Standard", "599.00", BillingInterval.MONTHLY);
        String accountId = UUID.randomUUID().toString();
        activate(accountId, plan, UUID.randomUUID());
        subscriptionService.cancel(accountId);
        Subscription entity = subscriptionRepository.findByAccountId(UUID.fromString(accountId)).orElseThrow();
        entity.setCurrentPeriodEnd(LocalDateTime.now().minusMinutes(1));
        subscriptionRepository.saveAndFlush(entity);

        assertThrows(BadRequestException.class, () -> subscriptionService.resume(accountId));
        assertEquals(SubscriptionStatus.CANCELLED, subscriptionService.getAccountSubscription(accountId).getStatus());
    }

    @Test
    void missingSubscriptionIs404() {
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> subscriptionService.getAccountSubscription(UUID.randomUUID().toString()));
        assertEquals("No subscription found", ex.getMessage());
        assertThrows(ResourceNotFoundException.class, () -> subscriptionService.cancel(UUID.randomUUID().toString()));
    }
}
