package com.streamx.subscription.repository;

import com.streamx.subscription.domain.Subscription;
import com.streamx.subscription.domain.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByAccountId(UUID accountId);
    boolean existsByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscription s where s.accountId = :accountId")
    Optional<Subscription> findByAccountIdForUpdate(@Param("accountId") UUID accountId);

    List<Subscription> findAllByOrderByCreatedAtDesc();

    List<Subscription> findByStatus(SubscriptionStatus status);

    List<Subscription> findByStatusAndCurrentPeriodEndBefore(SubscriptionStatus status, LocalDateTime cutoff);

    long countByStatus(SubscriptionStatus status);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);
}
