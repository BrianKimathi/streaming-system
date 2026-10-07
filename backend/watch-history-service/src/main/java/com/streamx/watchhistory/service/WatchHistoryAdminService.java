package com.streamx.watchhistory.service;

import com.streamx.watchhistory.repository.WatchProgressRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WatchHistoryAdminService {

    private final WatchProgressRepository repository;

    public WatchHistoryAdminService(WatchProgressRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        LocalDateTime now = LocalDateTime.now();
        long total = repository.count();
        long completed = repository.countByCompletedTrue();

        List<Map<String, Object>> topContent = new ArrayList<>();
        for (Object[] row : repository.topTitles(PageRequest.of(0, 10))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("titleId", row[0].toString());
            // Admin dashboard reads contentId; it carries the title (movie/show) id.
            item.put("contentId", row[0].toString());
            item.put("viewers", row[1]);
            item.put("completions", row[2]);
            topContent.add(item);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProgressRecords", total);
        stats.put("completedViews", completed);
        stats.put("completionRate", total == 0 ? 0.0 : Math.round(completed * 1000.0 / total) / 10.0);
        stats.put("averageProgressPercent", Math.round(repository.averagePercentage() * 10.0) / 10.0);
        stats.put("totalWatchSeconds", repository.sumPositionSeconds());
        stats.put("watchSecondsLast7Days", repository.sumPositionSecondsSince(now.minusDays(7)));
        stats.put("activeViewersLast7Days", repository.countDistinctProfilesSince(now.minusDays(7)));
        stats.put("activityLast24Hours", repository.countByLastWatchedAtGreaterThanEqual(now.minusHours(24)));
        stats.put("topContent", topContent);
        return stats;
    }
}
