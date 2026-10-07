package com.streamx.media.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.media.domain.UploadSessionStatus;
import com.streamx.media.exception.StorageUnavailableException;
import com.streamx.media.repository.UploadSessionRepository;
import com.streamx.media.service.UploadSessionService;
import com.streamx.media.storage.ObjectKeys;
import com.streamx.media.support.MediaWebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadFlowTest extends MediaWebTestBase {

    private static final String UPLOADS = "/api/v1/media/admin/uploads";
    private static final long CHUNK = 32L * 1024 * 1024;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UploadSessionService uploadSessionService;

    @Autowired
    private UploadSessionRepository sessionRepository;

    private ResultActions create(String filename, String contentType, long size, String purpose, String contentId)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("filename", filename);
        request.put("contentType", contentType);
        request.put("sizeBytes", size);
        request.put("purpose", purpose);
        request.put("contentId", contentId);
        String body = objectMapper.writeValueAsString(request);
        return mockMvc.perform(post(UPLOADS).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Account-Id", "admin-1"));
    }

    private String createId(String filename, String contentType, long size, String purpose, String contentId)
            throws Exception {
        String json = create(filename, contentType, size, purpose, contentId)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return node.path("data").path("uploadId").asText();
    }

    private ResultActions putPart(String uploadId, int part, byte[] body) throws Exception {
        return mockMvc.perform(put(UPLOADS + "/" + uploadId + "/parts/" + part)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(body));
    }

    @Test
    void imageUploadLifecycleCreatesPublicFile() throws Exception {
        byte[] png = "0123456789".getBytes(StandardCharsets.US_ASCII);
        create("poster.png", "image/png", png.length, "IMAGE", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purpose").value("IMAGE"))
                .andExpect(jsonPath("$.data.chunkSizeBytes").value(CHUNK))
                .andExpect(jsonPath("$.data.totalParts").value(1))
                .andExpect(jsonPath("$.data.receivedParts", empty()))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        String uploadId = createId("poster.png", "image/png", png.length, "IMAGE", null);
        putPart(uploadId, 1, png)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partNumber").value(1))
                .andExpect(jsonPath("$.data.sizeBytes").value(10))
                .andExpect(jsonPath("$.data.receivedParts", contains(1)));
        assertArrayEquals(png, memory.objects.get(ObjectKeys.uploadPart(UUID.fromString(uploadId), 1)));

        mockMvc.perform(get(UPLOADS + "/" + uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receivedParts", contains(1)));

        String fileUrl = "https://api.test.example/api/v1/media/files/" + uploadId + "/poster.png";
        mockMvc.perform(post(UPLOADS + "/" + uploadId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("IMAGE"))
                .andExpect(jsonPath("$.data.asset").value(nullValue()))
                .andExpect(jsonPath("$.data.file.id").value(uploadId))
                .andExpect(jsonPath("$.data.file.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.file.sizeBytes").value(10))
                .andExpect(jsonPath("$.data.file.url").value(fileUrl));

        assertArrayEquals(png, memory.objects.get("files/" + uploadId + "/poster.png"));
        assertFalse(memory.hasPrefix(ObjectKeys.uploadPrefix(UUID.fromString(uploadId))));

        mockMvc.perform(get("/api/v1/media/files/" + uploadId + "/poster.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));

        putPart(uploadId, 1, png).andExpect(status().isConflict());
        mockMvc.perform(post(UPLOADS + "/" + uploadId + "/complete")).andExpect(status().isConflict());
        mockMvc.perform(delete(UPLOADS + "/" + uploadId)).andExpect(status().isConflict());
    }

    @Test
    void partsMustMatchTheChunkLayout() throws Exception {
        String uploadId = createId("trailer.mp4", "video/mp4", CHUNK + 5, "TRAILER", null);
        putPart(uploadId, 2, new byte[4]).andExpect(status().isBadRequest());
        putPart(uploadId, 2, new byte[6]).andExpect(status().isBadRequest());
        putPart(uploadId, 3, new byte[5]).andExpect(status().isBadRequest());
        putPart(uploadId, 0, new byte[5]).andExpect(status().isBadRequest());
        putPart(uploadId, 1, new byte[5]).andExpect(status().isBadRequest());
        putPart(uploadId, 2, new byte[5]).andExpect(status().isOk());
        putPart(uploadId, 2, new byte[5])
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receivedParts", contains(2)));

        mockMvc.perform(post(UPLOADS + "/" + uploadId + "/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.missingParts", contains(1)));
    }

    @Test
    void partsLostFromStorageAreReportedMissingAndCanBeResent() throws Exception {
        byte[] data = new byte[7];
        String uploadId = createId("still.jpg", "image/jpeg", data.length, "IMAGE", null);
        putPart(uploadId, 1, data).andExpect(status().isOk());
        memory.objects.remove(ObjectKeys.uploadPart(UUID.fromString(uploadId), 1));

        mockMvc.perform(post(UPLOADS + "/" + uploadId + "/complete"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.missingParts", contains(1)));
        mockMvc.perform(get(UPLOADS + "/" + uploadId))
                .andExpect(jsonPath("$.data.receivedParts", empty()));

        putPart(uploadId, 1, data).andExpect(status().isOk());
        mockMvc.perform(post(UPLOADS + "/" + uploadId + "/complete")).andExpect(status().isOk());
    }

    @Test
    void abortDeletesPartsAndClosesTheSession() throws Exception {
        String uploadId = createId("still.webp", "image/webp", 3, "IMAGE", null);
        putPart(uploadId, 1, new byte[3]).andExpect(status().isOk());

        mockMvc.perform(delete(UPLOADS + "/" + uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ABORTED"));
        assertFalse(memory.hasPrefix(ObjectKeys.uploadPrefix(UUID.fromString(uploadId))));

        putPart(uploadId, 1, new byte[3]).andExpect(status().isConflict());
        mockMvc.perform(post(UPLOADS + "/" + uploadId + "/complete")).andExpect(status().isConflict());
        mockMvc.perform(delete(UPLOADS + "/" + uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ABORTED"));
    }

    @Test
    void unknownSessionsAndBadIdsAreRejected() throws Exception {
        mockMvc.perform(get(UPLOADS + "/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mockMvc.perform(get(UPLOADS + "/not-a-uuid")).andExpect(status().isBadRequest());
        putPart(UUID.randomUUID().toString(), 1, new byte[1]).andExpect(status().isNotFound());
    }

    @Test
    void createValidatesPurposeRules() throws Exception {
        create("movie.mp4", "video/mp4", 100, "VIDEO", null).andExpect(status().isBadRequest());
        create("anim.gif", "image/gif", 100, "IMAGE", null).andExpect(status().isBadRequest());
        create("huge.jpg", "image/jpeg", 21L * 1024 * 1024, "IMAGE", null).andExpect(status().isBadRequest());
        create("trailer.mkv", "video/x-matroska", 100, "TRAILER", null).andExpect(status().isBadRequest());
        create("x.jpg", "image/jpeg", 0, "IMAGE", null).andExpect(status().isBadRequest());
        create("x.jpg", "image/jpeg", 10, "SUBTITLE", null).andExpect(status().isBadRequest());
        create("backdrop", "image/webp", 10, "IMAGE", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.filename").value("backdrop.webp"));
    }

    @Test
    void videoCompletionQueuesAssemblyAndBlocksConcurrentUploads() throws Exception {
        String contentId = UUID.randomUUID().toString();
        String uploadId = createId("My Movie.mp4", "video/mp4", 10, "VIDEO", contentId);
        putPart(uploadId, 1, new byte[10]).andExpect(status().isOk());

        String json = mockMvc.perform(post(UPLOADS + "/" + uploadId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("VIDEO"))
                .andExpect(jsonPath("$.data.file").value(nullValue()))
                .andExpect(jsonPath("$.data.asset.contentId").value(contentId))
                .andExpect(jsonPath("$.data.asset.originalFilename").value("My_Movie.mp4"))
                .andExpect(jsonPath("$.data.asset.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.asset.fileSizeBytes").value(10))
                .andReturn().getResponse().getContentAsString();
        UUID assetId = UUID.fromString(objectMapper.readTree(json).path("data").path("asset").path("id").asText());
        verify(ingest).assembleUpload(assetId);

        create("Other.mp4", "video/mp4", 10, "VIDEO", contentId).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/media/admin/imports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentId\":\"" + contentId + "\",\"url\":\"https://cdn.example.com/a.mp4\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/media/admin/assets/by-content/" + contentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
        mockMvc.perform(get("/api/v1/media/internal/" + contentId + "/status"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void storageOutageReturns503() throws Exception {
        when(storage.isAvailable()).thenReturn(false);
        create("poster.png", "image/png", 10, "IMAGE", null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void storageWriteFailureDoesNotRecordThePart() throws Exception {
        String uploadId = createId("poster.png", "image/png", 4, "IMAGE", null);
        doThrow(new StorageUnavailableException("down")).when(storage).write(any(), any(), anyLong(), any());
        putPart(uploadId, 1, new byte[4]).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get(UPLOADS + "/" + uploadId)).andExpect(jsonPath("$.data.receivedParts", empty()));
    }

    @Test
    void staleOpenSessionsAreAbortedByTheCleanupJob() throws Exception {
        String uploadId = createId("poster.png", "image/png", 4, "IMAGE", null);
        putPart(uploadId, 1, new byte[4]).andExpect(status().isOk());

        assertTrue(uploadSessionService.abortSessionsIdleSince(LocalDateTime.now().plusMinutes(1)) >= 1);
        assertEquals(UploadSessionStatus.ABORTED, sessionRepository.findById(UUID.fromString(uploadId)).orElseThrow().getStatus());
        assertFalse(memory.hasPrefix(ObjectKeys.uploadPrefix(UUID.fromString(uploadId))));

        String fresh = createId("poster.png", "image/png", 4, "IMAGE", null);
        uploadSessionService.abortSessionsIdleSince(LocalDateTime.now().minusHours(24));
        assertEquals(UploadSessionStatus.OPEN, sessionRepository.findById(UUID.fromString(fresh)).orElseThrow().getStatus());
    }
}
