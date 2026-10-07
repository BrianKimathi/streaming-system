package com.streamx.subscription.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.subscription.dto.ActivateSubscriptionRequest;
import com.streamx.subscription.dto.EntitlementsResponse;
import com.streamx.subscription.dto.PlanResponse;
import com.streamx.subscription.dto.SubscriptionResponse;
import com.streamx.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service endpoints. The gateway returns 404 for any /internal/** path, so these are only reachable
 * on the internal network and take identity as explicit parameters.
 */
@RestController
@RequestMapping("/api/v1/subscriptions/internal")
public class SubscriptionInternalController {

    private final SubscriptionService subscriptionService;

    public SubscriptionInternalController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<PlanResponse>> getPlan(@PathVariable("planId") String planId) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getPlan(planId)));
    }

    @GetMapping("/entitlements/{accountId}")
    public ResponseEntity<ApiResponse<EntitlementsResponse>> getEntitlements(@PathVariable("accountId") String accountId) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getEntitlements(accountId)));
    }

    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> activate(@Valid @RequestBody ActivateSubscriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Subscription activated", subscriptionService.activate(request)));
    }
}
