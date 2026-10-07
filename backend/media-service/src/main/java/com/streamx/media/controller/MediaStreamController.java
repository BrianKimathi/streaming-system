package com.streamx.media.controller;

import com.streamx.common.dto.ApiResponse;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ForbiddenException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.security.JwtUtils;
import com.streamx.media.service.MediaService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public HLS delivery (no bearer token): the stream token in the path authorizes one content id. Playlists reference
 * their siblings relatively, so players resolve every variant and segment under the same token path.
 */
@RestController
@RequestMapping("/api/v1/media/stream")
public class MediaStreamController {

    private final MediaService mediaService;
    private final JwtUtils jwtUtils;

    public MediaStreamController(MediaService mediaService, JwtUtils jwtUtils) {
        this.mediaService = mediaService;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/{streamToken}/{contentId}/{file:.+}")
    public ResponseEntity<Resource> stream(@PathVariable("streamToken") String streamToken,
                                           @PathVariable("contentId") String contentId,
                                           @PathVariable("file") String file) {
        if (jwtUtils.parseStreamToken(streamToken, contentId) == null) {
            throw new ForbiddenException("Stream link is invalid or has expired");
        }
        return HlsResponses.serve(mediaService.resolveHlsFile(contentId, file));
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

    private static ResponseEntity<ApiResponse<Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.error(message));
    }
}
