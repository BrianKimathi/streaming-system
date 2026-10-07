package com.streamx.catalog.repository;

import com.streamx.catalog.domain.ContentStatus;
import com.streamx.catalog.domain.TvShow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TvShowRepository extends JpaRepository<TvShow, UUID> {
    Page<TvShow> findByStatus(ContentStatus status, Pageable pageable);
    Page<TvShow> findByTitleContainingIgnoreCaseAndStatus(String title, ContentStatus status, Pageable pageable);
}
