package com.streamx.trending.service;

import com.streamx.trending.domain.TrendingItem;
import com.streamx.trending.dto.RecordEventRequestDto;
import com.streamx.trending.dto.TrendingItemResponseDto;
import com.streamx.trending.repository.TrendingItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrendingServiceTest {

    @Mock
    private TrendingItemRepository repository;

    @InjectMocks
    private TrendingService trendingService;

    private UUID contentId;

    @BeforeEach
    void setUp() {
        contentId = UUID.randomUUID();
    }

    @Test
    void recordEvent_NewItem_CalculatesScoreCorrectly() {
        RecordEventRequestDto request = new RecordEventRequestDto(contentId, "Inception", "MOVIE", "VIEW");

        when(repository.findByContentId(contentId)).thenReturn(Optional.empty());
        when(repository.save(any(TrendingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrendingItemResponseDto response = trendingService.recordEvent(request);

        assertNotNull(response);
        assertEquals(contentId, response.getContentId());
        assertEquals("Inception", response.getTitle());
        assertEquals(1, response.getViews1h());
        assertEquals(1, response.getViews6h());
        // Score = (5*1) + (3*1) = 8.0
        assertEquals(8.0, response.getVelocityScore());
        verify(repository).save(any(TrendingItem.class));
    }

    @Test
    void recordEvent_ExistingItem_IncrementsCounts() {
        TrendingItem item = new TrendingItem(contentId, "Inception", "MOVIE");
        item.setViews1h(2);
        item.setViews6h(5);
        item.setCompletions24h(1);

        RecordEventRequestDto request = new RecordEventRequestDto(contentId, "Inception", "MOVIE", "COMPLETION");

        when(repository.findByContentId(contentId)).thenReturn(Optional.of(item));
        when(repository.save(any(TrendingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrendingItemResponseDto response = trendingService.recordEvent(request);

        assertEquals(2, response.getCompletions24h());
        // Score = (5*2) + (3*5) + (2*2) = 10 + 15 + 4 = 29.0
        assertEquals(29.0, response.getVelocityScore());
    }

    @Test
    void getTopTrending_ReturnsOrderedList() {
        TrendingItem item1 = new TrendingItem(UUID.randomUUID(), "Popular Movie", "MOVIE");
        item1.setVelocityScore(100.0);
        TrendingItem item2 = new TrendingItem(UUID.randomUUID(), "Niche Movie", "MOVIE");
        item2.setVelocityScore(10.0);

        when(repository.findTopTrending()).thenReturn(List.of(item1, item2));

        List<TrendingItemResponseDto> results = trendingService.getTopTrending(5);

        assertEquals(2, results.size());
        assertEquals("Popular Movie", results.get(0).getTitle());
        assertEquals(100.0, results.get(0).getVelocityScore());
    }

    @Test
    void calculateVelocityScore_ValidWeights() {
        TrendingItem item = new TrendingItem(contentId, "Test", "MOVIE");
        item.setViews1h(10);        // 5 * 10 = 50
        item.setViews6h(20);        // 3 * 20 = 60
        item.setCompletions24h(5);  // 2 * 5  = 10
        item.setLikes24h(3);        // 1 * 3  = 3

        double score = trendingService.calculateVelocityScore(item);
        assertEquals(123.0, score);
    }
}
