package com.streamx.admin.repository;

import com.streamx.admin.domain.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByStatusOrderByCreatedAtDesc(String status);
    List<Incident> findAllByOrderByCreatedAtDesc();
}
