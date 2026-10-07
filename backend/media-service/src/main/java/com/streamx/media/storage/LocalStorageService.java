package com.streamx.media.storage;

import com.streamx.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class LocalStorageService implements StorageService {

    private final Path rootPath;

    public LocalStorageService(@Value("${media.storage-path:./media-storage}") String storageDir) {
        this.rootPath = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    @Override
    public void storeFile(String relativePath, byte[] content) {
        try {
            Path targetPath = rootPath.resolve(relativePath).normalize();
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file at: " + relativePath, e);
        }
    }

    @Override
    public byte[] readFile(String relativePath) {
        try {
            Path targetPath = rootPath.resolve(relativePath).normalize();
            if (!Files.exists(targetPath)) {
                throw new ResourceNotFoundException("File not found at: " + relativePath);
            }
            return Files.readAllBytes(targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file at: " + relativePath, e);
        }
    }

    @Override
    public boolean exists(String relativePath) {
        Path targetPath = rootPath.resolve(relativePath).normalize();
        return Files.exists(targetPath);
    }
}
