package com.streamx.media.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.security.SecurityConstants;
import com.streamx.media.dto.CompleteUploadResponse;
import com.streamx.media.dto.CreateUploadRequest;
import com.streamx.media.dto.ImportRequest;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.dto.PartUploadResponse;
import com.streamx.media.dto.UploadSessionResponse;
import com.streamx.media.service.MediaService;
import com.streamx.media.service.UploadSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

/** Chunked uploads and link imports (admin-only at the gateway). */
@RestController
@RequestMapping("/api/v1/media/admin")
public class MediaUploadController {

    private final UploadSessionService uploadSessionService;
    private final MediaService mediaService;

    public MediaUploadController(UploadSessionService uploadSessionService, MediaService mediaService) {
        this.uploadSessionService = uploadSessionService;
        this.mediaService = mediaService;
    }

    @PostMapping("/uploads")
    public ResponseEntity<ApiResponse<UploadSessionResponse>> create(
            @Valid @RequestBody CreateUploadRequest request,
            @RequestHeader(value = SecurityConstants.HEADER_X_ACCOUNT_ID, required = false) String accountId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Upload session created", uploadSessionService.create(request, accountId)));
    }

    @GetMapping("/uploads/{uploadId}")
    public ResponseEntity<ApiResponse<UploadSessionResponse>> get(@PathVariable("uploadId") UUID uploadId) {
        return ResponseEntity.ok(ApiResponse.success(uploadSessionService.get(uploadId)));
    }

    /** Raw body (application/octet-stream); read directly from the servlet stream so it is never buffered. */
    @PutMapping("/uploads/{uploadId}/parts/{partNumber}")
    public ResponseEntity<ApiResponse<PartUploadResponse>> putPart(@PathVariable("uploadId") UUID uploadId,
                                                                   @PathVariable("partNumber") int partNumber,
                                                                   HttpServletRequest request) throws IOException {
        PartUploadResponse response = uploadSessionService.putPart(uploadId, partNumber,
                request.getContentLengthLong(), request.getInputStream());
        return ResponseEntity.ok(ApiResponse.success("Part stored", response));
    }

    @PostMapping("/uploads/{uploadId}/complete")
    public ResponseEntity<ApiResponse<CompleteUploadResponse>> complete(@PathVariable("uploadId") UUID uploadId) {
        return ResponseEntity.ok(ApiResponse.success("Upload completed", uploadSessionService.complete(uploadId)));
    }

    @DeleteMapping("/uploads/{uploadId}")
    public ResponseEntity<ApiResponse<UploadSessionResponse>> abort(@PathVariable("uploadId") UUID uploadId) {
        return ResponseEntity.ok(ApiResponse.success("Upload aborted", uploadSessionService.abort(uploadId)));
    }

    @PostMapping("/imports")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> importFromLink(@Valid @RequestBody ImportRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Import started", mediaService.startImport(request)));
    }
}
