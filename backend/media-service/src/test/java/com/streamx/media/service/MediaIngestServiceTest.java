package com.streamx.media.service;

import com.streamx.media.config.MediaProperties;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.domain.UploadSession;
import com.streamx.media.hls.HlsTranscoderService;
import com.streamx.media.imports.VideoLinkDownloader;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.repository.UploadPartRepository;
import com.streamx.media.repository.UploadSessionRepository;
import com.streamx.media.storage.ObjectKeys;
import com.streamx.media.storage.ObjectStorage;
import com.streamx.media.storage.ScratchSpace;
import com.streamx.media.support.InMemoryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaIngestServiceTest {

    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final UploadSessionRepository sessions = mock(UploadSessionRepository.class);
    private final UploadPartRepository parts = mock(UploadPartRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final HlsTranscoderService transcoder = mock(HlsTranscoderService.class);
    private final AssetStatusUpdater statusUpdater = mock(AssetStatusUpdater.class);
    private InMemoryStorage memory;
    private MediaIngestService service;

    private final UUID contentId = UUID.randomUUID();
    private final UUID uploadId = UUID.randomUUID();
    private MediaAsset asset;
    private UploadSession session;

    @BeforeEach
    void setUp() {
        memory = InMemoryStorage.attach(storage);
        MediaProperties properties = new MediaProperties("ffmpeg", "ffprobe", 1, 10, "./target/ingest-test", "",
                32L * 1024 * 1024, 1000, 1000, 1000, 24, 15, 60, null);
        service = new MediaIngestService(assets, sessions, parts, storage, new ScratchSpace(properties),
                mock(VideoLinkDownloader.class), transcoder, statusUpdater, properties);

        asset = new MediaAsset();
        asset.setId(UUID.randomUUID());
        asset.setContentId(contentId);
        asset.setStatus(MediaProcessingStatus.PROCESSING);
        asset.setPendingUploadId(uploadId);
        asset.setStoragePath(ObjectKeys.original(contentId, "new.mp4"));
        when(assets.findById(asset.getId())).thenReturn(Optional.of(asset));

        session = new UploadSession();
        session.setId(uploadId);
        session.setPurpose(UploadPurpose.VIDEO);
        session.setContentType("video/mp4");
        session.setTotalParts(3);
        session.setSizeBytes(9);
        when(sessions.findById(uploadId)).thenReturn(Optional.of(session));
    }

    @Test
    void assemblesPartsReplacesOldOriginalAndQueuesTranscode() {
        memory.put(ObjectKeys.uploadPart(uploadId, 1), new byte[]{1, 2, 3});
        memory.put(ObjectKeys.uploadPart(uploadId, 2), new byte[]{4, 5, 6});
        memory.put(ObjectKeys.uploadPart(uploadId, 3), new byte[]{7, 8, 9});
        memory.put(ObjectKeys.original(contentId, "old.mov"), new byte[]{0});

        service.assembleUpload(asset.getId());

        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9}, memory.objects.get(asset.getStoragePath()));
        assertFalse(memory.hasPrefix(ObjectKeys.uploadPrefix(uploadId)));
        assertFalse(memory.objects.containsKey(ObjectKeys.original(contentId, "old.mov")));
        verify(parts).deleteByUploadId(uploadId);
        verify(assets).clearPendingUpload(asset.getId());
        verify(transcoder).transcode(asset.getId());
    }

    @Test
    void resumesWhenPartsWereAlreadyComposedBeforeARestart() {
        memory.put(asset.getStoragePath(), new byte[9]);

        service.assembleUpload(asset.getId());

        verify(storage, never()).compose(any(), any(), any());
        verify(transcoder).transcode(asset.getId());
    }

    @Test
    void failsWhenPartsAreGoneAndNothingWasAssembled() {
        memory.put(ObjectKeys.uploadPart(uploadId, 1), new byte[]{1, 2, 3});

        service.assembleUpload(asset.getId());

        verify(statusUpdater).markFailed(eq(asset.getId()), contains("incomplete"));
        verify(transcoder, never()).transcode(any());
        assertTrue(memory.hasPrefix(ObjectKeys.uploadPrefix(uploadId)));
    }

    @Test
    void ignoresAssetsThatAreNotAwaitingAssembly() {
        asset.setPendingUploadId(null);
        service.assembleUpload(asset.getId());
        verify(storage, never()).list(any());
    }

    @Test
    void importedFilenamesKeepVideoExtensionsOrGetOneFromTheContentType() {
        assertEquals("Big_Film.mkv", MediaIngestService.importedFilename(
                new VideoLinkDownloader.DownloadResult(URI.create("https://x.example.com/a"), "application/octet-stream", 1, "Big Film.mkv")));
        assertEquals("watch.webm", MediaIngestService.importedFilename(
                new VideoLinkDownloader.DownloadResult(URI.create("https://x.example.com/watch"), "video/webm", 1, "watch")));
        assertEquals("video.mp4", MediaIngestService.importedFilename(
                new VideoLinkDownloader.DownloadResult(URI.create("https://x.example.com/"), "video/mp4", 1, "")));
    }
}
