package com.streamx.billing.controller;

import com.streamx.billing.dto.AdminPaymentTransactionResponse;
import com.streamx.billing.service.BillingAdminService;
import com.streamx.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/admin")
public class BillingAdminController {

    private final BillingAdminService billingAdminService;

    public BillingAdminController(BillingAdminService billingAdminService) {
        this.billingAdminService = billingAdminService;
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<AdminPaymentTransactionResponse>>> listTransactions(
            @RequestParam(value = "accountId", required = false) String accountId) {
        return ResponseEntity.ok(ApiResponse.success(billingAdminService.listTransactions(accountId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(billingAdminService.getStats()));
    }
}
