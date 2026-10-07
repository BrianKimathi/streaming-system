package com.streamx.subscription.service;

import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.subscription.domain.BillingInterval;
import com.streamx.subscription.domain.Plan;
import com.streamx.subscription.domain.Subscription;
import com.streamx.subscription.domain.SubscriptionStatus;
import com.streamx.subscription.dto.PlanResponse;
import com.streamx.subscription.dto.SubscriptionResponse;
import com.streamx.subscription.repository.PlanRepository;
import com.streamx.subscription.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubscriptionAdminService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final SubscriptionService subscriptionService;

    public SubscriptionAdminService(SubscriptionRepository subscriptionRepository,
                                    PlanRepository planRepository,
                                    SubscriptionService subscriptionService) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.subscriptionService = subscriptionService;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listSubscriptions() {
        Map<UUID, Plan> plans = plansById();
        return subscriptionRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(sub -> plans.containsKey(sub.getPlanId()))
                .map(sub -> subscriptionService.mapToSubscriptionResponse(sub, plans.get(sub.getPlanId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getForAccount(UUID accountId) {
        Subscription sub = subscriptionRepository.findByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account has no subscription"));
        Plan plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found for subscription"));
        return subscriptionService.mapToSubscriptionResponse(sub, plan);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listAllPlans() {
        return planRepository.findAll().stream()
                .sorted(Comparator.comparing(Plan::getName).thenComparing(Plan::getVersion, Comparator.reverseOrder()))
                .map(subscriptionService::mapToPlanResponse)
                .toList();
    }

    @Transactional
    public PlanResponse setPlanActive(UUID planId, boolean active) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        plan.setActive(active);
        return subscriptionService.mapToPlanResponse(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<UUID, Plan> plans = plansById();
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> active = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE).stream()
                .filter(sub -> SubscriptionService.effectiveStatus(sub, now) == SubscriptionStatus.ACTIVE)
                .toList();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            byStatus.put(status.name(), subscriptionRepository.countByStatus(status));
        }

        Map<UUID, Long> activeCountByPlan = active.stream()
                .collect(Collectors.groupingBy(Subscription::getPlanId, Collectors.counting()));
        List<Map<String, Object>> activeByPlan = new ArrayList<>();
        activeCountByPlan.forEach((planId, count) -> {
            Plan plan = plans.get(planId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("planId", planId.toString());
            row.put("planName", plan != null ? plan.getName() : "Unknown plan");
            row.put("planVersion", plan != null ? plan.getVersion() : null);
            row.put("price", plan != null ? plan.getPrice() : null);
            row.put("currency", plan != null ? plan.getCurrency() : null);
            row.put("activeSubscriptions", count);
            activeByPlan.add(row);
        });
        activeByPlan.sort(Comparator.comparing(r -> -((Long) r.get("activeSubscriptions"))));

        Map<String, BigDecimal> mrrByCurrency = new TreeMap<>();
        for (Subscription sub : active) {
            Plan plan = plans.get(sub.getPlanId());
            if (plan == null || plan.getPrice() == null) {
                continue;
            }
            BigDecimal monthly = plan.getBillingInterval() == BillingInterval.YEARLY
                    ? plan.getPrice().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                    : plan.getPrice();
            mrrByCurrency.merge(plan.getCurrency(), monthly, BigDecimal::add);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSubscriptions", subscriptionRepository.count());
        stats.put("activeSubscriptions", active.size());
        stats.put("newLast30Days", subscriptionRepository.countByCreatedAtGreaterThanEqual(LocalDateTime.now().minusDays(30)));
        stats.put("countByStatus", byStatus);
        stats.put("activeByPlan", activeByPlan);
        stats.put("monthlyRecurringRevenueByCurrency", mrrByCurrency);
        stats.put("activePlans", plans.values().stream().filter(Plan::isActive).count());
        return stats;
    }

    private Map<UUID, Plan> plansById() {
        return planRepository.findAll().stream().collect(Collectors.toMap(Plan::getId, Function.identity()));
    }
}
