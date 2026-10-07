package com.streamx.playback.service;

import com.streamx.playback.domain.PlaybackStatus;
import com.streamx.playback.repository.PlaybackSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PlaybackAdminService {

    private static final int SERIES_DAYS = 14;

    private final PlaybackSessionRepository repository;
    private final PlaybackService playbackService;

    public PlaybackAdminService(PlaybackSessionRepository repository, PlaybackService playbackService) {
        this.repository = repository;
        this.playbackService = playbackService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        Map<LocalDate, Long> perDay = new TreeMap<>();
        for (int i = SERIES_DAYS - 1; i >= 0; i--) {
            perDay.put(today.minusDays(i), 0L);
        }
        for (LocalDateTime start : repository.findStartTimesSince(today.minusDays(SERIES_DAYS - 1).atStartOfDay())) {
            perDay.computeIfPresent(start.toLocalDate(), (day, count) -> count + 1);
        }
        List<Map<String, Object>> dailySessions = new ArrayList<>();
        perDay.forEach((day, count) -> dailySessions.add(Map.of("date", day.toString(), "count", count)));

        List<Map<String, Object>> topContent = new ArrayList<>();
        for (Object[] row : repository.topContentSince(now.minusDays(7), PageRequest.of(0, 10))) {
            topContent.add(Map.of("contentId", row[0].toString(), "sessions", row[1]));
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("activeStreams", repository.countByStatusAndLastHeartbeatGreaterThanEqual(
                PlaybackStatus.ACTIVE, playbackService.heartbeatCutoff()));
        stats.put("totalSessions", repository.count());
        stats.put("sessionsToday", repository.countByStartTimeGreaterThanEqual(today.atStartOfDay()));
        stats.put("dailyActiveAccounts", repository.countDistinctAccountsSince(now.minusHours(24)));
        stats.put("weeklyActiveAccounts", repository.countDistinctAccountsSince(now.minusDays(7)));
        stats.put("monthlyActiveAccounts", repository.countDistinctAccountsSince(now.minusDays(30)));
        stats.put("dailySessions", dailySessions);
        stats.put("topContentLast7Days", topContent);
        return stats;
    }
}
