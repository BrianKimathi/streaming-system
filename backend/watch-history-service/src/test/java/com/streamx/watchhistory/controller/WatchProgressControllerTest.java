package com.streamx.watchhistory.controller;

import com.streamx.common.security.SecurityConstants;
import com.streamx.watchhistory.client.TrendingClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WatchProgressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrendingClient trendingClient;

    private final String accountId = UUID.randomUUID().toString();
    private final String profileId = UUID.randomUUID().toString();

    @Test
    void recordsProgressWithContractShape() throws Exception {
        String showId = UUID.randomUUID().toString();
        String episodeId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/watch-history/progress")
                        .header(SecurityConstants.HEADER_X_ACCOUNT_ID, accountId)
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, profileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentId\":\"" + episodeId + "\",\"titleId\":\"" + showId
                                + "\",\"titleType\":\"SERIES\",\"positionSeconds\":300,\"durationSeconds\":1200}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.profileId").value(profileId))
                .andExpect(jsonPath("$.data.contentId").value(episodeId))
                .andExpect(jsonPath("$.data.titleId").value(showId))
                .andExpect(jsonPath("$.data.titleType").value("SERIES"))
                .andExpect(jsonPath("$.data.positionSeconds").value(300))
                .andExpect(jsonPath("$.data.durationSeconds").value(1200))
                .andExpect(jsonPath("$.data.percentage").value(25.0))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.lastWatchedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.episodeId").doesNotExist());

        mockMvc.perform(get("/api/v1/watch-history/continue-watching")
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].titleId").value(showId));

        mockMvc.perform(get("/api/v1/watch-history/titles/" + showId)
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].contentId").value(episodeId));

        mockMvc.perform(get("/api/v1/watch-history/" + episodeId)
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.positionSeconds").value(300));

        mockMvc.perform(get("/api/v1/watch-history/" + episodeId)
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/watch-history/titles/" + showId)
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/watch-history/" + episodeId)
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, profileId))
                .andExpect(status().isNotFound());
    }

    @Test
    void profileHeaderIsRequired() throws Exception {
        mockMvc.perform(get("/api/v1/watch-history/continue-watching"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select a profile first"));

        mockMvc.perform(get("/api/v1/watch-history/titles/" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select a profile first"));

        mockMvc.perform(delete("/api/v1/watch-history/titles/" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select a profile first"));

        mockMvc.perform(get("/api/v1/watch-history/" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select a profile first"));

        mockMvc.perform(post("/api/v1/watch-history/progress")
                        .header(SecurityConstants.HEADER_X_ACCOUNT_ID, accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentId\":\"" + UUID.randomUUID() + "\",\"positionSeconds\":1,\"durationSeconds\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select a profile first"));
    }

    @Test
    void validatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/watch-history/progress")
                        .header(SecurityConstants.HEADER_X_ACCOUNT_ID, accountId)
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, profileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentId\":\"" + UUID.randomUUID() + "\",\"positionSeconds\":-1,\"durationSeconds\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.positionSeconds").exists())
                .andExpect(jsonPath("$.errors.durationSeconds").exists());

        mockMvc.perform(post("/api/v1/watch-history/progress")
                        .header(SecurityConstants.HEADER_X_ACCOUNT_ID, accountId)
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, profileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentId\":\"" + UUID.randomUUID() + "\",\"titleType\":\"CLIP\",\"positionSeconds\":1,\"durationSeconds\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("titleType must be MOVIE or SERIES"));
    }
}
