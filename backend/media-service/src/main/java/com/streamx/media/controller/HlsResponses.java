package com.streamx.media.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Path;

final class HlsResponses {

    static final MediaType PLAYLIST = MediaType.parseMediaType("application/vnd.apple.mpegurl");
    static final MediaType SEGMENT = MediaType.parseMediaType("video/mp2t");

    private HlsResponses() {
    }

    /** Streams the file from disk; playlists must be revalidated, segments are immutable for a given transcode. */
    static ResponseEntity<Resource> serve(Path file) {
        boolean playlist = file.getFileName().toString().endsWith(".m3u8");
        return ResponseEntity.ok()
                .contentType(playlist ? PLAYLIST : SEGMENT)
                .header(HttpHeaders.CACHE_CONTROL, playlist ? "no-cache" : "private, max-age=3600")
                .body(new FileSystemResource(file));
    }
}
