package com.streamx.media.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaFile;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.repository.MediaFileRepository;
import com.streamx.media.storage.ObjectKeys;
import com.streamx.media.support.MediaWebTestBase;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaAdminControllerTest extends MediaWebTestBase {

    private static final String ADMIN = "/api/v1/media/admin";

    @Autowired
    private MediaAssetRepository assetRepository;

    @Autowired
    private MediaFileRepository fileRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MediaAsset saveAsset(MediaProcessingStatus status) {
        MediaAsset asset = new MediaAsset();
        UUID contentId = UUID.randomUUID();
        asset.setContentId(contentId);
        asset.setStatus(status);
        asset.setOriginalFilename("movie.mp4");
        asset.setStoragePath(ObjectKeys.original(contentId, "movie.mp4"));
        return assetRepository.save(asset);
    }

    private ResultActions startImport(String contentId, String url) throws Exception {
        return mockMvc.perform(post(ADMIN + "/imports").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("contentId", contentId, "url", url))));
    }

    @Test
    void assetListExposesSourceUrlAndProgressWhileProcessing() throws Exception {
        MediaAsset processing = saveAsset(MediaProcessingStatus.PROCESSING);
        processing.setProgressPercent(42);
        processing.setSourceUrl("https://cdn.example.com/movie.mp4");
        assetRepository.save(processing);
        MediaAsset failed = saveAsset(MediaProcessingStatus.FAILED);
        failed.setProgressPercent(80);
        assetRepository.save(failed);

        mockMvc.perform(get(ADMIN + "/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.contentId=='" + processing.getContentId() + "')].progressPercent", hasItem(42)))
                .andExpect(jsonPath("$.data[?(@.contentId=='" + processing.getContentId() + "')].sourceUrl",
                        hasItem("https://cdn.example.com/movie.mp4")))
                .andExpect(jsonPath("$.data[?(@.contentId=='" + failed.getContentId() + "')].progressPercent",
                        hasItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void assetByContent() throws Exception {
        MediaAsset asset = saveAsset(MediaProcessingStatus.COMPLETED);
        mockMvc.perform(get(ADMIN + "/assets/by-content/" + asset.getContentId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(asset.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mockMvc.perform(get(ADMIN + "/assets/by-content/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void previewIssuesTwoHourStreamTokenForCompletedVideos() throws Exception {
        MediaAsset processing = saveAsset(MediaProcessingStatus.PROCESSING);
        mockMvc.perform(get(ADMIN + "/assets/" + processing.getContentId() + "/preview").header("X-Account-Id", "admin-7"))
                .andExpect(status().isConflict());
        mockMvc.perform(get(ADMIN + "/assets/" + UUID.randomUUID() + "/preview").header("X-Account-Id", "admin-7"))
                .andExpect(status().isNotFound());

        MediaAsset completed = saveAsset(MediaProcessingStatus.COMPLETED);
        mockMvc.perform(get(ADMIN + "/assets/" + completed.getContentId() + "/preview"))
                .andExpect(status().isUnauthorized());

        String json = mockMvc.perform(get(ADMIN + "/assets/" + completed.getContentId() + "/preview")
                        .header("X-Account-Id", "admin-7"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String streamUrl = objectMapper.readTree(json).path("data").path("streamUrl").asText();
        Matcher matcher = Pattern.compile("^https://api\\.test\\.example/api/v1/media/stream/([^/]+)/"
                + completed.getContentId() + "/master\\.m3u8$").matcher(streamUrl);
        assertTrue(matcher.matches(), streamUrl);

        Claims claims = jwtUtils.parseStreamToken(matcher.group(1), completed.getContentId().toString());
        assertNotNull(claims);
        assertEquals("admin-7", claims.getSubject());
        long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(2 * 60 * 60 * 1000L, ttlMs, 1000);
    }

    @Test
    void deleteRemovesStoredObjectsButNotWhileProcessing() throws Exception {
        MediaAsset busy = saveAsset(MediaProcessingStatus.PROCESSING);
        mockMvc.perform(delete(ADMIN + "/assets/" + busy.getId())).andExpect(status().isConflict());

        MediaAsset done = saveAsset(MediaProcessingStatus.COMPLETED);
        memory.put(done.getStoragePath(), new byte[]{1});
        memory.put(ObjectKeys.hls(done.getContentId(), "master.m3u8"), new byte[]{2});
        memory.put(ObjectKeys.hls(done.getContentId(), "480p_000.ts"), new byte[]{3});

        mockMvc.perform(delete(ADMIN + "/assets/" + done.getId())).andExpect(status().isOk());
        assertFalse(memory.hasPrefix(ObjectKeys.originalsPrefix(done.getContentId())));
        assertFalse(memory.hasPrefix(ObjectKeys.hlsPrefix(done.getContentId())));
        assertTrue(assetRepository.findById(done.getId()).isEmpty());
        mockMvc.perform(delete(ADMIN + "/assets/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void retryRequeuesTranscodeOrImport() throws Exception {
        MediaAsset failed = saveAsset(MediaProcessingStatus.FAILED);
        memory.put(failed.getStoragePath(), new byte[]{1});
        mockMvc.perform(post(ADMIN + "/assets/" + failed.getId() + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
        verify(transcoder).transcode(failed.getId());

        MediaAsset failedImport = saveAsset(MediaProcessingStatus.FAILED);
        failedImport.setStoragePath(null);
        failedImport.setSourceUrl("https://cdn.example.com/movie.mp4");
        assetRepository.save(failedImport);
        mockMvc.perform(post(ADMIN + "/assets/" + failedImport.getId() + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UPLOADING"));
        verify(ingest).importFromLink(failedImport.getId());

        MediaAsset lost = saveAsset(MediaProcessingStatus.FAILED);
        mockMvc.perform(post(ADMIN + "/assets/" + lost.getId() + "/retry")).andExpect(status().isBadRequest());
        MediaAsset busy = saveAsset(MediaProcessingStatus.PROCESSING);
        mockMvc.perform(post(ADMIN + "/assets/" + busy.getId() + "/retry")).andExpect(status().isConflict());
    }

    @Test
    void importRefusesUnsafeLinks() throws Exception {
        String contentId = UUID.randomUUID().toString();
        for (String url : new String[]{"http://localhost/movie.mp4", "http://127.0.0.1/movie.mp4",
                "http://10.1.2.3/movie.mp4", "http://169.254.169.254/latest", "http://minio:9000/streamx-media/x",
                "ftp://cdn.example.com/movie.mp4", "not a url", "http://[::1]/movie.mp4"}) {
            startImport(contentId, url).andExpect(status().isBadRequest());
        }
        startImport("not-a-uuid", "https://cdn.example.com/movie.mp4").andExpect(status().isBadRequest());
        verifyNoInteractions(ingest);
        mockMvc.perform(get(ADMIN + "/assets/by-content/" + contentId)).andExpect(status().isNotFound());
    }

    @Test
    void importQueuesDownload() throws Exception {
        String contentId = UUID.randomUUID().toString();
        String json = startImport(contentId, "https://cdn.example.com/films/The%20Movie.mp4?sig=abc")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://cdn.example.com/films/The%20Movie.mp4?sig=abc"))
                .andExpect(jsonPath("$.data.progressPercent").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        verify(ingest).importFromLink(UUID.fromString(objectMapper.readTree(json).path("data").path("id").asText()));
        startImport(contentId, "https://cdn.example.com/other.mp4").andExpect(status().isConflict());
    }

    @Test
    void filesCanBeListedByPurposeAndDeleted() throws Exception {
        MediaFile trailer = saveFile(UploadPurpose.TRAILER, "teaser.mp4");
        saveFile(UploadPurpose.IMAGE, "poster.jpg");

        mockMvc.perform(get(ADMIN + "/files").param("purpose", "TRAILER").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].purpose", everyItem(is("TRAILER"))))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(trailer.getId().toString())))
                .andExpect(jsonPath("$.data.content[*].url", hasItem(
                        "https://api.test.example/api/v1/media/files/" + trailer.getId() + "/teaser.mp4")));
        mockMvc.perform(get(ADMIN + "/files").param("purpose", "BOGUS")).andExpect(status().isBadRequest());

        mockMvc.perform(delete(ADMIN + "/files/" + trailer.getId())).andExpect(status().isOk());
        assertFalse(memory.objects.containsKey(trailer.getObjectKey()));
        mockMvc.perform(get("/api/v1/media/files/" + trailer.getId() + "/teaser.mp4")).andExpect(status().isNotFound());
        mockMvc.perform(delete(ADMIN + "/files/" + trailer.getId())).andExpect(status().isNotFound());
    }

    @Test
    void statsKeepExistingFields() throws Exception {
        saveAsset(MediaProcessingStatus.COMPLETED);
        mockMvc.perform(get(ADMIN + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAssets").isNumber())
                .andExpect(jsonPath("$.data.countByStatus.COMPLETED").isNumber())
                .andExpect(jsonPath("$.data.totalReadyDurationSeconds").isNumber())
                .andExpect(jsonPath("$.data.totalSourceBytes").isNumber())
                .andExpect(jsonPath("$.data.storageAvailable").value(true));
    }

    private MediaFile saveFile(UploadPurpose purpose, String filename) {
        UUID id = UUID.randomUUID();
        MediaFile file = new MediaFile();
        file.setId(id);
        file.setPurpose(purpose);
        file.setFilename(filename);
        file.setContentType(purpose == UploadPurpose.IMAGE ? "image/jpeg" : "video/mp4");
        file.setSizeBytes(1);
        file.setObjectKey(ObjectKeys.file(id, filename));
        memory.put(file.getObjectKey(), new byte[]{9});
        return fileRepository.save(file);
    }
}
