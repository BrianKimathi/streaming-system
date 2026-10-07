package com.streamx.admin.repository;

import com.streamx.admin.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {
    List<SupportTicket> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
    List<SupportTicket> findByStatus(String status);
}
