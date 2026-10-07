package com.streamx.auth.service;

import com.streamx.auth.domain.Account;
import com.streamx.auth.dto.AccountSummaryResponse;
import com.streamx.auth.repository.AccountRepository;
import com.streamx.auth.repository.RefreshTokenRepository;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AccountAdminService {

    private static final Logger log = LoggerFactory.getLogger(AccountAdminService.class);

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AccountAdminService(AccountRepository accountRepository, RefreshTokenRepository refreshTokenRepository) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public List<AccountSummaryResponse> listAccounts() {
        return accountRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AccountSummaryResponse::from)
                .toList();
    }

    public AccountSummaryResponse getAccount(String accountId) {
        return AccountSummaryResponse.from(findAccount(accountId));
    }

    @Transactional
    public AccountSummaryResponse updateBlockedStatus(String accountId, boolean blocked, String actingAccountId) {
        Account account = findAccount(accountId);
        if (blocked && account.getId().toString().equals(actingAccountId)) {
            throw new BadRequestException("You cannot block your own administrator account");
        }

        account.setStatus(blocked ? Account.AccountStatus.SUSPENDED : Account.AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        if (blocked) {
            refreshTokenRepository.deleteByAccountId(saved.getId());
        }
        log.info("Account {} {} by admin {}", accountId, blocked ? "suspended" : "reactivated", actingAccountId);
        return AccountSummaryResponse.from(saved);
    }

    public Map<String, Object> getStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime windowStart = today.minusDays(13).atStartOfDay();

        Map<LocalDate, Long> signupsByDay = new TreeMap<>();
        for (int i = 13; i >= 0; i--) {
            signupsByDay.put(today.minusDays(i), 0L);
        }
        for (Account account : accountRepository.findByCreatedAtGreaterThanEqual(windowStart)) {
            if (account.getCreatedAt() != null) {
                signupsByDay.merge(account.getCreatedAt().toLocalDate(), 1L, Long::sum);
            }
        }

        List<Map<String, Object>> dailySignups = new ArrayList<>();
        signupsByDay.forEach((date, count) -> dailySignups.add(Map.of("date", date.toString(), "count", count)));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalAccounts", accountRepository.count());
        stats.put("activeAccounts", accountRepository.countByStatus(Account.AccountStatus.ACTIVE));
        stats.put("suspendedAccounts", accountRepository.countByStatus(Account.AccountStatus.SUSPENDED)
                + accountRepository.countByStatus(Account.AccountStatus.BANNED));
        stats.put("newAccountsToday", accountRepository.countByCreatedAtGreaterThanEqual(startOfToday));
        stats.put("newAccountsLast7Days", accountRepository.countByCreatedAtGreaterThanEqual(today.minusDays(6).atStartOfDay()));
        stats.put("newAccountsLast30Days", accountRepository.countByCreatedAtGreaterThanEqual(today.minusDays(29).atStartOfDay()));
        stats.put("dailySignups", dailySignups);
        return stats;
    }

    private Account findAccount(String accountId) {
        UUID id;
        try {
            id = UUID.fromString(accountId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid account ID");
        }
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
