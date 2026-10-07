package com.streamx.auth.config;

import com.streamx.auth.domain.Account;
import com.streamx.auth.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Ensures the platform owner account configured via ADMIN_EMAIL / ADMIN_PASSWORD exists with SUPER_ADMIN rights.
 * An existing account's password is never overwritten, so changing ADMIN_PASSWORD later has no effect.
 */
@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);
    private static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public AdminAccountBootstrap(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("ADMIN_EMAIL is not configured; no administrator account will be provisioned");
            return;
        }
        String email = adminEmail.trim().toLowerCase();

        accountRepository.findByEmailIgnoreCase(email).ifPresentOrElse(account -> {
            if (!account.getRoles().contains(SUPER_ADMIN_ROLE)) {
                account.getRoles().add(SUPER_ADMIN_ROLE);
                accountRepository.save(account);
                log.info("Granted {} to existing account {}", SUPER_ADMIN_ROLE, email);
            }
        }, () -> {
            if (adminPassword == null || adminPassword.length() < 12) {
                log.error("ADMIN_PASSWORD must be at least 12 characters; administrator account {} was not created", email);
                return;
            }
            Account account = new Account();
            account.setEmail(email);
            account.setPasswordHash(passwordEncoder.encode(adminPassword));
            account.setStatus(Account.AccountStatus.ACTIVE);
            account.setEmailVerified(true);
            Set<String> roles = new HashSet<>();
            roles.add(SUPER_ADMIN_ROLE);
            account.setRoles(roles);
            accountRepository.save(account);
            log.info("Provisioned administrator account {}", email);
        });
    }
}
