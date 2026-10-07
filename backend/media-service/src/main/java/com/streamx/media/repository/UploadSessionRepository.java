package com.streamx.media.repository;

import com.streamx.media.domain.UploadSession;
import com.streamx.media.domain.UploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    List<UploadSession> findByStatusAndUpdatedAtBefore(UploadSessionStatus status, LocalDateTime cutoff);

    long countByStatus(UploadSessionStatus status);

    @Transactional
    @Modifying
    @Query("update UploadSession s set s.updatedAt = :now where s.id = :id")
    int touch(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
