package com.streamx.media.repository;

import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
