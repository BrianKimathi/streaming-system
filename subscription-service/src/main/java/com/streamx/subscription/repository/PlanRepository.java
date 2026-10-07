package com.streamx.subscription.repository;

import com.streamx.subscription.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findByActiveTrue();
    Optional<Plan> findByNameAndVersion(String name, int version);
    Optional<Plan> findFirstByNameOrderByVersionDesc(String name);
}
