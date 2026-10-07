package com.streamx.catalog.repository;

import com.streamx.catalog.domain.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeasonRepository extends JpaRepository<Season, UUID> {
    List<Season> findByTvShowIdOrderBySeasonNumberAsc(UUID tvShowId);
    long countByTvShowId(UUID tvShowId);
    boolean existsByTvShowIdAndSeasonNumber(UUID tvShowId, Integer seasonNumber);
    boolean existsByTvShowIdAndSeasonNumberAndIdNot(UUID tvShowId, Integer seasonNumber, UUID id);

    /** Rows of {tvShowId (UUID), seasonCount (Long)}. */
    @Query("SELECT s.tvShowId, COUNT(s) FROM Season s WHERE s.tvShowId IN :tvShowIds GROUP BY s.tvShowId")
    List<Object[]> countByTvShowIds(@Param("tvShowIds") Collection<UUID> tvShowIds);
}
