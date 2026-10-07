package com.streamx.trending.service;

import com.streamx.trending.domain.TrendingItem;
import com.streamx.trending.dto.RecordEventRequestDto;
import com.streamx.trending.dto.TrendingItemResponseDto;
import com.streamx.trending.repository.TrendingItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrendingService {

    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);

    private final TrendingItemRepository repository;

    public TrendingService(TrendingItemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TrendingItemResponseDto recordEvent(RecordEventRequestDto request) {
        TrendingItem item = repository.findByContentId(request.getContentId())
                .orElseGet(() -> new TrendingItem(
                        request.getContentId(),
                        request.getTitle() != null ? request.getTitle() : "Untitled",
                        request.getContentType() != null ? request.getContentType() : "MOVIE"
                ));

        if (request.getEventType() != null) {
            switch (request.getEventType().toUpperCase()) {
                case "VIEW" -> {
                    item.setViews1h(item.getViews1h() + 1);
                    item.setViews6h(item.getViews6h() + 1);
                }
                case "COMPLETION" -> item.setCompletions24h(item.getCompletions24h() + 1);
                case "LIKE" -> item.setLikes24h(item.getLikes24h() + 1);
                default -> log.warn("Unknown event type: {}", request.getEventType());
            }
        }

        double score = calculateVelocityScore(item);
        item.setVelocityScore(score);
        item.setUpdatedAt(Instant.now());

        TrendingItem saved = repository.save(item);
        return mapToDto(saved);
    }

    public List<TrendingItemResponseDto> getTopTrending(int limit) {
        return repository.findTopTrending().stream()
                .limit(limit > 0 ? limit : 20)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public double calculateVelocityScore(TrendingItem item) {
        return (5.0 * item.getViews1h())
                + (3.0 * item.getViews6h())
                + (2.0 * item.getCompletions24h())
                + (1.0 * item.getLikes24h());
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
}
