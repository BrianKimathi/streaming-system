package com.streamx.auth.repository;

import com.streamx.auth.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);
    Optional<Account> findByEmailIgnoreCase(String email);
    Optional<Account> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    List<Account> findAllByOrderByCreatedAtDesc();
    long countByStatus(Account.AccountStatus status);
    long countByCreatedAtGreaterThanEqual(LocalDateTime since);
    List<Account> findByCreatedAtGreaterThanEqual(LocalDateTime since);
}
