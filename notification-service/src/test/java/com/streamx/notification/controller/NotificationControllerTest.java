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

        mockMvc.perform(get("/api/v1/notifications/user?accountId=" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].recipient").value("user@example.com"));
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
