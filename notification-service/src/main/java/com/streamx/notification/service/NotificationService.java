package com.streamx.notification.service;

import com.streamx.notification.domain.NotificationLog;
import com.streamx.notification.dto.NotificationResponseDto;
import com.streamx.notification.dto.SendNotificationRequestDto;
import com.streamx.notification.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationLogRepository repository;

    public NotificationService(NotificationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationResponseDto sendNotification(SendNotificationRequestDto request) {
        log.info("Sending {} notification to recipient {} for template {}",
                request.getChannel(), request.getRecipient(), request.getTemplate());

        // Mock delivery logic: always succeeds in this environment
        String status = "SENT";

        NotificationLog logEntry = new NotificationLog(
                request.getAccountId(),
                request.getRecipient(),
                request.getChannel() != null ? request.getChannel() : "EMAIL",
                request.getTemplate() != null ? request.getTemplate() : "WELCOME",
                request.getSubject() != null ? request.getSubject() : "StreamX Notification",
                request.getBody(),
                status
        );

        NotificationLog saved = repository.save(logEntry);
        return mapToDto(saved);
    }

    public List<NotificationResponseDto> getUserNotifications(UUID accountId) {
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private NotificationResponseDto mapToDto(NotificationLog logEntry) {
        return new NotificationResponseDto(
                logEntry.getId(),
                logEntry.getAccountId(),
                logEntry.getRecipient(),
                logEntry.getChannel(),
                logEntry.getTemplate(),
                logEntry.getSubject(),
                logEntry.getBody(),
                logEntry.getStatus(),
                logEntry.getCreatedAt()
        );
    }
}
