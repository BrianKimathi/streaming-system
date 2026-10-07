package com.streamx.catalog.service;

import com.streamx.catalog.domain.Episode;
import com.streamx.catalog.domain.Season;
import com.streamx.catalog.domain.TvShow;
import com.streamx.catalog.dto.EpisodeResponse;
import com.streamx.catalog.dto.SeasonResponse;
import com.streamx.catalog.dto.TvShowDetailResponse;
import com.streamx.catalog.dto.TvShowResponse;
import com.streamx.catalog.repository.EpisodeRepository;
import com.streamx.catalog.repository.SeasonRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds TV show responses with accurate season counts and nested seasons/episodes using batched queries.
 */
@Component
public class TvShowAssembler {

    private final SeasonRepository seasonRepository;
    private final EpisodeRepository episodeRepository;
    private final CatalogMapper mapper;

    public TvShowAssembler(SeasonRepository seasonRepository, EpisodeRepository episodeRepository,
                           CatalogMapper mapper) {
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.mapper = mapper;
    }

    public TvShowResponse toResponse(TvShow show) {
        return mapper.toTvShowResponse(show, (int) seasonRepository.countByTvShowId(show.getId()));
    }

    public List<TvShowResponse> toResponses(Collection<TvShow> shows) {
        if (shows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> counts = new HashMap<>();
        Set<UUID> ids = shows.stream().map(TvShow::getId).collect(Collectors.toSet());
        for (Object[] row : seasonRepository.countByTvShowIds(ids)) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return shows.stream()
                .map(show -> mapper.toTvShowResponse(show, counts.getOrDefault(show.getId(), 0L).intValue()))
                .toList();
    }

    public TvShowDetailResponse toDetail(TvShow show) {
        List<Season> seasons = seasonRepository.findByTvShowIdOrderBySeasonNumberAsc(show.getId());
        Map<UUID, List<Episode>> episodesBySeason = seasons.isEmpty()
                ? Map.of()
                : episodeRepository.findBySeasonIdInOrderByEpisodeNumberAsc(
                                seasons.stream().map(Season::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(Episode::getSeasonId, LinkedHashMap::new, Collectors.toList()));

        List<SeasonResponse> seasonResponses = seasons.stream()
                .map(season -> mapper.toSeasonResponse(season, toEpisodeResponses(
                        episodesBySeason.getOrDefault(season.getId(), List.of()), season)))
                .toList();
        return TvShowDetailResponse.of(mapper.toTvShowResponse(show, seasons.size()), seasonResponses);
    }

    public SeasonResponse toSeasonResponse(Season season) {
        return mapper.toSeasonResponse(season,
                toEpisodeResponses(episodeRepository.findBySeasonIdOrderByEpisodeNumberAsc(season.getId()), season));
    }

    private List<EpisodeResponse> toEpisodeResponses(List<Episode> episodes, Season season) {
        return episodes.stream().map(episode -> mapper.toEpisodeResponse(episode, season)).toList();
    }
}
