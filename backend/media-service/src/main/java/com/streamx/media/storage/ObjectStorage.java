package com.streamx.media.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Persistent media storage (one private bucket). Every method throws
 * {@link com.streamx.media.exception.StorageUnavailableException} while the backing store is unreachable.
 */
public interface ObjectStorage {

    boolean isAvailable();

    Optional<ObjectStat> stat(String key);

    /** Opens the object (or a byte range of it when {@code length >= 0}); throws ResourceNotFoundException if absent. */
    InputStream read(String key, long offset, long length);

    /** Streams exactly {@code size} bytes from {@code content} into the object without buffering it in memory. */
    void write(String key, InputStream content, long size, String contentType);

    void uploadFile(String key, Path file, String contentType);

    void downloadToFile(String key, Path target);

    /** Concatenates the sources (in order) into {@code targetKey} server-side. */
    void compose(String targetKey, List<String> sourceKeys, String contentType);

    List<ObjectStat> list(String prefix);

    void delete(String key);

    void deletePrefix(String prefix);
}
