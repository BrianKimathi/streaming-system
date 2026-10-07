package com.streamx.media.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.service.MediaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
        return ResponseEntity.ok(ApiResponse.success("Media uploaded and HLS transcoding completed", response));
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> getMediaAsset(@PathVariable("contentId") String contentId) {
        MediaAssetResponse response = mediaService.getMediaAsset(contentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping(value = "/{contentId}/hls/{filename:.+}")
    public ResponseEntity<byte[]> getHlsFile(
            @PathVariable("contentId") String contentId,
            @PathVariable("filename") String filename) {

        byte[] data = mediaService.getHlsFile(contentId, filename);

        String contentType = "application/octet-stream";
        if (filename.endsWith(".m3u8")) {
            contentType = "application/vnd.apple.mpegurl";
        } else if (filename.endsWith(".ts")) {
            contentType = "video/MP2T";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(data);
    }
}
