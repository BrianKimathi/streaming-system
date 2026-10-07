package com.streamx.admin.repository;

import com.streamx.admin.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findTop50ByOrderByTimestampDesc();
    List<AuditLog> findByTargetIdOrderByTimestampDesc(String targetId);
    List<AuditLog> findByAction(String action);
}
