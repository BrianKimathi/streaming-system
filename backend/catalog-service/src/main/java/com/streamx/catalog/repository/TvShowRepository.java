package com.streamx.catalog.repository;

import com.streamx.catalog.domain.ContentStatus;
import com.streamx.catalog.domain.TvShow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TvShowRepository extends JpaRepository<TvShow, UUID>, JpaSpecificationExecutor<TvShow> {
    List<TvShow> findByIdInAndStatus(Collection<UUID> ids, ContentStatus status);
    long countByStatus(ContentStatus status);
}
