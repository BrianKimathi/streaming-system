package com.streamx.notification.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.notification.dto.NotificationResponseDto;
import com.streamx.notification.service.NotificationAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/admin")
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;

    public NotificationAdminController(NotificationAdminService notificationAdminService) {
        this.notificationAdminService = notificationAdminService;
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> logs(
            @RequestParam(value = "accountId", required = false) UUID accountId) {
        return ResponseEntity.ok(ApiResponse.success(notificationAdminService.listLogs(accountId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(notificationAdminService.getStats()));
    }
}
