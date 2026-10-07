package com.streamx.notification.repository;

import com.streamx.notification.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    List<NotificationLog> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<NotificationLog> findTop500ByOrderByCreatedAtDesc();

    long countByStatus(String status);

    long countByChannel(String channel);

    long countByCreatedAtGreaterThanEqual(Instant since);
}
