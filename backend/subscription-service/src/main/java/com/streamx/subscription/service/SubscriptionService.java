package com.streamx.subscription.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.subscription.domain.BillingInterval;
import com.streamx.subscription.domain.Plan;
import com.streamx.subscription.domain.Subscription;
import com.streamx.subscription.domain.SubscriptionStatus;
import com.streamx.subscription.dto.*;
import com.streamx.subscription.repository.PlanRepository;
import com.streamx.subscription.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final DateTimeFormatter HUMAN_DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(PlanRepository planRepository, SubscriptionRepository subscriptionRepository) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    // --- Admin Plan Management & Versioning ---
    @Transactional
    public PlanResponse createPlan(CreatePlanRequest request) {
        Optional<Plan> latestVersionOpt = planRepository.findFirstByNameOrderByVersionDesc(request.getName());

        int nextVersion = 1;
        if (latestVersionOpt.isPresent()) {
            Plan previous = latestVersionOpt.get();
            nextVersion = previous.getVersion() + 1;
            previous.setActive(false);
            planRepository.save(previous);
            log.info("Deactivating previous version v{} of plan '{}' for new subscribers", previous.getVersion(), request.getName());
        }

        Plan plan = new Plan();
        plan.setName(request.getName());
        plan.setDescription(request.getDescription());
        plan.setPrice(request.getPrice());
        plan.setCurrency(request.getCurrency());
        plan.setBillingInterval(request.getBillingInterval());
        plan.setVersion(nextVersion);
        plan.setActive(true);
        plan.setMaxProfiles(request.getMaxProfiles());
        plan.setMaxRegisteredDevices(request.getMaxRegisteredDevices());
        plan.setMaxConcurrentStreams(request.getMaxConcurrentStreams());
        plan.setMaxResolution(request.getMaxResolution());
        plan.setHdrEnabled(request.isHdrEnabled());
        plan.setAudioQuality(request.getAudioQuality());
        plan.setDownloadsEnabled(request.isDownloadsEnabled());
        plan.setMaxDownloadDevices(request.getMaxDownloadDevices());
        plan.setKidsProfilesEnabled(request.isKidsProfilesEnabled());

        Plan saved = planRepository.save(plan);
        log.info("Created plan '{}' version v{}", saved.getName(), saved.getVersion());
        return mapToPlanResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> getActivePlans() {
        return planRepository.findByActiveTrueOrderByPriceAscNameAsc().stream()
                .map(this::mapToPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(String planIdStr) {
        Plan plan = planRepository.findById(parsePlanId(planIdStr))
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        return mapToPlanResponse(plan);
    }

    // --- Subscription Lifecycle ---
    @Transactional
    public SubscriptionResponse subscribe(String accountIdStr, SubscribeRequest request) {
        UUID accountId = parseAccountId(accountIdStr);
        Plan plan = planRepository.findById(parsePlanId(request.getPlanId()))
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (!plan.isActive()) {
            throw new BadRequestException("This plan version is no longer active for new subscriptions");
        }
        if (plan.getPrice() == null || plan.getPrice().compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException("This plan requires payment. Pay with M-Pesa to subscribe.");
        }

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = subscriptionRepository.findByAccountIdForUpdate(accountId).orElse(null);
        if (subscription != null) {
            applyExpiry(subscription, now);
            if (subscription.getStatus() == SubscriptionStatus.ACTIVE
                    && !subscription.getPlanId().equals(plan.getId())
                    && isPaidPlan(subscription.getPlanId())) {
                throw new BadRequestException("Your current paid plan is active until "
                        + subscription.getCurrentPeriodEnd().format(HUMAN_DATE)
                        + ". You can switch to a free plan after it ends.");
            }
        } else {
            subscription = new Subscription();
            subscription.setAccountId(accountId);
        }

        startNewPeriod(subscription, plan, now);
        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Account {} subscribed to free plan '{}' v{}", accountId, plan.getName(), plan.getVersion());
        return mapToSubscriptionResponse(saved, plan);
    }

    @Transactional
    public SubscriptionResponse getAccountSubscription(String accountIdStr) {
        Subscription sub = loadWithExpiry(parseAccountId(accountIdStr));
        return mapToSubscriptionResponse(sub, planFor(sub));
    }

    @Transactional
    public EntitlementsResponse getEntitlements(String accountIdStr) {
        Subscription sub = loadWithExpiry(parseAccountId(accountIdStr));
        return mapToEntitlementsResponse(sub, planFor(sub));
    }

    @Transactional
    public SubscriptionResponse cancel(String accountIdStr) {
        Subscription sub = loadWithExpiry(parseAccountId(accountIdStr));
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BadRequestException("Only an active subscription can be cancelled");
        }
        sub.setCancelAtPeriodEnd(true);
        Subscription saved = subscriptionRepository.save(sub);
        log.info("Subscription {} set to cancel at period end ({})", saved.getId(), saved.getCurrentPeriodEnd());
        return mapToSubscriptionResponse(saved, planFor(saved));
    }

    @Transactional
    public SubscriptionResponse resume(String accountIdStr) {
        Subscription sub = loadWithExpiry(parseAccountId(accountIdStr));
        if (sub.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BadRequestException("This subscription has already ended. Choose a plan to subscribe again.");
        }
        sub.setCancelAtPeriodEnd(false);
        Subscription saved = subscriptionRepository.save(sub);
        log.info("Subscription {} resumed", saved.getId());
        return mapToSubscriptionResponse(saved, planFor(saved));
    }

    /**
     * Activates or renews a paid subscription after billing confirmed the payment.
     * Idempotent per payment transaction id.
     */
    @Transactional
    public SubscriptionResponse activate(ActivateSubscriptionRequest request) {
        UUID accountId = parseUuid(request.getAccountId(), "Invalid account ID");
        UUID planId = parsePlanId(request.getPlanId());
        UUID paymentTransactionId = parseUuid(request.getPaymentTransactionId(), "Invalid payment transaction ID");

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = subscriptionRepository.findByAccountIdForUpdate(accountId).orElse(null);

        if (subscription != null && paymentTransactionId.equals(subscription.getLastPaymentTransactionId())) {
            log.info("Payment {} already applied to subscription {}; not extending again",
                    paymentTransactionId, subscription.getId());
            applyExpiry(subscription, now);
            return mapToSubscriptionResponse(subscription, planFor(subscription));
        }

        if (subscription == null) {
            subscription = new Subscription();
            subscription.setAccountId(accountId);
            startNewPeriod(subscription, plan, now);
        } else {
            applyExpiry(subscription, now);
            boolean renewal = subscription.getStatus() == SubscriptionStatus.ACTIVE
                    && plan.getId().equals(subscription.getPlanId())
                    && subscription.getCurrentPeriodEnd() != null
                    && subscription.getCurrentPeriodEnd().isAfter(now);
            if (renewal) {
                subscription.setCurrentPeriodEnd(addInterval(subscription.getCurrentPeriodEnd(), plan.getBillingInterval()));
                subscription.setCancelAtPeriodEnd(false);
            } else {
                startNewPeriod(subscription, plan, now);
            }
        }
        subscription.setLastPaymentTransactionId(paymentTransactionId);

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("Activated plan '{}' v{} for account {} until {} (payment {})",
                plan.getName(), plan.getVersion(), accountId, saved.getCurrentPeriodEnd(), paymentTransactionId);
        return mapToSubscriptionResponse(saved, plan);
    }

    /**
     * Persists EXPIRED/CANCELLED for every ACTIVE subscription whose period has ended.
     */
    @Transactional
    public int expireOverdueSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> overdue = subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, now);
        for (Subscription sub : overdue) {
            applyExpiry(sub, now);
        }
        if (!overdue.isEmpty()) {
            subscriptionRepository.saveAll(overdue);
            log.info("Expired {} subscription(s) whose period ended", overdue.size());
        }
        return overdue.size();
    }

    static SubscriptionStatus effectiveStatus(Subscription sub, LocalDateTime now) {
        if (sub.getStatus() == SubscriptionStatus.ACTIVE
                && sub.getCurrentPeriodEnd() != null
                && !sub.getCurrentPeriodEnd().isAfter(now)) {
            return sub.isCancelAtPeriodEnd() ? SubscriptionStatus.CANCELLED : SubscriptionStatus.EXPIRED;
        }
        return sub.getStatus();
    }

    static boolean applyExpiry(Subscription sub, LocalDateTime now) {
        SubscriptionStatus effective = effectiveStatus(sub, now);
        if (effective != sub.getStatus()) {
            sub.setStatus(effective);
            return true;
        }
        return false;
    }

    static LocalDateTime addInterval(LocalDateTime from, BillingInterval interval) {
        return interval == BillingInterval.YEARLY ? from.plusYears(1) : from.plusMonths(1);
    }

    private void startNewPeriod(Subscription subscription, Plan plan, LocalDateTime now) {
        subscription.setPlanId(plan.getId());
        subscription.setPlanVersion(plan.getVersion());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(addInterval(now, plan.getBillingInterval()));
        subscription.setCancelAtPeriodEnd(false);
    }

    private Subscription loadWithExpiry(UUID accountId) {
        Subscription sub = subscriptionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No subscription found"));
        if (applyExpiry(sub, LocalDateTime.now())) {
            sub = subscriptionRepository.save(sub);
        }
        return sub;
    }

    private boolean isPaidPlan(UUID planId) {
        return planRepository.findById(planId)
                .map(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .orElse(false);
    }

    private Plan planFor(Subscription sub) {
        return planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found for subscription"));
    }

    private static UUID parseAccountId(String accountIdStr) {
        try {
            return UUID.fromString(accountIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private static UUID parsePlanId(String planIdStr) {
        try {
            return UUID.fromString(planIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResourceNotFoundException("Plan not found");
        }
    }

    private static UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException(message);
        }
    }

    PlanResponse mapToPlanResponse(Plan plan) {
        return new PlanResponse(
                plan.getId().toString(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getCurrency(),
                plan.getBillingInterval(),
                plan.getVersion(),
                plan.isActive(),
                plan.getMaxProfiles(),
                plan.getMaxRegisteredDevices(),
                plan.getMaxConcurrentStreams(),
                plan.getMaxResolution(),
                plan.isHdrEnabled(),
                plan.getAudioQuality(),
                plan.isDownloadsEnabled(),
                plan.getMaxDownloadDevices(),
                plan.isKidsProfilesEnabled()
        );
    }

    private EntitlementsResponse mapToEntitlementsResponse(Subscription sub, Plan plan) {
        return new EntitlementsResponse(
                sub.getId().toString(),
                effectiveStatus(sub, LocalDateTime.now()).name(),
                plan.getMaxProfiles(),
                plan.getMaxRegisteredDevices(),
                plan.getMaxConcurrentStreams(),
                plan.getMaxResolution(),
                plan.isHdrEnabled(),
                plan.getAudioQuality(),
                plan.isDownloadsEnabled(),
                plan.getMaxDownloadDevices(),
                plan.isKidsProfilesEnabled()
        );
    }

    SubscriptionResponse mapToSubscriptionResponse(Subscription sub, Plan plan) {
        return new SubscriptionResponse(
                sub.getId().toString(),
                sub.getAccountId().toString(),
                mapToPlanResponse(plan),
                effectiveStatus(sub, LocalDateTime.now()),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.isCancelAtPeriodEnd(),
                mapToEntitlementsResponse(sub, plan)
        );
    }
}
