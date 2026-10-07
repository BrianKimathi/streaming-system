package com.streamx.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.notification.dto.NotificationResponseDto;
import com.streamx.notification.dto.SendNotificationRequestDto;
import com.streamx.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void getUserNotifications_Returns200() throws Exception {
        UUID accountId = UUID.randomUUID();
        NotificationResponseDto dto = new NotificationResponseDto(
                UUID.randomUUID(), accountId, "user@example.com", "EMAIL", "WELCOME", "Subject", "Body", "SENT", Instant.now()
        );

        when(notificationService.getUserNotifications(accountId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/notifications/user").header("X-Account-Id", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].recipient").value("user@example.com"));
    }

    @Test
    void getUserNotifications_IncludesFailureReasonAndKeepsServiceOrder() throws Exception {
        UUID accountId = UUID.randomUUID();
        NotificationResponseDto newest = new NotificationResponseDto(
                UUID.randomUUID(), accountId, accountId.toString(), "IN_APP", "PAYMENT_FAILED", "Payment failed", "Body", "DELIVERED", Instant.now()
        );
        NotificationResponseDto older = new NotificationResponseDto(
                UUID.randomUUID(), accountId, "user@example.com", "EMAIL", "WELCOME", "Welcome", "Body", "FAILED", Instant.now().minusSeconds(60)
        );
        older.setFailureReason("Email delivery is not configured (set SPRING_MAIL_HOST and credentials)");
        when(notificationService.getUserNotifications(accountId)).thenReturn(List.of(newest, older));

        mockMvc.perform(get("/api/v1/notifications/user").header("X-Account-Id", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].template").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.data[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data[1].failureReason").value("Email delivery is not configured (set SPRING_MAIL_HOST and credentials)"));
    }

    @Test
    void getUserNotifications_WithoutAccountHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendInAppNotificationFromInternalCallerWithoutGatewayHeaders() throws Exception {
        UUID accountId = UUID.randomUUID();
        NotificationResponseDto dto = new NotificationResponseDto(
                UUID.randomUUID(), accountId, accountId.toString(), "IN_APP", "PAYMENT_SUCCESS", "Payment received", "Your plan is active", "DELIVERED", Instant.now()
        );
        when(notificationService.sendNotification(any(SendNotificationRequestDto.class))).thenReturn(dto);

        String body = "{\"accountId\":\"" + accountId + "\",\"recipient\":\"" + accountId + "\",\"channel\":\"IN_APP\","
                + "\"template\":\"PAYMENT_SUCCESS\",\"subject\":\"Payment received\",\"body\":\"Your plan is active\"}";

        mockMvc.perform(post("/api/v1/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("IN_APP"))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }

    @Test
    void sendNotification_Returns200() throws Exception {
        UUID accountId = UUID.randomUUID();
        SendNotificationRequestDto request = new SendNotificationRequestDto(
                accountId, "user@example.com", "EMAIL", "WELCOME", "Subject", "Body"
        );
        NotificationResponseDto dto = new NotificationResponseDto(
                UUID.randomUUID(), accountId, "user@example.com", "EMAIL", "WELCOME", "Subject", "Body", "SENT", Instant.now()
        );

        when(notificationService.sendNotification(any(SendNotificationRequestDto.class))).thenReturn(dto);

        mockMvc.perform(post("/api/v1/notifications/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }
}
