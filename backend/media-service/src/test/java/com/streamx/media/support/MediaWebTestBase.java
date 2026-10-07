package com.streamx.media.support;

import com.streamx.common.security.JwtUtils;
import com.streamx.media.hls.HlsTranscoderService;
import com.streamx.media.service.MediaIngestService;
import com.streamx.media.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full application context on H2 with the storage abstraction mocked (backed by {@link InMemoryStorage}) and the
 * background ingest/transcode jobs mocked so tests never need MinIO or ffmpeg.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class MediaWebTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtUtils jwtUtils;

    @MockitoBean
    protected ObjectStorage storage;

    @MockitoBean
    protected MediaIngestService ingest;

    @MockitoBean
    protected HlsTranscoderService transcoder;

    protected InMemoryStorage memory;

    @BeforeEach
    void attachInMemoryStorage() {
        memory = InMemoryStorage.attach(storage);
    }
}
