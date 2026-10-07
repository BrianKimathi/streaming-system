package com.streamx.media.repository;

import com.streamx.media.domain.UploadPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UploadPartRepository extends JpaRepository<UploadPart, UUID> {

    @Query("select p.partNumber from UploadPart p where p.uploadId = :uploadId order by p.partNumber")
    List<Integer> findPartNumbers(@Param("uploadId") UUID uploadId);

    boolean existsByUploadIdAndPartNumber(UUID uploadId, int partNumber);

    @Transactional
    @Modifying
    @Query("delete from UploadPart p where p.uploadId = :uploadId")
    int deleteByUploadId(@Param("uploadId") UUID uploadId);

    @Transactional
    @Modifying
    @Query("delete from UploadPart p where p.uploadId = :uploadId and p.partNumber in :partNumbers")
    int deleteParts(@Param("uploadId") UUID uploadId, @Param("partNumbers") Collection<Integer> partNumbers);
}
