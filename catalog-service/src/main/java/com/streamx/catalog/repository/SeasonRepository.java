package com.streamx.catalog.repository;

import com.streamx.catalog.domain.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeasonRepository extends JpaRepository<Season, UUID> {
    List<Season> findByTvShowIdOrderBySeasonNumberAsc(UUID tvShowId);
}
