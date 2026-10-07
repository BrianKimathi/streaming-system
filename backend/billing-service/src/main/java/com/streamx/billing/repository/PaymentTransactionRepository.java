package com.streamx.billing.repository;

import com.streamx.billing.domain.PaymentStatus;
import com.streamx.billing.domain.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    List<PaymentTransaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
    List<PaymentTransaction> findAllByOrderByCreatedAtDesc();

    Optional<PaymentTransaction> findByIdAndAccountId(UUID id, UUID accountId);

    Optional<PaymentTransaction> findFirstByCheckoutRequestId(String checkoutRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PaymentTransaction t where t.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByAccountIdAndStatusInAndCreatedAtAfter(UUID accountId, Collection<PaymentStatus> statuses,
                                                          LocalDateTime createdAfter);

    List<PaymentTransaction> findByStatusInAndCreatedAtBefore(Collection<PaymentStatus> statuses, LocalDateTime createdBefore);

    List<PaymentTransaction> findByStatusAndSubscriptionIdIsNullAndPlanIdIsNotNullAndUpdatedAtAfter(
            PaymentStatus status, LocalDateTime updatedAfter);
}
