package com.streamx.media.controller;

import com.streamx.media.domain.MediaFile;
import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.exception.StorageUnavailableException;
import com.streamx.media.repository.MediaFileRepository;
import com.streamx.media.storage.ObjectKeys;
import com.streamx.media.support.MediaWebTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaFileControllerTest extends MediaWebTestBase {

    private static final byte[] DATA = "0123456789".getBytes();

    @Autowired
    private MediaFileRepository fileRepository;

    private MediaFile file;
    private String url;

    @BeforeEach
    void storeTrailer() {
        UUID id = UUID.randomUUID();
        file = new MediaFile();
        file.setId(id);
        file.setPurpose(UploadPurpose.TRAILER);
        file.setFilename("teaser.mp4");
        file.setContentType("video/mp4");
        file.setSizeBytes(DATA.length);
        file.setObjectKey(ObjectKeys.file(id, "teaser.mp4"));
        fileRepository.save(file);
        memory.put(file.getObjectKey(), DATA);
        url = "/api/v1/media/files/" + id + "/teaser.mp4";
    }

    @Test
    void servesWholeFileWithImmutableCaching() throws Exception {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("video/mp4"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + file.getId() + "\""))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, DATA.length))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(DATA));
    }

    @Test
    void servesByteRanges() throws Exception {
        mockMvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 4))
                .andExpect(content().bytes("2345".getBytes()));

        mockMvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 7-9/10"))
                .andExpect(content().bytes("789".getBytes()));

        mockMvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=8-"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes("89".getBytes()));
    }

    @Test
    void badRangesAre416() throws Exception {
        for (String range : new String[]{"bytes=10-20", "bytes=abc", "bytes=5-2"}) {
            mockMvc.perform(get(url).header(HttpHeaders.RANGE, range))
                    .andExpect(status().isRequestedRangeNotSatisfiable())
                    .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */10"));
        }
    }

    @Test
    void conditionalGetReturns304() throws Exception {
        mockMvc.perform(get(url).header(HttpHeaders.IF_NONE_MATCH, "\"" + file.getId() + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void headReturnsHeadersOnly() throws Exception {
        mockMvc.perform(head(url))
                .andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, DATA.length))
                .andExpect(content().bytes(new byte[0]));
    }

    @Test
    void idAndFilenameMustMatch() throws Exception {
        mockMvc.perform(get("/api/v1/media/files/" + file.getId() + "/other.mp4").header(HttpHeaders.ACCEPT, "video/*"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/media/files/" + UUID.randomUUID() + "/teaser.mp4").header(HttpHeaders.ACCEPT, "image/*"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/media/files/not-a-uuid/teaser.mp4"))
                .andExpect(status().isNotFound());
    }

    @Test
    void storageOutageIs503() throws Exception {
        doThrow(new StorageUnavailableException("down")).when(storage).read(anyString(), anyLong(), anyLong());
        mockMvc.perform(get(url).header(HttpHeaders.ACCEPT, "video/mp4"))
                .andExpect(status().isServiceUnavailable());
    }
}
