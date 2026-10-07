package com.streamx.notification.service;

import com.streamx.notification.domain.NotificationLog;
import com.streamx.notification.dto.NotificationResponseDto;
import com.streamx.notification.dto.SendNotificationRequestDto;
import com.streamx.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository repository;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender mailSender;

    private NotificationService service(String mailHost) {
        return new NotificationService(repository, mailSenderProvider, mailHost, "noreply@streamx.test");
    }

    @Test
    void sendEmail_WithoutSmtpConfigured_IsRecordedAsFailed() {
        UUID accountId = UUID.randomUUID();
        SendNotificationRequestDto request = new SendNotificationRequestDto(
                accountId, "user@example.com", "EMAIL", "WELCOME", "Welcome to StreamX", "Enjoy streaming!"
        );
        when(repository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDto response = service("").sendNotification(request);

        assertEquals(accountId, response.getAccountId());
        assertEquals("FAILED", response.getStatus());
        assertNotNull(response.getFailureReason());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendEmail_WithSmtpConfigured_SendsAndRecordsSent() {
        SendNotificationRequestDto request = new SendNotificationRequestDto(
                UUID.randomUUID(), "user@example.com", "EMAIL", "WELCOME", "Welcome", "Body"
        );
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(repository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDto response = service("smtp.example.com").sendNotification(request);

        assertEquals("SENT", response.getStatus());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendInApp_IsDelivered() {
        SendNotificationRequestDto request = new SendNotificationRequestDto(
                UUID.randomUUID(), "user@example.com", "in_app", null, null, "Hello"
        );
        when(repository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDto response = service("").sendNotification(request);

        assertEquals("IN_APP", response.getChannel());
        assertEquals("DELIVERED", response.getStatus());
    }

    @Test
    void sendInApp_ForPaymentRecordsAccountAndTemplate() {
        UUID accountId = UUID.randomUUID();
        SendNotificationRequestDto request = new SendNotificationRequestDto(
                accountId, accountId.toString(), "IN_APP", "PAYMENT_SUCCESS", "Payment received", "Your Premium plan is active"
        );
        when(repository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDto response = service("").sendNotification(request);

        assertEquals(accountId, response.getAccountId());
        assertEquals("PAYMENT_SUCCESS", response.getTemplate());
        assertEquals("Payment received", response.getSubject());
        assertEquals("DELIVERED", response.getStatus());
        assertNull(response.getFailureReason());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSms_IsRecordedAsFailedWithReason() {
        SendNotificationRequestDto request = new SendNotificationRequestDto(
                UUID.randomUUID(), "+254700000000", "SMS", "OTP", null, "Code"
        );
        when(repository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDto response = service("").sendNotification(request);

        assertEquals("FAILED", response.getStatus());
        assertNotNull(response.getFailureReason());
    }

    @Test
    void getUserNotifications_PreservesNewestFirstOrderAndFailureReason() {
        UUID accountId = UUID.randomUUID();
        NotificationLog newest = new NotificationLog(accountId, accountId.toString(), "IN_APP", "PAYMENT_SUCCESS", "Paid", "Body", "DELIVERED");
        NotificationLog older = new NotificationLog(accountId, "user@example.com", "EMAIL", "WELCOME", "Hi", "Body", "FAILED");
        older.setFailureReason("SMTP error");
        when(repository.findByAccountIdOrderByCreatedAtDesc(accountId)).thenReturn(List.of(newest, older));

        List<NotificationResponseDto> results = service("").getUserNotifications(accountId);

        assertEquals("PAYMENT_SUCCESS", results.get(0).getTemplate());
        assertEquals("SMTP error", results.get(1).getFailureReason());
        verify(repository).findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Test
    void getUserNotifications_ReturnsAccountNotifications() {
        UUID accountId = UUID.randomUUID();
        NotificationLog log1 = new NotificationLog(accountId, "user@example.com", "EMAIL", "WELCOME", "Subj", "Body", "SENT");
        NotificationLog log2 = new NotificationLog(accountId, "user@example.com", "SMS", "OTP", "Subj2", "Body2", "SENT");

        when(repository.findByAccountIdOrderByCreatedAtDesc(accountId)).thenReturn(List.of(log1, log2));

        List<NotificationResponseDto> results = service("").getUserNotifications(accountId);

        assertEquals(2, results.size());
        assertEquals("EMAIL", results.get(0).getChannel());
        assertEquals("SMS", results.get(1).getChannel());
    }
}
