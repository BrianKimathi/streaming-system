package com.streamx.media.storage;

import java.io.InputStream;
import java.nio.file.Path;

public interface StorageService {
    void storeFile(String path, byte[] content);
    void storeStream(String path, InputStream content);
    byte[] readFile(String path);
    boolean exists(String path);
    Path resolve(String path);
    void deleteRecursively(String path);
}
