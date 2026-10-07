package com.streamx.catalog.repository;

import com.streamx.catalog.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, UUID> {
    List<Episode> findBySeasonIdOrderByEpisodeNumberAsc(UUID seasonId);
}
