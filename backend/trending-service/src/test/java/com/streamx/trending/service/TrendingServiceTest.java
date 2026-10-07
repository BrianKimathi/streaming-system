package com.streamx.trending.service;

import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.trending.client.CatalogClient;
import com.streamx.trending.client.CatalogTitle;
import com.streamx.trending.domain.TrendingEvent;
import com.streamx.trending.domain.TrendingEventType;
import com.streamx.trending.domain.TrendingItem;
import com.streamx.trending.dto.RecordTrendingEventRequest;
import com.streamx.trending.dto.TrendingItemResponseDto;
import com.streamx.trending.exception.CatalogUnavailableException;
import com.streamx.trending.repository.TrendingEventRepository;
import com.streamx.trending.repository.TrendingItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.streamx.trending.domain.TrendingEventType.COMPLETION;
import static com.streamx.trending.domain.TrendingEventType.VIEW;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class TrendingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Autowired
    private TrendingItemRepository itemRepository;

    @Autowired
    private TrendingEventRepository eventRepository;

    private final CatalogClient catalogClient = mock(CatalogClient.class);
    private final MutableClock clock = new MutableClock(NOW);
    private TrendingService trendingService;

    @BeforeEach
    void setUp() {
        trendingService = new TrendingService(itemRepository, eventRepository, catalogClient, clock);
    }

    @Test
    void recordEvent_episodeCountsTowardsItsShow() {
        UUID episodeId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        when(catalogClient.findTitle(episodeId))
                .thenReturn(Optional.of(new CatalogTitle(episodeId, "Pilot", "EPISODE", showId, "PUBLISHED")));
        when(catalogClient.findTitle(showId))
                .thenReturn(Optional.of(new CatalogTitle(showId, "The Show", "SERIES", null, "PUBLISHED")));

        TrendingItemResponseDto response = trendingService.recordEvent(new RecordTrendingEventRequest(episodeId, VIEW));

        assertEquals(showId, response.getContentId());
        assertEquals("The Show", response.getTitle());
        assertEquals("SERIES", response.getContentType());
        assertEquals(1, response.getViews1h());
        assertEquals(1, response.getViews6h());
        assertEquals(8.0, response.getVelocityScore());
        List<TrendingEvent> events = eventRepository.findAll();
        assertEquals(1, events.size());
        assertEquals(showId, events.get(0).getTitleId());
        assertTrue(itemRepository.findByContentId(episodeId).isEmpty());
    }

    @Test
    void recordEvent_recomputesCountersFromEventWindows() {
        UUID movieId = publishedMovie("Inception");
        event(movieId, VIEW, Duration.ofMinutes(30));
        event(movieId, VIEW, Duration.ofHours(3));
        event(movieId, VIEW, Duration.ofHours(10));
        event(movieId, COMPLETION, Duration.ofHours(20));
        event(movieId, COMPLETION, Duration.ofHours(30));

        TrendingItemResponseDto response = trendingService.recordEvent(
                new RecordTrendingEventRequest(movieId, COMPLETION));

        assertEquals("MOVIE", response.getContentType());
        assertEquals(1, response.getViews1h());
        assertEquals(2, response.getViews6h());
        assertEquals(2, response.getCompletions24h());
        assertEquals(0, response.getLikes24h());
        // 5*1 + 3*2 + 2*2
        assertEquals(15.0, response.getVelocityScore());
        assertEquals(NOW, response.getUpdatedAt());
    }

    @Test
    void recordEvent_unknownTitleIs404AndStoresNothing() {
        UUID unknown = UUID.randomUUID();
        when(catalogClient.findTitle(unknown)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> trendingService.recordEvent(new RecordTrendingEventRequest(unknown, VIEW)));
        assertEquals(0, eventRepository.count());
        assertEquals(0, itemRepository.count());
    }

    @Test
    void recordEvent_episodeOfMissingShowIs404() {
        UUID episodeId = UUID.randomUUID();
        UUID showId = UUID.randomUUID();
        when(catalogClient.findTitle(episodeId))
                .thenReturn(Optional.of(new CatalogTitle(episodeId, "Orphan", "EPISODE", showId, "PUBLISHED")));
        when(catalogClient.findTitle(showId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> trendingService.recordEvent(new RecordTrendingEventRequest(episodeId, VIEW)));
        assertEquals(0, eventRepository.count());
    }

    @Test
    void recordEvent_unpublishedTitleIsRejected() {
        UUID draftId = UUID.randomUUID();
        when(catalogClient.findTitle(draftId))
                .thenReturn(Optional.of(new CatalogTitle(draftId, "Draft", "MOVIE", null, "DRAFT")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> trendingService.recordEvent(new RecordTrendingEventRequest(draftId, VIEW)));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals(0, eventRepository.count());
    }

    @Test
    void recomputeAll_decaysCountersDeletesOldEventsAndRemovesInactiveItems() {
        UUID active = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();
        UUID dead = UUID.randomUUID();
        staleItem(active);
        staleItem(quiet);
        staleItem(dead);

        event(active, VIEW, Duration.ofMinutes(10));
        event(active, VIEW, Duration.ofHours(2));
        event(active, VIEW, Duration.ofHours(7));
        event(active, COMPLETION, Duration.ofHours(23));
        event(active, COMPLETION, Duration.ofHours(25));
        event(active, VIEW, Duration.ofHours(49));
        event(quiet, VIEW, Duration.ofHours(47));
        event(dead, VIEW, Duration.ofHours(50));

        int removed = trendingService.recomputeAll();

        assertEquals(1, removed);
        assertTrue(itemRepository.findByContentId(dead).isEmpty());
        assertEquals(6, eventRepository.count());

        TrendingItem activeItem = itemRepository.findByContentId(active).orElseThrow();
        assertEquals(1, activeItem.getViews1h());
        assertEquals(2, activeItem.getViews6h());
        assertEquals(1, activeItem.getCompletions24h());
        assertEquals(0, activeItem.getLikes24h());
        assertEquals(13.0, activeItem.getVelocityScore());
        assertEquals(NOW, activeItem.getUpdatedAt());

        TrendingItem quietItem = itemRepository.findByContentId(quiet).orElseThrow();
        assertEquals(0.0, quietItem.getVelocityScore());
        assertEquals(List.of(active), trendingService.getTopTrending(10).stream()
                .map(TrendingItemResponseDto::getContentId).toList());

        clock.advance(Duration.ofHours(2));
        trendingService.recomputeAll();

        TrendingItem decayed = itemRepository.findByContentId(active).orElseThrow();
        assertEquals(0, decayed.getViews1h());
        assertEquals(2, decayed.getViews6h());
        assertEquals(0, decayed.getCompletions24h());
        assertEquals(6.0, decayed.getVelocityScore());
        assertTrue(itemRepository.findByContentId(quiet).isEmpty());
    }

    @Test
    void getTopTrending_ordersByScoreSkipsZeroAndClampsLimit() {
        scoredItem("Low", 5.0);
        scoredItem("High", 50.0);
        scoredItem("Zero", 0.0);
        scoredItem("Mid", 20.0);

        assertEquals(List.of("High", "Mid"), titles(trendingService.getTopTrending(2)));
        assertEquals(List.of("High", "Mid", "Low"), titles(trendingService.getTopTrending(500)));
        assertEquals(List.of("High"), titles(trendingService.getTopTrending(0)));
    }

    @Test
    void refreshCatalogMetadata_removesUnpublishedAndRenames() {
        UUID renamed = UUID.randomUUID();
        UUID unpublished = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        staleItem(renamed);
        staleItem(unpublished);
        staleItem(deleted);
        event(unpublished, VIEW, Duration.ofMinutes(5));
        when(catalogClient.findTitle(renamed))
                .thenReturn(Optional.of(new CatalogTitle(renamed, "New Name", "MOVIE", null, "PUBLISHED")));
        when(catalogClient.findTitle(unpublished))
                .thenReturn(Optional.of(new CatalogTitle(unpublished, "Old", "MOVIE", null, "ARCHIVED")));
        when(catalogClient.findTitle(deleted)).thenReturn(Optional.empty());

        trendingService.refreshCatalogMetadata();

        assertEquals("New Name", itemRepository.findByContentId(renamed).orElseThrow().getTitle());
        assertTrue(itemRepository.findByContentId(unpublished).isEmpty());
        assertTrue(itemRepository.findByContentId(deleted).isEmpty());
        assertEquals(0, eventRepository.count());
    }

    @Test
    void refreshCatalogMetadata_keepsDataWhenCatalogIsDown() {
        UUID id = UUID.randomUUID();
        staleItem(id);
        when(catalogClient.findTitle(id)).thenThrow(new CatalogUnavailableException("down", null));

        trendingService.refreshCatalogMetadata();

        assertTrue(itemRepository.findByContentId(id).isPresent());
    }

    @Test
    void calculateVelocityScore_ValidWeights() {
        TrendingItem item = new TrendingItem(UUID.randomUUID(), "Test", "MOVIE");
        item.setViews1h(10);        // 5 * 10 = 50
        item.setViews6h(20);        // 3 * 20 = 60
        item.setCompletions24h(5);  // 2 * 5  = 10
        item.setLikes24h(3);        // 1 * 3  = 3

        assertEquals(123.0, trendingService.calculateVelocityScore(item));
    }

    private UUID publishedMovie(String title) {
        UUID id = UUID.randomUUID();
        when(catalogClient.findTitle(id)).thenReturn(Optional.of(new CatalogTitle(id, title, "MOVIE", null, "PUBLISHED")));
        return id;
    }

    private void event(UUID titleId, TrendingEventType type, Duration age) {
        eventRepository.save(new TrendingEvent(titleId, type, clock.instant().minus(age)));
    }

    private void staleItem(UUID id) {
        TrendingItem item = new TrendingItem(id, "Old", "MOVIE");
        item.setViews1h(100);
        item.setViews6h(100);
        item.setCompletions24h(100);
        item.setVelocityScore(1000.0);
        itemRepository.save(item);
    }

    private void scoredItem(String title, double score) {
        TrendingItem item = new TrendingItem(UUID.randomUUID(), title, "MOVIE");
        item.setVelocityScore(score);
        itemRepository.save(item);
    }

    private static List<String> titles(List<TrendingItemResponseDto> items) {
        return items.stream().map(TrendingItemResponseDto::getTitle).toList();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
