package com.streamx.catalog.repository;

import com.streamx.catalog.domain.ContentStatus;
import com.streamx.catalog.domain.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {
    Page<Movie> findByStatus(ContentStatus status, Pageable pageable);
    Page<Movie> findByTitleContainingIgnoreCaseAndStatus(String title, ContentStatus status, Pageable pageable);
}
