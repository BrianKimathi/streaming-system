package com.streamx.billing.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.billing.dto.AdminPaymentTransactionResponse;
import com.streamx.billing.dto.CheckoutRequest;
import com.streamx.billing.dto.PaymentTransactionResponse;
import com.streamx.billing.service.BillingService;
import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> checkout(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @Valid @RequestBody CheckoutRequest request) {
        PaymentTransactionResponse response = billingService.checkout(accountId, request);
        return ResponseEntity.ok(ApiResponse.success("Check your phone and enter your M-Pesa PIN to complete the payment", response));
    }

    /**
     * Public Daraja STK callback. The token path segment is a shared secret; Daraja expects a 200 acknowledgement.
     */
    @PostMapping("/mpesa/callback/{token}")
    public ResponseEntity<Map<String, Object>> mpesaCallback(
            @PathVariable("token") String token,
            @RequestBody(required = false) JsonNode payload) {
        billingService.handleCallback(token, payload);
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("ResultCode", 0);
        ack.put("ResultDesc", "Accepted");
        return ResponseEntity.ok(ack);
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<PaymentTransactionResponse>> getTransaction(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId,
            @PathVariable("transactionId") String transactionId) {
        return ResponseEntity.ok(ApiResponse.success(billingService.getTransaction(accountId, transactionId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PaymentTransactionResponse>>> getBillingHistory(
            @RequestHeader(SecurityConstants.HEADER_X_ACCOUNT_ID) String accountId) {
        List<PaymentTransactionResponse> response = billingService.getAccountBillingHistory(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refund/{transactionId}")
    public ResponseEntity<ApiResponse<AdminPaymentTransactionResponse>> refundTransaction(
            @PathVariable("transactionId") String transactionId) {
        AdminPaymentTransactionResponse response = billingService.refundTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.success("Transaction marked as refunded. Reverse the payment in the M-Pesa portal.", response));
    }
}
