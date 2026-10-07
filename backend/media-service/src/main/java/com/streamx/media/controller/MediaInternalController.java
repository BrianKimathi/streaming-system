package com.streamx.media.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.media.dto.MediaStatusResponse;
import com.streamx.media.service.MediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service only; the gateway blocks /api/v1/media/internal/** from outside. */
@RestController
@RequestMapping("/api/v1/media/internal")
public class MediaInternalController {

    private final MediaService mediaService;

    public MediaInternalController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/{contentId}/status")
    public ResponseEntity<ApiResponse<MediaStatusResponse>> status(@PathVariable("contentId") String contentId) {
        MediaStatusResponse status = mediaService.getStatus(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("No video uploaded for content " + contentId));
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
