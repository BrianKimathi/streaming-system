package com.streamx.media.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.service.MediaService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload/{contentId}")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> uploadMedia(
            @PathVariable("contentId") String contentId,
            @RequestParam("file") MultipartFile file) {
        MediaAssetResponse response = mediaService.processUpload(contentId, file);
        return ResponseEntity.ok(ApiResponse.success("Media uploaded; HLS transcoding started", response));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> getMediaAsset(@PathVariable("contentId") String contentId) {
        MediaAssetResponse response = mediaService.getMediaAsset(contentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/{contentId}/hls/{filename:.+}")
    public ResponseEntity<Resource> getHlsFile(
            @PathVariable("contentId") String contentId,
            @PathVariable("filename") String filename) {
        return HlsResponses.serve(mediaService.resolveHlsFile(contentId, filename));
    }
}
