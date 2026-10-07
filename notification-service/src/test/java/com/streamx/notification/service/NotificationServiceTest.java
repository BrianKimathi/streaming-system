package com.streamx.notification.service;

import com.streamx.notification.domain.NotificationLog;
import com.streamx.notification.dto.NotificationResponseDto;
import com.streamx.notification.dto.SendNotificationRequestDto;
import com.streamx.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository repository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void sendNotification_SavesAndReturnsSentNotification() {
        UUID accountId = UUID.randomUUID();
        SendNotificationRequestDto request = new SendNotificationRequestDto(
                accountId, "user@example.com", "EMAIL", "WELCOME", "Welcome to StreamX", "Enjoy streaming!"
        );

        when(repository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDto response = notificationService.sendNotification(request);

        assertNotNull(response);
        assertEquals(accountId, response.getAccountId());
        assertEquals("user@example.com", response.getRecipient());
        assertEquals("SENT", response.getStatus());
        assertEquals("WELCOME", response.getTemplate());
    }

    @Test
    void getUserNotifications_ReturnsAccountNotifications() {
        UUID accountId = UUID.randomUUID();
        NotificationLog log1 = new NotificationLog(accountId, "user@example.com", "EMAIL", "WELCOME", "Subj", "Body", "SENT");
        NotificationLog log2 = new NotificationLog(accountId, "user@example.com", "SMS", "OTP", "Subj2", "Body2", "SENT");

        when(repository.findByAccountIdOrderByCreatedAtDesc(accountId)).thenReturn(List.of(log1, log2));

        List<NotificationResponseDto> results = notificationService.getUserNotifications(accountId);

        assertEquals(2, results.size());
        assertEquals("EMAIL", results.get(0).getChannel());
        assertEquals("SMS", results.get(1).getChannel());
    }
}
