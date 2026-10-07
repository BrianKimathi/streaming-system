package com.streamx.media.storage;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.media.config.MediaProperties;
import com.streamx.media.exception.StorageUnavailableException;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);
    private static final Set<String> NOT_FOUND_CODES = Set.of("NoSuchKey", "NoSuchObject", "NotFound");
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int DELETE_BATCH = 1000;

    private final MinioClient client;
    private final String bucket;
    private final ApplicationEventPublisher events;
    private final AtomicBoolean ready = new AtomicBoolean();
    private volatile Thread initThread;

    public MinioObjectStorage(MediaProperties properties, ApplicationEventPublisher events) {
        MediaProperties.Minio minio = properties.minio();
        this.bucket = minio.bucket() == null || minio.bucket().isBlank() ? "streamx-media" : minio.bucket().trim();
        this.events = events;
        if (minio.isConfigured()) {
            MinioClient.Builder builder = MinioClient.builder()
                    .endpoint(minio.endpoint().trim())
                    .credentials(minio.accessKey(), minio.secretKey());
            if (minio.region() != null && !minio.region().isBlank()) {
                builder.region(minio.region().trim());
            }
            this.client = builder.build();
            this.client.setTimeout(TimeUnit.SECONDS.toMillis(10), TimeUnit.MINUTES.toMillis(10), TimeUnit.MINUTES.toMillis(10));
        } else {
            this.client = null;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startBucketInitialisation() {
        if (client == null) {
            log.error("MinIO is not configured (MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY); media storage stays unavailable");
            return;
        }
        initThread = Thread.ofPlatform().daemon().name("minio-init").start(this::initialiseUntilReady);
    }

    private void initialiseUntilReady() {
        long delayMs = 2_000;
        for (int attempt = 1; !Thread.currentThread().isInterrupted(); attempt++) {
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("Created media bucket '{}'", bucket);
                }
                ready.set(true);
                log.info("Media storage ready (bucket '{}')", bucket);
                events.publishEvent(new StorageReadyEvent(bucket));
                return;
            } catch (Exception e) {
                if (attempt == 1 || attempt % 10 == 0) {
                    log.warn("MinIO not reachable yet (attempt {}), retrying in background: {}", attempt, e.toString());
                }
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            delayMs = Math.min(delayMs * 2, 30_000);
        }
    }

    @PreDestroy
    public void stop() {
        Thread thread = initThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isAvailable() {
        return ready.get();
    }

    @Override
    public Optional<ObjectStat> stat(String key) {
        requireReady();
        try {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return Optional.of(new ObjectStat(key, response.size(), response.etag(),
                    response.lastModified() == null ? null : response.lastModified().toInstant()));
        } catch (ErrorResponseException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            throw failure("stat " + key, e);
        } catch (Exception e) {
            throw failure("stat " + key, e);
        }
    }

    @Override
    public InputStream read(String key, long offset, long length) {
        requireReady();
        GetObjectArgs.Builder args = GetObjectArgs.builder().bucket(bucket).object(key);
        if (length >= 0) {
            args.offset(offset).length(length);
        } else if (offset > 0) {
            args.offset(offset);
        }
        try {
            return client.getObject(args.build());
        } catch (ErrorResponseException e) {
            if (isNotFound(e)) {
                throw new ResourceNotFoundException("File not found");
            }
            throw failure("read " + key, e);
        } catch (Exception e) {
            throw failure("read " + key, e);
        }
    }

    /*
     * The SDK's putObject buffers each part of an InputStream in memory, so request bodies are streamed through a
     * short-lived presigned PUT URL with a fixed-length body instead.
     */
    @Override
    public void write(String key, InputStream content, long size, String contentType) {
        requireReady();
        String url;
        try {
            url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT).bucket(bucket).object(key).expiry(15, TimeUnit.MINUTES).build());
        } catch (Exception e) {
            throw failure("presign " + key, e);
        }

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection(Proxy.NO_PROXY);
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(size);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(120_000);
            connection.setRequestProperty("Content-Type", contentType == null ? "application/octet-stream" : contentType);
        } catch (IOException e) {
            throw failure("write " + key, e);
        }

        try {
            try (OutputStream out = connection.getOutputStream()) {
                copyExactly(content, out, size);
            }
            int status = connection.getResponseCode();
            if (status / 100 != 2) {
                throw new StorageUnavailableException("Media storage rejected the upload (HTTP " + status + "): "
                        + errorSnippet(connection));
            }
        } catch (ClientBodyException e) {
            throw new BadRequestException(e.getMessage());
        } catch (IOException e) {
            throw failure("write " + key, e);
        } finally {
            connection.disconnect();
        }
    }

    private static void copyExactly(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        long remaining = size;
        while (remaining > 0) {
            int read;
            try {
                read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            } catch (IOException e) {
                throw new ClientBodyException("The upload was interrupted before the part was fully received");
            }
            if (read < 0) {
                throw new ClientBodyException("Request body ended after " + (size - remaining) + " of " + size + " bytes");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static String errorSnippet(HttpURLConnection connection) {
        try (InputStream error = connection.getErrorStream()) {
            if (error == null) {
                return "(no body)";
            }
            String body = new String(error.readNBytes(500), StandardCharsets.UTF_8);
            return body.isBlank() ? "(no body)" : body;
        } catch (IOException e) {
            return "(unreadable body)";
        }
    }

    @Override
    public void uploadFile(String key, Path file, String contentType) {
        requireReady();
        try {
            UploadObjectArgs.Builder args = UploadObjectArgs.builder().bucket(bucket).object(key).filename(file.toString());
            if (contentType != null) {
                args.contentType(contentType);
            }
            client.uploadObject(args.build());
        } catch (Exception e) {
            throw failure("upload " + key, e);
        }
    }

    @Override
    public void downloadToFile(String key, Path target) {
        try (InputStream in = read(key, 0, -1)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw failure("download " + key, e);
        }
    }

    @Override
    public void compose(String targetKey, List<String> sourceKeys, String contentType) {
        requireReady();
        if (sourceKeys.isEmpty()) {
            throw new IllegalArgumentException("Nothing to compose");
        }
        Map<String, String> headers = contentType == null ? Map.of() : Map.of("Content-Type", contentType);
        try {
            if (sourceKeys.size() == 1) {
                client.copyObject(CopyObjectArgs.builder()
                        .bucket(bucket).object(targetKey)
                        .source(CopySource.builder().bucket(bucket).object(sourceKeys.get(0)).build())
                        .build());
                return;
            }
            List<ComposeSource> sources = new ArrayList<>(sourceKeys.size());
            for (String source : sourceKeys) {
                sources.add(ComposeSource.builder().bucket(bucket).object(source).build());
            }
            client.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket).object(targetKey).sources(sources).headers(headers).build());
        } catch (Exception e) {
            throw failure("compose " + targetKey, e);
        }
    }

    @Override
    public List<ObjectStat> list(String prefix) {
        requireReady();
        List<ObjectStat> objects = new ArrayList<>();
        try {
            for (Result<Item> result : client.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build())) {
                Item item = result.get();
                if (!item.isDir()) {
                    objects.add(new ObjectStat(item.objectName(), item.size(), item.etag(),
                            item.lastModified() == null ? null : item.lastModified().toInstant()));
                }
            }
            return objects;
        } catch (Exception e) {
            throw failure("list " + prefix, e);
        }
    }

    @Override
    public void delete(String key) {
        requireReady();
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw failure("delete " + key, e);
        }
    }

    @Override
    public void deletePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || !prefix.endsWith("/")) {
            throw new IllegalArgumentException("Refusing to delete an unscoped prefix: " + prefix);
        }
        List<ObjectStat> objects = list(prefix);
        try {
            for (int from = 0; from < objects.size(); from += DELETE_BATCH) {
                List<DeleteObject> batch = objects.subList(from, Math.min(objects.size(), from + DELETE_BATCH)).stream()
                        .map(object -> new DeleteObject(object.key()))
                        .toList();
                // The SDK sends the request lazily while the results are iterated.
                for (Result<DeleteError> result : client.removeObjects(
                        RemoveObjectsArgs.builder().bucket(bucket).objects(batch).build())) {
                    DeleteError error = result.get();
                    log.warn("Could not delete {}: {}", error.objectName(), error.message());
                }
            }
        } catch (Exception e) {
            throw failure("delete " + prefix, e);
        }
    }

    private void requireReady() {
        if (client == null) {
            throw new StorageUnavailableException("Media storage is not configured");
        }
        if (!ready.get()) {
            throw new StorageUnavailableException("Media storage is unavailable; try again shortly");
        }
    }

    private static boolean isNotFound(ErrorResponseException e) {
        return (e.errorResponse() != null && NOT_FOUND_CODES.contains(e.errorResponse().code()))
                || (e.response() != null && e.response().code() == 404);
    }

    private static StorageUnavailableException failure(String operation, Exception e) {
        if (e instanceof StorageUnavailableException unavailable) {
            return unavailable;
        }
        log.warn("Media storage operation failed ({}): {}", operation, e.toString());
        String detail = e instanceof ErrorResponseException error && error.errorResponse() != null
                ? error.errorResponse().code()
                : e.getClass().getSimpleName();
        return new StorageUnavailableException("Media storage request failed (" + detail + ")", e);
    }

    private static final class ClientBodyException extends IOException {
        ClientBodyException(String message) {
            super(message);
        }
    }
}
