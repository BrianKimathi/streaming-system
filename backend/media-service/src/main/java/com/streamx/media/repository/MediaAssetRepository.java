package com.streamx.media.repository;

import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    Optional<MediaAsset> findByContentId(UUID contentId);

    List<MediaAsset> findAllByOrderByCreatedAtDesc();

    List<MediaAsset> findByStatusIn(Collection<MediaProcessingStatus> statuses);

    long countByStatus(MediaProcessingStatus status);

    @Query("select coalesce(sum(m.durationSeconds), 0) from MediaAsset m where m.status = com.streamx.media.domain.MediaProcessingStatus.COMPLETED")
    long sumCompletedDurationSeconds();

    @Query("select coalesce(sum(m.fileSizeBytes), 0) from MediaAsset m")
    long sumFileSizeBytes();

    @Transactional
    @Modifying
    @Query("update MediaAsset m set m.progressPercent = :percent where m.id = :id and m.status = com.streamx.media.domain.MediaProcessingStatus.PROCESSING")
    int updateProgress(@Param("id") UUID id, @Param("percent") Integer percent);

    @Transactional
    @Modifying
    @Query("update MediaAsset m set m.pendingUploadId = null where m.id = :id")
    int clearPendingUpload(@Param("id") UUID id);
}
