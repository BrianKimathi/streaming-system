package com.streamx.notification.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.notification.domain.NotificationLog;
import com.streamx.notification.dto.NotificationResponseDto;
import com.streamx.notification.dto.SendNotificationRequestDto;
import com.streamx.notification.repository.NotificationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Set<String> CHANNELS = Set.of("EMAIL", "SMS", "IN_APP");

    private final NotificationLogRepository repository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String mailFrom;

    public NotificationService(NotificationLogRepository repository,
                               ObjectProvider<JavaMailSender> mailSenderProvider,
                               @Value("${spring.mail.host:}") String mailHost,
                               @Value("${notification.mail.from:}") String mailFrom) {
        this.repository = repository;
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.mailFrom = mailFrom;
    }

    @Transactional
    public NotificationResponseDto sendNotification(SendNotificationRequestDto request) {
        if (request.getAccountId() == null) {
            throw new BadRequestException("accountId is required");
        }
        if (request.getRecipient() == null || request.getRecipient().isBlank()) {
            throw new BadRequestException("recipient is required");
        }
        if (request.getBody() == null || request.getBody().isBlank()) {
            throw new BadRequestException("body is required");
        }
        String channel = request.getChannel() == null ? "EMAIL" : request.getChannel().trim().toUpperCase(Locale.ROOT);
        if (!CHANNELS.contains(channel)) {
            throw new BadRequestException("channel must be one of " + CHANNELS);
        }

        NotificationLog logEntry = new NotificationLog(
                request.getAccountId(),
                request.getRecipient().trim(),
                channel,
                request.getTemplate() != null && !request.getTemplate().isBlank() ? request.getTemplate() : "CUSTOM",
                request.getSubject() != null && !request.getSubject().isBlank() ? request.getSubject() : "StreamX Notification",
                request.getBody(),
                "PENDING"
        );

        deliver(logEntry);
        log.info("{} notification to {} finished with status {}", channel, logEntry.getRecipient(), logEntry.getStatus());
        return mapToDto(repository.save(logEntry));
    }

    public List<NotificationResponseDto> getUserNotifications(UUID accountId) {
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private void deliver(NotificationLog entry) {
        switch (entry.getChannel()) {
            case "IN_APP" -> entry.setStatus("DELIVERED");
            case "EMAIL" -> sendEmail(entry);
            default -> {
                entry.setStatus("FAILED");
                entry.setFailureReason("No SMS provider is configured for this deployment");
            }
        }
    }

    private void sendEmail(NotificationLog entry) {
        JavaMailSender sender = mailHost == null || mailHost.isBlank() ? null : mailSenderProvider.getIfAvailable();
        if (sender == null) {
            entry.setStatus("FAILED");
            entry.setFailureReason("Email delivery is not configured (set SPRING_MAIL_HOST and credentials)");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (mailFrom != null && !mailFrom.isBlank()) {
                message.setFrom(mailFrom);
            }
            message.setTo(entry.getRecipient());
            message.setSubject(entry.getSubject());
            message.setText(entry.getBody());
            sender.send(message);
            entry.setStatus("SENT");
        } catch (MailException e) {
            log.warn("Email delivery to {} failed: {}", entry.getRecipient(), e.getMessage());
            entry.setStatus("FAILED");
            String reason = e.getMessage() == null ? "SMTP error" : e.getMessage();
            entry.setFailureReason(reason.length() > 990 ? reason.substring(0, 990) : reason);
        }
    }

    NotificationResponseDto mapToDto(NotificationLog logEntry) {
        NotificationResponseDto dto = new NotificationResponseDto(
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
        dto.setFailureReason(logEntry.getFailureReason());
        return dto;
    }
}
