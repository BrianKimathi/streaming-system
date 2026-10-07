package com.streamx.catalog.repository;

import com.streamx.catalog.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GenreRepository extends JpaRepository<Genre, UUID> {
    Optional<Genre> findBySlug(String slug);
    boolean existsByName(String name);
}
