package com.streamx.billing.provider;

import com.streamx.billing.domain.PaymentStatus;
import com.streamx.billing.domain.PaymentTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);

    @Override
    public PaymentTransaction processPayment(BigDecimal amount, String currency, String accountId, String paymentMethod) {
        log.info("[MOCK PAYMENT PROVIDER] Processing payment of {} {} for accountId {}", amount, currency, accountId);

        String txnId = "MOCK_TXN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setAccountId(UUID.fromString(accountId));
        transaction.setAmount(amount);
        transaction.setCurrency(currency != null ? currency : "KES");
        transaction.setPaymentMethod(paymentMethod != null ? paymentMethod : "CARD");
        transaction.setExternalTransactionId(txnId);
        transaction.setStatus(PaymentStatus.COMPLETED);

        return transaction;
    }
}
