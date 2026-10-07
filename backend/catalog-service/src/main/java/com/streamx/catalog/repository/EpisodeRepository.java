package com.streamx.catalog.repository;

import com.streamx.catalog.domain.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, UUID> {
    List<Episode> findBySeasonIdOrderByEpisodeNumberAsc(UUID seasonId);
    List<Episode> findBySeasonIdInOrderByEpisodeNumberAsc(Collection<UUID> seasonIds);
    boolean existsBySeasonIdAndEpisodeNumber(UUID seasonId, Integer episodeNumber);
    boolean existsBySeasonIdAndEpisodeNumberAndIdNot(UUID seasonId, Integer episodeNumber, UUID id);
    void deleteBySeasonId(UUID seasonId);
}
