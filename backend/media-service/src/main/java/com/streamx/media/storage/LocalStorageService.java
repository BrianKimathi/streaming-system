package com.streamx.media.storage;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

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
    public Path resolve(String relativePath) {
        Path targetPath = rootPath.resolve(relativePath).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new BadRequestException("Invalid storage path");
        }
        return targetPath;
    }

    @Override
    public void storeFile(String relativePath, byte[] content) {
        try {
            Path targetPath = resolve(relativePath);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file at: " + relativePath, e);
        }
    }

    @Override
    public void storeStream(String relativePath, InputStream content) {
        try (content) {
            Path targetPath = resolve(relativePath);
            Files.createDirectories(targetPath.getParent());
            Files.copy(content, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file at: " + relativePath, e);
        }
    }

    @Override
    public byte[] readFile(String relativePath) {
        try {
            Path targetPath = resolve(relativePath);
            if (!Files.isRegularFile(targetPath)) {
                throw new ResourceNotFoundException("File not found at: " + relativePath);
            }
            return Files.readAllBytes(targetPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file at: " + relativePath, e);
        }
    }

    @Override
    public boolean exists(String relativePath) {
        return Files.exists(resolve(relativePath));
    }

    @Override
    public void deleteRecursively(String relativePath) {
        Path target = resolve(relativePath);
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete: " + relativePath, e);
        }
    }
}
