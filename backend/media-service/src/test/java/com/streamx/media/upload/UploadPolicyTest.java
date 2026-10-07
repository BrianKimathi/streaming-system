package com.streamx.media.upload;

import com.streamx.common.exception.BadRequestException;
import com.streamx.media.config.MediaProperties;
import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.dto.CreateUploadRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadPolicyTest {

    private static final long MIB = 1024L * 1024;
    private static final long CHUNK = 32 * MIB;

    private final UploadPolicy policy = new UploadPolicy(new MediaProperties("ffmpeg", "ffprobe", 2, 240,
            "./target/w", "https://api.example", CHUNK, 20L * 1024 * MIB, 2L * 1024 * MIB, 20 * MIB, 24, 15, 60, null));

    @Test
    void totalPartsIsCeilingOfSizeOverChunk() {
        assertEquals(1, UploadPolicy.totalParts(1, CHUNK));
        assertEquals(1, UploadPolicy.totalParts(CHUNK, CHUNK));
        assertEquals(2, UploadPolicy.totalParts(CHUNK + 1, CHUNK));
        assertEquals(640, UploadPolicy.totalParts(20L * 1024 * MIB, CHUNK));
    }

    @Test
    void everyPartButTheLastIsExactlyOneChunk() {
        long size = 3 * CHUNK + 123;
        assertEquals(CHUNK, UploadPolicy.expectedPartSize(size, CHUNK, 1));
        assertEquals(CHUNK, UploadPolicy.expectedPartSize(size, CHUNK, 3));
        assertEquals(123, UploadPolicy.expectedPartSize(size, CHUNK, 4));
        assertEquals(CHUNK, UploadPolicy.expectedPartSize(2 * CHUNK, CHUNK, 2));
        assertEquals(10, UploadPolicy.expectedPartSize(10, CHUNK, 1));
    }

    @Test
    void partNumbersOutsideTheSessionAreRejected() {
        assertThrows(BadRequestException.class, () -> UploadPolicy.expectedPartSize(10, CHUNK, 0));
        assertThrows(BadRequestException.class, () -> UploadPolicy.expectedPartSize(10, CHUNK, 2));
        assertThrows(BadRequestException.class, () -> UploadPolicy.expectedPartSize(CHUNK + 1, CHUNK, -1));
    }

    @Test
    void missingPartsListsGapsInOrder() {
        assertEquals(List.of(1, 3, 5), UploadPolicy.missingParts(5, List.of(4, 2)));
        assertTrue(UploadPolicy.missingParts(3, List.of(3, 1, 2, 2)).isEmpty());
    }

    @Test
    void videoRequiresContentIdAndVideoFile() {
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("movie.mp4", "video/mp4", 100L, UploadPurpose.VIDEO, null)));
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("movie.mp4", "video/mp4", 100L, UploadPurpose.VIDEO, "not-a-uuid")));
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("notes.txt", "text/plain", 100L, UploadPurpose.VIDEO, UUID.randomUUID().toString())));

        UUID contentId = UUID.randomUUID();
        UploadPolicy.ValidatedUpload upload = policy.validate(
                new CreateUploadRequest("../My Movie (2024).mkv", "", 3 * CHUNK + 1, UploadPurpose.VIDEO, contentId.toString()));
        assertEquals("My_Movie__2024_.mkv", upload.filename());
        assertEquals("video/x-matroska", upload.contentType());
        assertEquals(contentId, upload.contentId());
        assertEquals(4, upload.totalParts());
        assertEquals(CHUNK, upload.chunkSizeBytes());
    }

    @Test
    void sizeLimitsDependOnPurpose() {
        String contentId = UUID.randomUUID().toString();
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("m.mp4", "video/mp4", 20L * 1024 * MIB + 1, UploadPurpose.VIDEO, contentId)));
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("t.mp4", "video/mp4", 2L * 1024 * MIB + 1, UploadPurpose.TRAILER, null)));
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("p.jpg", "image/jpeg", 20 * MIB + 1, UploadPurpose.IMAGE, null)));
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("p.jpg", "image/jpeg", 0L, UploadPurpose.IMAGE, null)));
        assertEquals(20 * MIB, policy.validate(
                new CreateUploadRequest("p.jpg", "image/jpeg", 20 * MIB, UploadPurpose.IMAGE, null)).sizeBytes());
    }

    @Test
    void imagesAndTrailersGetContentTypeFromAllowedExtensions() {
        assertEquals("image/png", policy.validate(
                new CreateUploadRequest("poster.PNG", "application/octet-stream", 10L, UploadPurpose.IMAGE, null)).contentType());
        UploadPolicy.ValidatedUpload webp = policy.validate(
                new CreateUploadRequest("backdrop", "image/webp", 10L, UploadPurpose.IMAGE, null));
        assertEquals("backdrop.webp", webp.filename());
        assertEquals("image/webp", webp.contentType());
        assertEquals("video/quicktime", policy.validate(
                new CreateUploadRequest("teaser.mov", null, 10L, UploadPurpose.TRAILER, null)).contentType());
        assertEquals("video/mp4", policy.validate(
                new CreateUploadRequest("teaser.m4v", "video/x-m4v", 10L, UploadPurpose.TRAILER, null)).contentType());

        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("anim.gif", "image/gif", 10L, UploadPurpose.IMAGE, null)));
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("vector.svg", "image/svg+xml", 10L, UploadPurpose.IMAGE, null)));
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("evil.html", "image/png", 10L, UploadPurpose.IMAGE, null)));
        assertThrows(BadRequestException.class, () -> policy.validate(
                new CreateUploadRequest("trailer.mkv", "video/x-matroska", 10L, UploadPurpose.TRAILER, null)));
    }

    @Test
    void chunkSizeBelowS3MinimumIsRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new MediaProperties("ffmpeg", "ffprobe", 2, 240, "./w", "",
                MIB, 1, 1, 1, 24, 15, 60, null));
    }
}
