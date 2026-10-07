package com.streamx.billing.controller;

import com.streamx.billing.dto.PaymentTransactionResponse;
import com.streamx.billing.dto.ProcessPaymentRequest;
import com.streamx.billing.service.BillingService;
import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/pay")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> processPayment(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @Valid @RequestBody ProcessPaymentRequest request) {
        PaymentTransactionResponse response = billingService.processPayment(accountId, request);
        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", response));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PaymentTransactionResponse>>> getBillingHistory(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId) {
        List<PaymentTransactionResponse> response = billingService.getAccountBillingHistory(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refund/{transactionId}")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> refundTransaction(
            @PathVariable("transactionId") String transactionId) {
        PaymentTransactionResponse response = billingService.refundTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success("Refund processed successfully", response));
    }
}
