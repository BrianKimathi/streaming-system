package com.streamx.media.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.dto.MediaFileResponse;
import com.streamx.media.dto.PreviewResponse;
import com.streamx.media.service.MediaAdminService;
import com.streamx.media.service.MediaFileService;
import com.streamx.media.service.MediaService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin-only: the gateway restricts /api/v1/media/admin/** to admin roles. */
@RestController
@RequestMapping("/api/v1/media/admin")
public class MediaAdminController {

    private final MediaAdminService mediaAdminService;
    private final MediaService mediaService;
    private final MediaFileService mediaFileService;

    public MediaAdminController(MediaAdminService mediaAdminService, MediaService mediaService,
                                MediaFileService mediaFileService) {
        this.mediaAdminService = mediaAdminService;
        this.mediaService = mediaService;
        this.mediaFileService = mediaFileService;
    }

    @GetMapping("/assets")
    public ResponseEntity<ApiResponse<List<MediaAssetResponse>>> listAssets() {
        return ResponseEntity.ok(ApiResponse.success(mediaAdminService.listAssets()));
    }

    @GetMapping("/assets/by-content/{contentId}")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> byContent(@PathVariable("contentId") UUID contentId) {
        return ResponseEntity.ok(ApiResponse.success(mediaService.getByContentId(contentId)));
    }

    @PostMapping("/assets/{id}/retry")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> retry(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Processing re-queued", mediaService.retryTranscode(id)));
    }

    @DeleteMapping("/assets/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable("id") UUID id) {
        mediaService.deleteAsset(id);
        return ResponseEntity.ok(ApiResponse.success("Video deleted", null));
    }

    @GetMapping("/assets/{contentId}/preview")
    public ResponseEntity<ApiResponse<PreviewResponse>> preview(
            @PathVariable("contentId") UUID contentId,
            @RequestHeader(value = SecurityConstants.HEADER_X_ACCOUNT_ID, required = false) String accountId) {
        return ResponseEntity.ok(ApiResponse.success(mediaService.preview(contentId, accountId)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> stats() {
        return ResponseEntity.ok(ApiResponse.success(mediaAdminService.getStats()));
    }

    @GetMapping("/files")
    public ResponseEntity<ApiResponse<Page<MediaFileResponse>>> listFiles(
            @RequestParam(value = "purpose", required = false) UploadPurpose purpose,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(mediaFileService.list(purpose, page, size)));
    }

    @DeleteMapping("/files/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable("id") UUID id) {
        mediaFileService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("File deleted", null));
    }
}
