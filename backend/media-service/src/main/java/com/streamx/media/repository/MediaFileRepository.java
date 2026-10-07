package com.streamx.media.repository;

import com.streamx.media.domain.MediaFile;
import com.streamx.media.domain.UploadPurpose;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    Page<MediaFile> findByPurpose(UploadPurpose purpose, Pageable pageable);

    @Query("select coalesce(sum(f.sizeBytes), 0) from MediaFile f")
    long sumSizeBytes();
}
