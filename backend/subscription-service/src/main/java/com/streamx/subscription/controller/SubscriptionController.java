package com.streamx.subscription.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.subscription.dto.*;
import com.streamx.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // --- Admin Plans ---
    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        PlanResponse response = subscriptionService.createPlan(request);
        return ResponseEntity.ok(ApiResponse.success("Plan created/versioned successfully", response));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> getActivePlans() {
        List<PlanResponse> response = subscriptionService.getActivePlans();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // --- Customer Subscriptions ---
    @PostMapping("/subscribe")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @Valid @RequestBody SubscribeRequest request) {
        SubscriptionResponse response = subscriptionService.subscribe(accountId, request);
        return ResponseEntity.ok(ApiResponse.success("Subscribed successfully", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getMySubscription(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId) {
        SubscriptionResponse response = subscriptionService.getAccountSubscription(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/entitlements")
    public ResponseEntity<ApiResponse<EntitlementsResponse>> getEntitlements(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId) {
        EntitlementsResponse response = subscriptionService.getEntitlements(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
