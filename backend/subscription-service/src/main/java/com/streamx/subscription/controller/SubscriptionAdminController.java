package com.streamx.subscription.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.subscription.dto.PlanResponse;
import com.streamx.subscription.dto.SubscriptionResponse;
import com.streamx.subscription.service.SubscriptionAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions/admin")
public class SubscriptionAdminController {

    private final SubscriptionAdminService subscriptionAdminService;

    public SubscriptionAdminController(SubscriptionAdminService subscriptionAdminService) {
        this.subscriptionAdminService = subscriptionAdminService;
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionAdminService.listSubscriptions()));
    }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> forAccount(@PathVariable("accountId") UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionAdminService.getForAccount(accountId)));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> plans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionAdminService.listAllPlans()));
    }

    @PatchMapping("/plans/{id}/active")
    public ResponseEntity<ApiResponse<PlanResponse>> setPlanActive(@PathVariable("id") UUID id,
                                                                   @RequestParam("active") boolean active) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionAdminService.setPlanActive(id, active)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionAdminService.getStats()));
    }
}
