package com.streamx.media.storage;

public interface StorageService {
    void storeFile(String path, byte[] content);
    byte[] readFile(String path);
    boolean exists(String path);
}
