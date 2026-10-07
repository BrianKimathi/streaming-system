package com.streamx.user.repository;

import com.streamx.user.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    List<Profile> findByAccountId(UUID accountId);
    List<Profile> findByAccountIdOrderByCreatedAtAsc(UUID accountId);
    long countByAccountId(UUID accountId);
    Optional<Profile> findByIdAndAccountId(UUID id, UUID accountId);
}
