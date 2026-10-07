package com.streamx.media.imports;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test server listens on loopback, so the guard used here treats loopback as public; every other private range
 * is still refused, which is what the redirect tests rely on.
 */
class VideoLinkDownloaderTest {

    private static final byte[] VIDEO = new byte[1000];
    private static HttpServer server;
    private static int port;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startServer() throws IOException {
        Arrays.fill(VIDEO, (byte) 7);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/movie.mp4", exchange -> respond(exchange, 200, "video/mp4", VIDEO, true));
        server.createContext("/download", exchange -> {
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"Big Film.mkv\"");
            respond(exchange, 200, "application/octet-stream", VIDEO, true);
        });
        server.createContext("/unlabelled.webm", exchange -> respond(exchange, 200, "application/x-unknown", VIDEO, true));
        server.createContext("/watch", exchange -> respond(exchange, 200, "text/html; charset=utf-8",
                "<html>player</html>".getBytes(StandardCharsets.UTF_8), true));
        server.createContext("/missing.mp4", exchange -> respond(exchange, 404, "text/plain", new byte[0], true));
        server.createContext("/chunked.mp4", exchange -> respond(exchange, 200, "video/mp4", VIDEO, false));
        server.createContext("/relative", exchange -> redirect(exchange, "/movie.mp4"));
        server.createContext("/to-private", exchange ->
                redirect(exchange, "http://internal.example.com:" + port + "/movie.mp4"));
        server.createContext("/to-docker", exchange -> redirect(exchange, "http://minio:9000/streamx-media/x.mp4"));
        server.createContext("/to-metadata", exchange -> redirect(exchange, "http://169.254.169.254/latest/meta-data"));
        server.createContext("/to-ftp", exchange -> redirect(exchange, "ftp://videos.example.com/movie.mp4"));
        server.createContext("/loop/", exchange -> {
            int hop = Integer.parseInt(exchange.getRequestURI().getPath().substring("/loop/".length()));
            redirect(exchange, "/loop/" + (hop + 1));
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body, boolean fixedLength)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, fixedLength ? (body.length == 0 ? -1 : body.length) : 0);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private VideoLinkDownloader downloader() {
        SsrfGuard guard = new SsrfGuard(host -> switch (host) {
            case "videos.example.com" -> new InetAddress[]{InetAddress.getLoopbackAddress()};
            case "internal.example.com" -> new InetAddress[]{InetAddress.getByName("10.0.0.5")};
            default -> {
                if (host.matches("[0-9.]+")) {
                    yield new InetAddress[]{InetAddress.getByName(host)};
                }
                throw new UnknownHostException(host);
            }
        }, address -> !address.isLoopbackAddress() && SsrfGuard.isBlockedAddress(address));
        return new VideoLinkDownloader(guard, Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    private String url(String path) {
        return "http://videos.example.com:" + port + path;
    }

    private String failure(String path, long maxBytes) {
        Path target = tempDir.resolve("download");
        return assertThrows(ImportFailedException.class, () -> downloader().download(url(path), target, maxBytes))
                .getMessage();
    }

    @Test
    void downloadsDirectVideo() throws Exception {
        Path target = tempDir.resolve("download");
        VideoLinkDownloader.DownloadResult result = downloader().download(url("/movie.mp4"), target, 10_000);
        assertEquals(VIDEO.length, result.bytes());
        assertEquals("video/mp4", result.contentType());
        assertEquals("movie.mp4", result.suggestedFilename());
        assertArrayEquals(VIDEO, Files.readAllBytes(target));
    }

    @Test
    void followsRelativeRedirectsOnTheSameHost() throws Exception {
        VideoLinkDownloader.DownloadResult result = downloader().download(url("/relative"), tempDir.resolve("d"), 10_000);
        assertEquals(URI.create(url("/movie.mp4")), result.finalUrl());
    }

    @Test
    void usesContentDispositionNameAndAcceptsGenericBinaryOrVideoExtensions() throws Exception {
        assertEquals("Big Film.mkv",
                downloader().download(url("/download"), tempDir.resolve("a"), 10_000).suggestedFilename());
        assertEquals(VIDEO.length, downloader().download(url("/unlabelled.webm"), tempDir.resolve("b"), 10_000).bytes());
    }

    @Test
    void refusesRedirectsToPrivateAddressesDockerHostsMetadataAndOtherSchemes() {
        assertTrue(failure("/to-private", 10_000).contains("private or internal"));
        assertTrue(failure("/to-docker", 10_000).contains("internal hosts"));
        assertTrue(failure("/to-metadata", 10_000).contains("private or internal"));
        assertTrue(failure("/to-ftp", 10_000).contains("http and https"));
    }

    @Test
    void stopsAfterFiveRedirects() {
        assertTrue(failure("/loop/0", 10_000).contains("more than 5 times"));
    }

    @Test
    void htmlPagesAreNotVideos() {
        assertEquals(VideoLinkDownloader.NOT_A_VIDEO, failure("/watch", 10_000));
    }

    @Test
    void reportsHttpErrors() {
        assertEquals("The link returned HTTP 404", failure("/missing.mp4", 10_000));
    }

    @Test
    void enforcesTheSizeCapWithAndWithoutContentLength() {
        assertTrue(failure("/movie.mp4", 999).contains("larger than"));
        assertTrue(failure("/chunked.mp4", 999).contains("larger than"));
    }

    @Test
    void refusesInitialLinksToInternalHosts() {
        Path target = tempDir.resolve("x");
        assertThrows(ImportFailedException.class, () -> downloader().download("http://localhost:" + port + "/movie.mp4", target, 10));
        assertThrows(ImportFailedException.class, () -> downloader().download("http://internal.example.com/movie.mp4", target, 10));
        assertFalse(Files.exists(target));
    }

    @Test
    void videoDetectionRules() {
        assertTrue(VideoLinkDownloader.isDirectVideo("video/mp4", "/x"));
        assertTrue(VideoLinkDownloader.isDirectVideo("application/octet-stream", "/x"));
        assertTrue(VideoLinkDownloader.isDirectVideo(null, "/films/a.MKV"));
        assertFalse(VideoLinkDownloader.isDirectVideo("text/html", "/a.mp4"));
        assertFalse(VideoLinkDownloader.isDirectVideo("application/vnd.apple.mpegurl", "/master.m3u8"));
        assertFalse(VideoLinkDownloader.isDirectVideo(null, "/watch"));
    }
}
