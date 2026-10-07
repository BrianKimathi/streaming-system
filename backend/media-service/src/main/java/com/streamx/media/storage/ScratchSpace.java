package com.streamx.media.storage;

import com.streamx.media.config.MediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/** Temporary local disk (MEDIA_WORK_DIR) for downloads and ffmpeg; nothing here is persistent. */
@Component
public class ScratchSpace {

    private static final Logger log = LoggerFactory.getLogger(ScratchSpace.class);

    private final Path root;

    public ScratchSpace(MediaProperties properties) {
        String dir = properties.workDir() == null || properties.workDir().isBlank() ? "./media-work" : properties.workDir();
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    /** Returns an empty directory for one job, wiping leftovers from an interrupted earlier run. */
    public Path freshDirectory(String kind, UUID id) throws IOException {
        Path dir = root.resolve(kind).resolve(id.toString());
        deleteQuietly(dir);
        Files.createDirectories(dir);
        return dir;
    }

    public void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Could not clean scratch directory {}: {}", dir, e.toString());
        }
    }
}
