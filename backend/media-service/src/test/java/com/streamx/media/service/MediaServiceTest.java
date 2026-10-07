package com.streamx.media.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.dto.MediaAssetResponse;
import com.streamx.media.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MediaServiceTest {

    @Autowired
    private MediaService mediaService;

    @Autowired
    private StorageService storageService;

    @Test
    void uploadStoresSourceAndQueuesTranscode() {
        String contentId = UUID.randomUUID().toString();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../movie sample.mp4",
                "video/mp4",
                "NOT_A_REAL_VIDEO".getBytes(StandardCharsets.UTF_8)
        );

        MediaAssetResponse response = mediaService.processUpload(contentId, file);

        assertNotNull(response.getId());
        assertEquals(contentId, response.getContentId());
        assertEquals("movie_sample.mp4", response.getOriginalFilename());
        assertEquals(MediaProcessingStatus.PROCESSING, response.getStatus());
        assertNull(response.getMasterPlaylistUrl());
        assertNull(response.getDurationSeconds());
        assertTrue(storageService.exists("raw/" + contentId + "/movie_sample.mp4"));

        assertEquals(MediaProcessingStatus.PROCESSING, mediaService.getStatus(contentId).orElseThrow().status());
    }

    @Test
    void rejectsNonVideoUploads() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        assertThrows(BadRequestException.class, () -> mediaService.processUpload(UUID.randomUUID().toString(), file));
    }

    @Test
    void rejectsPathTraversalInHlsFilename() {
        String contentId = UUID.randomUUID().toString();
        for (String name : new String[]{"../../etc/passwd", "..\\..\\secret.ts", "a/b.ts", "..ts", "...m3u8",
                "master.m3u8.bak", "run.sh", "", "seg ment.ts"}) {
            assertThrows(BadRequestException.class, () -> mediaService.resolveHlsFile(contentId, name), name);
        }
        assertThrows(BadRequestException.class, () -> mediaService.resolveHlsFile(contentId, null));
    }

    @Test
    void resolvesExistingHlsFilesOnly() {
        UUID contentId = UUID.randomUUID();
        storageService.storeFile("hls/" + contentId + "/720p_000.ts", new byte[]{1, 2, 3});

        Path file = mediaService.resolveHlsFile(contentId.toString(), "720p_000.ts");
        assertTrue(file.endsWith(Path.of("hls", contentId.toString(), "720p_000.ts")));
        assertThrows(ResourceNotFoundException.class, () -> mediaService.resolveHlsFile(contentId.toString(), "720p_001.ts"));
    }

    @Test
    void statusIsEmptyForUnknownContent() {
        assertTrue(mediaService.getStatus(UUID.randomUUID().toString()).isEmpty());
        assertTrue(mediaService.getStatus("not-a-uuid").isEmpty());
    }

    @Test
    void acceptsFfmpegOutputNames() {
        for (String name : new String[]{"master.m3u8", "480p.m3u8", "1080p.m3u8", "480p_000.ts", "720p_123.ts"}) {
            assertTrue(MediaService.isValidHlsFilename(name), name);
        }
    }
}
