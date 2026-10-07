package com.streamx.media.delivery;

import com.streamx.media.storage.ObjectStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

/** Streams a stored object to the client with Range, conditional-GET and caching support. */
@Component
public class ObjectDelivery {

    private static final Logger log = LoggerFactory.getLogger(ObjectDelivery.class);
    private static final int BUFFER_BYTES = 64 * 1024;

    private final ObjectStorage storage;

    public ObjectDelivery(ObjectStorage storage) {
        this.storage = storage;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, String key, long size,
                      String contentType, String cacheControl, String etag) throws IOException {
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CACHE_CONTROL, cacheControl);
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (etag != null) {
            response.setHeader(HttpHeaders.ETAG, etag);
            if (matches(request.getHeader(HttpHeaders.IF_NONE_MATCH), etag)) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
        }

        ByteRange range;
        try {
            range = ByteRangeParser.parse(request.getHeader(HttpHeaders.RANGE), size);
        } catch (ByteRangeParser.InvalidRangeException e) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
            response.setContentLength(0);
            return;
        }

        long offset = range == null ? 0 : range.start();
        long length = range == null ? size : range.length();
        boolean head = "HEAD".equalsIgnoreCase(request.getMethod());

        // Open the object before committing any headers so storage errors still produce a clean error response.
        InputStream in = head || length == 0 ? InputStream.nullInputStream() : storage.read(key, offset, length);
        try (in) {
            response.setStatus(range == null ? HttpServletResponse.SC_OK : HttpServletResponse.SC_PARTIAL_CONTENT);
            if (range != null) {
                response.setHeader(HttpHeaders.CONTENT_RANGE, range.contentRange(size));
            }
            response.setContentType(contentType);
            response.setContentLengthLong(length);
            if (head) {
                return;
            }
            copy(in, response, key);
        }
    }

    private static void copy(InputStream in, HttpServletResponse response, String key) throws IOException {
        OutputStream out = response.getOutputStream();
        byte[] buffer = new byte[BUFFER_BYTES];
        while (true) {
            int read;
            try {
                read = in.read(buffer);
            } catch (IOException e) {
                throw new UncheckedIOException("Media storage stream for " + key + " failed", e);
            }
            if (read < 0) {
                break;
            }
            try {
                out.write(buffer, 0, read);
            } catch (IOException e) {
                // The player went away (seek, close); nothing left to do.
                log.debug("Client aborted download of {}", key);
                return;
            }
        }
        try {
            out.flush();
        } catch (IOException e) {
            log.debug("Client aborted download of {}", key);
        }
    }

    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String bare = stripWeak(etag);
        for (String candidate : ifNoneMatch.split(",")) {
            String value = candidate.trim();
            if (value.equals("*") || stripWeak(value).equals(bare)) {
                return true;
            }
        }
        return false;
    }

    private static String stripWeak(String tag) {
        return tag.startsWith("W/") ? tag.substring(2) : tag;
    }
}
