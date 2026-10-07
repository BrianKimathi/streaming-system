package com.streamx.billing.provider;

import com.streamx.billing.domain.PaymentTransaction;

import java.math.BigDecimal;

public interface PaymentProvider {
    PaymentTransaction processPayment(BigDecimal amount, String currency, String accountId, String paymentMethod);
}
