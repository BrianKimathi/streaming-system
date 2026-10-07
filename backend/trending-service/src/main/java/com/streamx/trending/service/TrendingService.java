package com.streamx.trending.service;

import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.trending.client.CatalogClient;
import com.streamx.trending.client.CatalogTitle;
import com.streamx.trending.domain.TrendingEvent;
import com.streamx.trending.domain.TrendingItem;
import com.streamx.trending.dto.RecordTrendingEventRequest;
import com.streamx.trending.dto.TrendingItemResponseDto;
import com.streamx.trending.exception.CatalogUnavailableException;
import com.streamx.trending.repository.TitleActivity;
import com.streamx.trending.repository.TrendingEventRepository;
import com.streamx.trending.repository.TrendingItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trending counters are derived from timestamped events, so they decay as events age out of their windows:
 * views1h / views6h count VIEW events in the last 1h / 6h, completions24h counts COMPLETION events in the last 24h.
 * There is no like feature, so likes24h is always 0.
 */
@Service
public class TrendingService {

    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 50;

    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);

    private final TrendingItemRepository itemRepository;
    private final TrendingEventRepository eventRepository;
    private final CatalogClient catalogClient;
    private final Clock clock;

    public TrendingService(TrendingItemRepository itemRepository, TrendingEventRepository eventRepository,
                           CatalogClient catalogClient, Clock clock) {
        this.itemRepository = itemRepository;
        this.eventRepository = eventRepository;
        this.catalogClient = catalogClient;
        this.clock = clock;
    }

    public TrendingItemResponseDto recordEvent(RecordTrendingEventRequest request) {
        ResolvedTitle title = resolveTitle(request.titleId());
        Instant now = clock.instant();
        eventRepository.save(new TrendingEvent(title.id(), request.eventType(), now));
        return mapToDto(refreshItem(title, now));
    }

    @Transactional(readOnly = true)
    public List<TrendingItemResponseDto> getTopTrending(int limit) {
        int size = Math.clamp(limit, MIN_LIMIT, MAX_LIMIT);
        return itemRepository.findByVelocityScoreGreaterThanOrderByVelocityScoreDesc(0.0, PageRequest.of(0, size))
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Drops events past retention, recomputes every item's counters from the remaining events and removes items
     * with no activity left in the retention window.
     *
     * @return number of items removed
     */
    @Transactional
    public int recomputeAll() {
        Instant now = clock.instant();
        eventRepository.deleteOlderThan(now.minus(TrendingEventRepository.RETENTION));
        Map<UUID, TitleActivity> activityByTitle = eventRepository.activitySince(now).stream()
                .collect(Collectors.toMap(TitleActivity::titleId, Function.identity()));

        List<TrendingItem> inactive = new ArrayList<>();
        for (TrendingItem item : itemRepository.findAll()) {
            TitleActivity activity = activityByTitle.get(item.getContentId());
            if (activity == null) {
                inactive.add(item);
            } else {
                applyActivity(item, activity, now);
            }
        }
        itemRepository.deleteAll(inactive);
        return inactive.size();
    }

    /**
     * Keeps public trending honest after catalog changes: titles that were deleted or unpublished are removed and
     * renamed titles pick up their new name. Stops early (keeping current data) if the catalog is unreachable.
     */
    public void refreshCatalogMetadata() {
        for (TrendingItem item : itemRepository.findAll()) {
            Optional<CatalogTitle> title;
            try {
                title = catalogClient.findTitle(item.getContentId());
            } catch (CatalogUnavailableException e) {
                log.warn("Skipping trending metadata refresh: {}", e.getMessage());
                return;
            }
            if (title.isEmpty() || !title.get().isPublished()) {
                itemRepository.delete(item);
                eventRepository.deleteByTitle(item.getContentId());
            } else if (!Objects.equals(title.get().title(), item.getTitle())) {
                item.setTitle(title.get().title());
                itemRepository.save(item);
            }
        }
    }

    public double calculateVelocityScore(TrendingItem item) {
        return (5.0 * item.getViews1h())
                + (3.0 * item.getViews6h())
                + (2.0 * item.getCompletions24h())
                + (1.0 * item.getLikes24h());
    }

    private ResolvedTitle resolveTitle(UUID titleId) {
        CatalogTitle title = catalogClient.findTitle(titleId)
                .orElseThrow(() -> new ResourceNotFoundException("Title not found"));
        if (CatalogTitle.EPISODE.equals(title.titleType())) {
            if (title.tvShowId() == null) {
                throw new ResourceNotFoundException("Title not found");
            }
            title = catalogClient.findTitle(title.tvShowId())
                    .filter(show -> CatalogTitle.SERIES.equals(show.titleType()))
                    .orElseThrow(() -> new ResourceNotFoundException("Title not found"));
        }
        if (!CatalogTitle.MOVIE.equals(title.titleType()) && !CatalogTitle.SERIES.equals(title.titleType())) {
            throw new ResourceNotFoundException("Title not found");
        }
        if (!title.isPublished()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only published titles can trend");
        }
        return new ResolvedTitle(title.id(), title.title(), title.titleType());
    }

    private TrendingItem refreshItem(ResolvedTitle title, Instant now) {
        try {
            return saveWithActivity(itemRepository.findByContentId(title.id())
                    .orElseGet(() -> new TrendingItem(title.id(), title.title(), title.contentType())), title, now);
        } catch (DataIntegrityViolationException concurrentInsert) {
            TrendingItem existing = itemRepository.findByContentId(title.id()).orElseThrow(() -> concurrentInsert);
            return saveWithActivity(existing, title, now);
        }
    }

    private TrendingItem saveWithActivity(TrendingItem item, ResolvedTitle title, Instant now) {
        item.setTitle(title.title());
        item.setContentType(title.contentType());
        applyActivity(item, eventRepository.activityFor(title.id(), now).orElse(null), now);
        return itemRepository.save(item);
    }

    private void applyActivity(TrendingItem item, TitleActivity activity, Instant now) {
        item.setViews1h(activity != null ? nullToZero(activity.views1h()) : 0);
        item.setViews6h(activity != null ? nullToZero(activity.views6h()) : 0);
        item.setCompletions24h(activity != null ? nullToZero(activity.completions24h()) : 0);
        item.setLikes24h(0);
        item.setVelocityScore(calculateVelocityScore(item));
        item.setUpdatedAt(now);
    }

    private static long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    private TrendingItemResponseDto mapToDto(TrendingItem item) {
        return new TrendingItemResponseDto(
                item.getContentId(),
                item.getTitle(),
                item.getContentType(),
                item.getViews1h(),
                item.getViews6h(),
                item.getCompletions24h(),
                item.getLikes24h(),
                item.getVelocityScore(),
                item.getUpdatedAt()
        );
    }

    private record ResolvedTitle(UUID id, String title, String contentType) {
    }
}
