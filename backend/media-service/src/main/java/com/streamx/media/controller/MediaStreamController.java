package com.streamx.media.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ForbiddenException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.security.JwtUtils;
import com.streamx.media.delivery.ObjectDelivery;
import com.streamx.media.exception.StorageUnavailableException;
import com.streamx.media.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Public HLS delivery (no bearer token): the stream token in the path authorizes one content id. Playlists reference
 * their siblings relatively, so players resolve every variant and segment under the same token path.
 */
@RestController
@RequestMapping("/api/v1/media/stream")
public class MediaStreamController {

    static final String PLAYLIST = "application/vnd.apple.mpegurl";
    static final String SEGMENT = "video/mp2t";

    private final MediaService mediaService;
    private final JwtUtils jwtUtils;
    private final ObjectDelivery delivery;

    public MediaStreamController(MediaService mediaService, JwtUtils jwtUtils, ObjectDelivery delivery) {
        this.mediaService = mediaService;
        this.jwtUtils = jwtUtils;
        this.delivery = delivery;
    }

    @GetMapping("/{streamToken}/{contentId}/{file:.+}")
    public void stream(@PathVariable("streamToken") String streamToken,
                       @PathVariable("contentId") String contentId,
                       @PathVariable("file") String file,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        if (jwtUtils.parseStreamToken(streamToken, contentId) == null) {
            throw new ForbiddenException("Stream link is invalid or has expired");
        }
        MediaService.HlsObject object = mediaService.resolveHlsObject(contentId, file);
        // Playlists are rewritten when a video is replaced; segments of one rendition never change.
        boolean playlist = file.endsWith(".m3u8");
        String etag = playlist || object.stat().etag() == null ? null : "\"" + object.stat().etag().replace("\"", "") + "\"";
        delivery.write(request, response, object.key(), object.stat().size(),
                playlist ? PLAYLIST : SEGMENT,
                playlist ? "no-store" : "private, max-age=86400",
                etag);
    }

    // Players often send Accept headers without JSON; a preset content type bypasses negotiation so the
    // real status code still reaches them.
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> forbidden(ForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> badRequest(BadRequestException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> notFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ApiResponse<Object>> storageUnavailable(StorageUnavailableException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    private static ResponseEntity<ApiResponse<Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.error(message));
    }
}
