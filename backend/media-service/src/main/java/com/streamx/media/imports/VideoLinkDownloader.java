package com.streamx.media.imports;

import com.streamx.media.config.MediaProperties;
import com.streamx.media.upload.Filenames;
import com.streamx.media.upload.UploadPolicy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads a direct video link to disk, re-checking every redirect hop against the SSRF guard. */
@Component
public class VideoLinkDownloader {

    public static final int MAX_REDIRECTS = 5;
    public static final String NOT_A_VIDEO = "The link must point directly to a video file";

    private static final Pattern DISPOSITION_FILENAME =
            Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    public record DownloadResult(URI finalUrl, String contentType, long bytes, String suggestedFilename) {
    }

    private final SsrfGuard guard;
    private final OkHttpClient client;

    @Autowired
    public VideoLinkDownloader(SsrfGuard guard, MediaProperties properties) {
        this(guard, Duration.ofSeconds(properties.importConnectTimeoutSeconds()),
                Duration.ofSeconds(properties.importReadTimeoutSeconds()));
    }

    public VideoLinkDownloader(SsrfGuard guard, Duration connectTimeout, Duration readTimeout) {
        this.guard = guard;
        this.client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .proxy(Proxy.NO_PROXY)
                // Connections only ever go to addresses that passed the guard, which also defeats DNS rebinding.
                .dns(hostname -> {
                    try {
                        return guard.resolveAllowed(hostname);
                    } catch (SsrfGuard.UnsafeUrlException e) {
                        throw new BlockedHostException(e.getMessage());
                    }
                })
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .writeTimeout(readTimeout)
                .build();
    }

    public DownloadResult download(String url, Path target, long maxBytes) throws ImportFailedException {
        URI current = check(url);
        for (int redirects = 0; ; redirects++) {
            Request request = new Request.Builder()
                    .url(current.toString())
                    .header("User-Agent", "StreamX-Media-Importer/1.0")
                    .header("Accept-Encoding", "identity")
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                if (response.isRedirect()) {
                    if (redirects >= MAX_REDIRECTS) {
                        throw new ImportFailedException("The link redirected more than " + MAX_REDIRECTS + " times");
                    }
                    String location = response.header("Location");
                    if (location == null || location.isBlank()) {
                        throw new ImportFailedException("The link redirected without a destination");
                    }
                    current = check(resolve(current, location));
                    continue;
                }
                if (code != 200) {
                    throw new ImportFailedException("The link returned HTTP " + code);
                }
                String contentType = response.header("Content-Type");
                if (!isDirectVideo(contentType, current.getPath())) {
                    throw new ImportFailedException(NOT_A_VIDEO);
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new ImportFailedException("The link returned an empty response");
                }
                long declared = body.contentLength();
                if (declared > maxBytes) {
                    throw new ImportFailedException(tooLarge(maxBytes));
                }
                long bytes = copyCapped(body.byteStream(), target, maxBytes);
                if (bytes == 0) {
                    throw new ImportFailedException("The link returned an empty file");
                }
                return new DownloadResult(current, Filenames.normalizeContentType(contentType), bytes,
                        suggestedFilename(response.header("Content-Disposition"), current));
            } catch (ImportFailedException e) {
                throw e;
            } catch (BlockedHostException e) {
                throw new ImportFailedException(e.getMessage());
            } catch (InterruptedIOException e) {
                throw new ImportFailedException("Timed out while downloading the link", e);
            } catch (UnknownHostException e) {
                throw new ImportFailedException("The link's host could not be resolved", e);
            } catch (IOException e) {
                throw new ImportFailedException("Could not download the link: " + e.getMessage(), e);
            }
        }
    }

    private URI check(String url) throws ImportFailedException {
        try {
            URI uri = guard.checkUrl(url);
            guard.resolveAllowed(uri.getHost());
            return uri;
        } catch (SsrfGuard.UnsafeUrlException e) {
            throw new ImportFailedException(e.getMessage());
        }
    }

    private static String resolve(URI base, String location) throws ImportFailedException {
        try {
            return base.resolve(location.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new ImportFailedException("The link redirected to an invalid URL");
        }
    }

    private static long copyCapped(InputStream in, Path target, long maxBytes) throws IOException, ImportFailedException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (in; OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new ImportFailedException(tooLarge(maxBytes));
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static String tooLarge(long maxBytes) {
        return "The file is larger than the " + (maxBytes / (1024 * 1024)) + " MiB video limit";
    }

    /** HTML pages (YouTube, Drive previews...) are refused; servers that label videos generically are accepted. */
    public static boolean isDirectVideo(String contentType, String path) {
        String type = Filenames.normalizeContentType(contentType);
        if (type.startsWith("video/") || type.equals("application/octet-stream") || type.equals("binary/octet-stream")) {
            return true;
        }
        if (type.startsWith("text/") || type.contains("html") || type.contains("json") || type.contains("xml")
                || type.contains("mpegurl") || type.startsWith("image/") || type.startsWith("audio/")) {
            return false;
        }
        return UploadPolicy.VIDEO_EXTENSIONS.contains(Filenames.extension(path == null ? "" : path));
    }

    static String suggestedFilename(String contentDisposition, URI url) {
        if (contentDisposition != null) {
            Matcher matcher = DISPOSITION_FILENAME.matcher(contentDisposition);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        String path = url.getPath() == null ? "" : url.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static final class BlockedHostException extends UnknownHostException {
        BlockedHostException(String message) {
            super(message);
        }
    }
}
