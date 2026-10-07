package com.streamx.billing.service;

import com.streamx.billing.domain.PaymentStatus;
import com.streamx.billing.dto.PaymentTransactionResponse;
import com.streamx.billing.dto.ProcessPaymentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillingServiceTest {

    @Autowired
    private BillingService billingService;

    @Test
    void testProcessPaymentAndGetHistory() {
        String accountId = UUID.randomUUID().toString();
        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setAmount(new BigDecimal("999.00"));
        request.setCurrency("KES");
        request.setPaymentMethod("CARD");

        PaymentTransactionResponse txnRes = billingService.processPayment(accountId, request);

        assertNotNull(txnRes.getId());
        assertEquals(PaymentStatus.COMPLETED, txnRes.getStatus());
        assertEquals(new BigDecimal("999.00"), txnRes.getAmount());
        assertNotNull(txnRes.getExternalTransactionId());

        List<PaymentTransactionResponse> history = billingService.getAccountBillingHistory(accountId);
        assertEquals(1, history.size());
        assertEquals(txnRes.getId(), history.get(0).getId());
    }

    @Test
    void testRefundTransaction() {
        String accountId = UUID.randomUUID().toString();
        ProcessPaymentRequest request = new ProcessPaymentRequest(new BigDecimal("599.00"), "KES", null, "MPESA");

        PaymentTransactionResponse txnRes = billingService.processPayment(accountId, request);
        PaymentTransactionResponse refundedRes = billingService.refundTransaction(txnRes.getId());

        assertEquals(PaymentStatus.REFUNDED, refundedRes.getStatus());
    }
}
