package com.streamx.billing.service;

import com.streamx.billing.domain.PaymentStatus;
import com.streamx.billing.domain.PaymentTransaction;
import com.streamx.billing.dto.PaymentTransactionResponse;
import com.streamx.billing.dto.ProcessPaymentRequest;
import com.streamx.billing.provider.PaymentProvider;
import com.streamx.billing.repository.PaymentTransactionRepository;
import com.streamx.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentProvider paymentProvider;

    public BillingService(PaymentTransactionRepository transactionRepository, PaymentProvider paymentProvider) {
        this.transactionRepository = transactionRepository;
        this.paymentProvider = paymentProvider;
    }

    @Transactional
    public PaymentTransactionResponse processPayment(String accountIdStr, ProcessPaymentRequest request) {
        PaymentTransaction txn = paymentProvider.processPayment(
                request.getAmount(),
                request.getCurrency(),
                accountIdStr,
                request.getPaymentMethod()
        );

        if (request.getSubscriptionId() != null && !request.getSubscriptionId().isBlank()) {
            txn.setSubscriptionId(UUID.fromString(request.getSubscriptionId()));
        }

        PaymentTransaction saved = transactionRepository.save(txn);
        log.info("Processed transaction {} for account {}", saved.getId(), accountIdStr);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getAccountBillingHistory(String accountIdStr) {
        UUID accountId = UUID.fromString(accountIdStr);
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentTransactionResponse refundTransaction(String transactionIdStr) {
        UUID transactionId = UUID.fromString(transactionIdStr);
        PaymentTransaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        txn.setStatus(PaymentStatus.REFUNDED);
        PaymentTransaction updated = transactionRepository.save(txn);
        log.info("Refunded transaction {}", transactionIdStr);
        return mapToResponse(updated);
    }

    private PaymentTransactionResponse mapToResponse(PaymentTransaction txn) {
        return new PaymentTransactionResponse(
                txn.getId().toString(),
                txn.getAccountId().toString(),
                txn.getSubscriptionId() != null ? txn.getSubscriptionId().toString() : null,
                txn.getAmount(),
                txn.getCurrency(),
                txn.getStatus(),
                txn.getPaymentMethod(),
                txn.getExternalTransactionId(),
                txn.getCreatedAt()
        );
    }
}
