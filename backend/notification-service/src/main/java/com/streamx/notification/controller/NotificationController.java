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
            @RequestHeader(value = SecurityConstants.HEADER_X_ACCOUNT_ID, required = false) String accountIdHeader,
            @RequestParam(required = false) UUID accountId) {

        UUID targetAccountId = accountId;
        if (targetAccountId == null && accountIdHeader != null) {
            targetAccountId = UUID.fromString(accountIdHeader);
        }

        if (targetAccountId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Account ID must be provided via header or request parameter"));
        }

        List<NotificationResponseDto> notifications = notificationService.getUserNotifications(targetAccountId);
        return ResponseEntity.ok(ApiResponse.success("User notifications retrieved successfully", notifications));
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> sendNotification(
            @RequestBody SendNotificationRequestDto request) {
        NotificationResponseDto response = notificationService.sendNotification(request);
        return ResponseEntity.ok(ApiResponse.success("Notification dispatched successfully", response));
    }
}
