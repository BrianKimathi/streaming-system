package com.streamx.media.service;

import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.dto.MediaAssetResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MediaServiceTest {

    @Autowired
    private MediaService mediaService;

    @Test
    void testUploadAndHlsGeneration() {
        String contentId = UUID.randomUUID().toString();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "movie_sample.mp4",
                "video/mp4",
                "SAMPLE_VIDEO_CONTENT_BYTES".getBytes(StandardCharsets.UTF_8)
        );

        MediaAssetResponse response = mediaService.processUpload(contentId, file);

        assertNotNull(response.getId());
        assertEquals(contentId, response.getContentId());
        assertEquals("movie_sample.mp4", response.getOriginalFilename());
        assertEquals(MediaProcessingStatus.COMPLETED, response.getStatus());
        assertTrue(response.getMasterPlaylistUrl().contains("master.m3u8"));

        byte[] masterBytes = mediaService.getHlsFile(contentId, "master.m3u8");
        assertNotNull(masterBytes);
        assertTrue(new String(masterBytes, StandardCharsets.UTF_8).contains("#EXTM3U"));
    }
}
