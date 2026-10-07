package com.streamx.subscription.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

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
        return planRepository.findByActiveTrue().stream()
                .map(this::mapToPlanResponse)
                .collect(Collectors.toList());
    }

    // --- Subscription Lifecycle ---
    @Transactional
    public SubscriptionResponse subscribe(String accountIdStr, SubscribeRequest request) {
        UUID accountId = UUID.fromString(accountIdStr);
        UUID planId = UUID.fromString(request.getPlanId());

        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (!plan.isActive()) {
            throw new BadRequestException("This plan version is no longer active for new subscriptions");
        }

        Optional<Subscription> existingOpt = subscriptionRepository.findByAccountId(accountId);
        Subscription subscription;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodEnd = plan.getBillingInterval() == com.streamx.subscription.domain.BillingInterval.YEARLY
                ? now.plusYears(1)
                : now.plusMonths(1);

        if (existingOpt.isPresent()) {
            subscription = existingOpt.get();
            subscription.setPlanId(plan.getId());
            subscription.setPlanVersion(plan.getVersion());
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(periodEnd);
            subscription.setCancelAtPeriodEnd(false);
        } else {
            subscription = new Subscription();
            subscription.setAccountId(accountId);
            subscription.setPlanId(plan.getId());
            subscription.setPlanVersion(plan.getVersion());
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(periodEnd);
            subscription.setCancelAtPeriodEnd(false);
        }

        Subscription saved = subscriptionRepository.save(subscription);
        return mapToSubscriptionResponse(saved, plan);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getAccountSubscription(String accountIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        Subscription sub = subscriptionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for account"));

        Plan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found for subscription"));

        return mapToSubscriptionResponse(sub, plan);
    }

    @Transactional(readOnly = true)
    public EntitlementsResponse getEntitlements(String accountIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        Subscription sub = subscriptionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("No subscription found for account"));

        Plan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found for subscription"));

        return mapToEntitlementsResponse(sub, plan);
    }

    private PlanResponse mapToPlanResponse(Plan plan) {
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
                sub.getStatus().name(),
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

    private SubscriptionResponse mapToSubscriptionResponse(Subscription sub, Plan plan) {
        return new SubscriptionResponse(
                sub.getId().toString(),
                sub.getAccountId().toString(),
                mapToPlanResponse(plan),
                sub.getStatus(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.isCancelAtPeriodEnd(),
                mapToEntitlementsResponse(sub, plan)
        );
    }
}
