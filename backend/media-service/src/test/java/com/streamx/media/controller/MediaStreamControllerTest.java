package com.streamx.media.controller;

import com.streamx.common.security.JwtUtils;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaStreamControllerTest {

    private static final String MASTER = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-STREAM-INF:BANDWIDTH=1128751,RESOLUTION=854x480
            480p.m3u8
            """;
    private static final String VARIANT = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.000000,
            480p_000.ts
            #EXT-X-ENDLIST
            """;
    private static final byte[] SEGMENT = {0x47, 0x40, 0x11, 0x10, 0x00, 0x42, 0x47, 0x00};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private StorageService storageService;

    @Autowired
    private MediaAssetRepository repository;

    private UUID contentId;
    private String token;

    @BeforeEach
    void writeHlsOutput() {
        contentId = UUID.randomUUID();
        String dir = "hls/" + contentId + "/";
        storageService.storeFile(dir + "master.m3u8", MASTER.getBytes(StandardCharsets.UTF_8));
        storageService.storeFile(dir + "480p.m3u8", VARIANT.getBytes(StandardCharsets.UTF_8));
        storageService.storeFile(dir + "480p_000.ts", SEGMENT);
        token = jwtUtils.generateStreamToken(UUID.randomUUID().toString(), contentId.toString(),
                UUID.randomUUID().toString(), 60_000);
    }

    private String url(String streamToken, Object content, String file) {
        return "/api/v1/media/stream/" + streamToken + "/" + content + "/" + file;
    }

    @Test
    void servesMasterAndVariantPlaylistsUncached() throws Exception {
        mockMvc.perform(get(url(token, contentId, "master.m3u8")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.apple.mpegurl"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(content().string(MASTER));

        mockMvc.perform(get(url(token, contentId, "480p.m3u8")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.apple.mpegurl"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(content().string(VARIANT));
    }

    @Test
    void servesSegmentsWithPrivateCaching() throws Exception {
        mockMvc.perform(get(url(token, contentId, "480p_000.ts")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp2t"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=3600"))
                .andExpect(content().bytes(SEGMENT));
    }

    @Test
    void supportsByteRanges() throws Exception {
        mockMvc.perform(get(url(token, contentId, "480p_000.ts")).header(HttpHeaders.RANGE, "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes(new byte[]{0x47, 0x40, 0x11, 0x10}));
    }

    @Test
    void rejectsTokenForAnotherContent() throws Exception {
        String other = jwtUtils.generateStreamToken(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 60_000);
        mockMvc.perform(get(url(other, contentId, "master.m3u8")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectsGarbageExpiredAndAccessTokens() throws Exception {
        String expired = jwtUtils.generateStreamToken(UUID.randomUUID().toString(), contentId.toString(),
                UUID.randomUUID().toString(), -1_000);
        String access = jwtUtils.generateAccessToken(UUID.randomUUID().toString(), "a@b.c", List.of("ROLE_USER"));
        for (String bad : new String[]{"not-a-token", expired, access}) {
            mockMvc.perform(get(url(bad, contentId, "master.m3u8")).header(HttpHeaders.ACCEPT, "*/*"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void forbiddenEvenWhenPlayerAcceptsOnlyPlaylists() throws Exception {
        mockMvc.perform(get(url("bad", contentId, "master.m3u8"))
                        .header(HttpHeaders.ACCEPT, "application/vnd.apple.mpegurl"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsafeFileNames() throws Exception {
        for (String name : new String[]{"..master.m3u8", "master.m3u8.bak", "transcode.log", "a%5Cb.ts"}) {
            mockMvc.perform(get(url(token, contentId, name)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void missingFileIs404() throws Exception {
        mockMvc.perform(get(url(token, contentId, "720p.m3u8")))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalStatusReportsProcessingState() throws Exception {
        mockMvc.perform(get("/api/v1/media/internal/" + contentId + "/status"))
                .andExpect(status().isNotFound());

        MediaAsset asset = new MediaAsset();
        asset.setContentId(contentId);
        asset.setStatus(MediaProcessingStatus.COMPLETED);
        asset.setDurationSeconds(5400);
        repository.save(asset);

        mockMvc.perform(get("/api/v1/media/internal/" + contentId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.durationSeconds").value(5400));
    }

    @Test
    void authenticatedHlsEndpointStillServesFiles() throws Exception {
        mockMvc.perform(get("/api/v1/media/" + contentId + "/hls/480p_000.ts"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp2t"))
                .andExpect(content().bytes(SEGMENT));
    }
}
