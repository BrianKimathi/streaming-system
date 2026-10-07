package com.streamx.notification.service;

import com.streamx.notification.dto.NotificationResponseDto;
import com.streamx.notification.repository.NotificationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationAdminService {

    private final NotificationLogRepository repository;
    private final NotificationService notificationService;

    public NotificationAdminService(NotificationLogRepository repository, NotificationService notificationService) {
        this.repository = repository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> listLogs(UUID accountId) {
        var logs = accountId != null
                ? repository.findByAccountIdOrderByCreatedAtDesc(accountId)
                : repository.findTop500ByOrderByCreatedAtDesc();
        return logs.stream().map(notificationService::mapToDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String status : List.of("SENT", "DELIVERED", "FAILED", "PENDING")) {
            byStatus.put(status, repository.countByStatus(status));
        }
        Map<String, Long> byChannel = new LinkedHashMap<>();
        for (String channel : List.of("EMAIL", "SMS", "IN_APP")) {
            byChannel.put(channel, repository.countByChannel(channel));
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", repository.count());
        stats.put("last24Hours", repository.countByCreatedAtGreaterThanEqual(Instant.now().minus(Duration.ofHours(24))));
        stats.put("countByStatus", byStatus);
        stats.put("countByChannel", byChannel);
        return stats;
    }
}
