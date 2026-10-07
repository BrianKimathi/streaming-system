package com.streamx.subscription.repository;

import com.streamx.subscription.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByAccountId(UUID accountId);
    boolean existsByAccountId(UUID accountId);
}
