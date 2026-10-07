package com.streamx.media.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.service.MediaAdminService;
import com.streamx.media.service.MediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media/admin")
public class MediaAdminController {

    private final MediaAdminService mediaAdminService;
    private final MediaService mediaService;

    public MediaAdminController(MediaAdminService mediaAdminService, MediaService mediaService) {
        this.mediaAdminService = mediaAdminService;
        this.mediaService = mediaService;
    }

    @GetMapping("/assets")
    public ResponseEntity<ApiResponse<List<MediaAssetResponse>>> listAssets() {
        return ResponseEntity.ok(ApiResponse.success(mediaAdminService.listAssets()));
    }

    @PostMapping("/assets/{id}/retry")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> retry(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Transcoding re-queued", mediaService.retryTranscode(id)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(mediaAdminService.getStats()));
    }
}
