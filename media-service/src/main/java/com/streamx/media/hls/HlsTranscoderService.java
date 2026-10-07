package com.streamx.media.hls;

import com.streamx.media.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class HlsTranscoderService {

    private static final Logger log = LoggerFactory.getLogger(HlsTranscoderService.class);

    private final StorageService storageService;

    public HlsTranscoderService(StorageService storageService) {
        this.storageService = storageService;
    }

    public String generateHlsStream(String contentId) {
        log.info("Generating HLS multi-bitrate playlists for contentId: {}", contentId);

        String basePath = "hls/" + contentId + "/";

        // Master Playlist content
        String masterPlaylist = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480
                480p.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=1280x720
                720p.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1920x1080
                1080p.m3u8
                """;

        // 720p Variant Playlist
        String playlist720p = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:10
                #EXT-X-MEDIA-SEQUENCE:0
                #EXTINF:10.0,
                segment_0.ts
                #EXTINF:10.0,
                segment_1.ts
                #EXT-X-ENDLIST
                """;

        // Dummy Segment byte arrays
        byte[] dummySegment = "STREAMX_HLS_VIDEO_SEGMENT_DATA".getBytes(StandardCharsets.UTF_8);

        storageService.storeFile(basePath + "master.m3u8", masterPlaylist.getBytes(StandardCharsets.UTF_8));
        storageService.storeFile(basePath + "720p.m3u8", playlist720p.getBytes(StandardCharsets.UTF_8));
        storageService.storeFile(basePath + "1080p.m3u8", playlist720p.getBytes(StandardCharsets.UTF_8));
        storageService.storeFile(basePath + "480p.m3u8", playlist720p.getBytes(StandardCharsets.UTF_8));
        storageService.storeFile(basePath + "segment_0.ts", dummySegment);
        storageService.storeFile(basePath + "segment_1.ts", dummySegment);

        return "/api/v1/media/" + contentId + "/hls/master.m3u8";
    }
}
