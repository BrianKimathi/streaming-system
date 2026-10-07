package com.streamx.media;

import com.streamx.common.security.JwtUtils;
import com.streamx.media.storage.MinioObjectStorage;
import com.streamx.media.storage.ObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real MinIO-backed context must start while MinIO is unreachable and answer storage calls with 503. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:media_storage_down_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
        "media.minio.endpoint=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorageUnavailableStartupTest {

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void contextStartsAndStorageEndpointsReport503() throws Exception {
        assertInstanceOf(MinioObjectStorage.class, storage);
        assertFalse(storage.isAvailable());

        mockMvc.perform(post("/api/v1/media/admin/uploads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"poster.png\",\"contentType\":\"image/png\",\"sizeBytes\":10,\"purpose\":\"IMAGE\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));

        String contentId = UUID.randomUUID().toString();
        String token = jwtUtils.generateStreamToken("acc", contentId, UUID.randomUUID().toString(), 60_000);
        mockMvc.perform(get("/api/v1/media/stream/" + token + "/" + contentId + "/master.m3u8"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(get("/api/v1/media/admin/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storageAvailable").value(false));
    }
}
