package com.streamx.billing.service;

import com.streamx.billing.domain.PaymentStatus;
import com.streamx.billing.domain.PaymentTransaction;
import com.streamx.billing.dto.AdminPaymentTransactionResponse;
import com.streamx.billing.repository.PaymentTransactionRepository;
import com.streamx.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class BillingAdminService {

    private final PaymentTransactionRepository transactionRepository;
    private final BillingService billingService;

    public BillingAdminService(PaymentTransactionRepository transactionRepository, BillingService billingService) {
        this.transactionRepository = transactionRepository;
        this.billingService = billingService;
    }

    @Transactional(readOnly = true)
    public List<AdminPaymentTransactionResponse> listTransactions(String accountId) {
        List<PaymentTransaction> transactions;
        if (accountId == null || accountId.isBlank()) {
            transactions = transactionRepository.findAllByOrderByCreatedAtDesc();
        } else {
            UUID parsed;
            try {
                parsed = UUID.fromString(accountId.trim());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid account ID");
            }
            transactions = transactionRepository.findByAccountIdOrderByCreatedAtDesc(parsed);
        }
        return transactions.stream().map(billingService::mapToAdminResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        List<PaymentTransaction> transactions = transactionRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate windowStart = today.minusDays(13);
        LocalDate last30Start = today.minusDays(29);

        Map<String, BigDecimal> revenueByCurrency = new TreeMap<>();
        Map<String, BigDecimal> refundedByCurrency = new TreeMap<>();
        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            countByStatus.put(status.name(), 0L);
        }
        Map<LocalDate, BigDecimal> dailyRevenue = new TreeMap<>();
        for (int i = 13; i >= 0; i--) {
            dailyRevenue.put(today.minusDays(i), BigDecimal.ZERO);
        }
        BigDecimal revenueLast30Days = BigDecimal.ZERO;
        Set<UUID> payingAccounts = new HashSet<>();

        for (PaymentTransaction txn : transactions) {
            countByStatus.merge(txn.getStatus().name(), 1L, Long::sum);
            BigDecimal amount = txn.getAmount() != null ? txn.getAmount() : BigDecimal.ZERO;
            if (txn.getStatus() == PaymentStatus.COMPLETED) {
                revenueByCurrency.merge(txn.getCurrency(), amount, BigDecimal::add);
                payingAccounts.add(txn.getAccountId());
                if (txn.getCreatedAt() != null) {
                    LocalDate day = txn.getCreatedAt().toLocalDate();
                    if (!day.isBefore(windowStart)) {
                        dailyRevenue.merge(day, amount, BigDecimal::add);
                    }
                    if (!day.isBefore(last30Start)) {
                        revenueLast30Days = revenueLast30Days.add(amount);
                    }
                }
            } else if (txn.getStatus() == PaymentStatus.REFUNDED) {
                refundedByCurrency.merge(txn.getCurrency(), amount, BigDecimal::add);
            }
        }

        List<Map<String, Object>> dailyRevenueSeries = new ArrayList<>();
        dailyRevenue.forEach((date, amount) -> dailyRevenueSeries.add(Map.of("date", date.toString(), "amount", amount)));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTransactions", (long) transactions.size());
        stats.put("completedTransactions", countByStatus.get(PaymentStatus.COMPLETED.name()));
        stats.put("failedTransactions", countByStatus.get(PaymentStatus.FAILED.name()));
        stats.put("refundedTransactions", countByStatus.get(PaymentStatus.REFUNDED.name()));
        stats.put("pendingTransactions", countByStatus.get(PaymentStatus.PENDING.name()));
        stats.put("cancelledTransactions", countByStatus.get(PaymentStatus.CANCELLED.name()));
        stats.put("payingAccounts", (long) payingAccounts.size());
        stats.put("revenueByCurrency", revenueByCurrency);
        stats.put("refundedByCurrency", refundedByCurrency);
        stats.put("totalRevenue", revenueByCurrency.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("revenueLast30Days", revenueLast30Days);
        stats.put("primaryCurrency", revenueByCurrency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("KES"));
        stats.put("countByStatus", countByStatus);
        stats.put("dailyRevenue", dailyRevenueSeries);
        return stats;
    }
}
