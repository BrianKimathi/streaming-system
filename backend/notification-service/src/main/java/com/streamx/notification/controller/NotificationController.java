package com.streamx.notification.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.notification.dto.NotificationResponseDto;
import com.streamx.notification.dto.SendNotificationRequestDto;
import com.streamx.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> getUserNotifications(
            @RequestHeader(value = SecurityConstants.HEADER_X_ACCOUNT_ID, required = false) String accountIdHeader) {

        if (accountIdHeader == null || accountIdHeader.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required"));
        }

        List<NotificationResponseDto> notifications = notificationService.getUserNotifications(UUID.fromString(accountIdHeader));
        return ResponseEntity.ok(ApiResponse.success("User notifications retrieved successfully", notifications));
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> sendNotification(
            @RequestBody SendNotificationRequestDto request) {
        NotificationResponseDto response = notificationService.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.success("Notification " + response.getStatus().toLowerCase(), response));
    }
}
