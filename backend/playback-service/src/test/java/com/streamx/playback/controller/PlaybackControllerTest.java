package com.streamx.playback.controller;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.security.SecurityConstants;
import com.streamx.playback.client.DeviceClient;
import com.streamx.playback.client.DeviceClient.DeviceStatus;
import com.streamx.playback.client.EntitlementClient;
import com.streamx.playback.client.MediaClient;
import com.streamx.playback.client.MediaClient.MediaStatus;
import com.streamx.playback.client.SubscriptionEntitlementClient;
import com.streamx.playback.client.TrendingClient;
import com.streamx.playback.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlaybackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EntitlementClient entitlementClient;

    @MockitoBean
    private DeviceClient deviceClient;

    @MockitoBean
    private MediaClient mediaClient;

    @MockitoBean
    private TrendingClient trendingClient;

    private final String contentId = UUID.randomUUID().toString();

    @BeforeEach
    void allowEverything() {
        when(entitlementClient.maxConcurrentStreams(any(UUID.class))).thenReturn(4);
        when(deviceClient.deviceStatus(any(UUID.class), any(UUID.class))).thenReturn(DeviceStatus.ACTIVE);
        when(mediaClient.mediaStatus(any(UUID.class))).thenReturn(Optional.of(new MediaStatus("COMPLETED", 3600)));
    }

    private ResultActions requestPlayback(boolean withProfile) throws Exception {
        var builder = post("/api/v1/playback/request")
                .header(SecurityConstants.HEADER_X_ACCOUNT_ID, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentId\":\"" + contentId + "\",\"deviceId\":\"" + UUID.randomUUID() + "\"}");
        if (withProfile) {
            builder.header(SecurityConstants.HEADER_X_PROFILE_ID, UUID.randomUUID().toString());
        }
        return mockMvc.perform(builder);
    }

    @Test
    void authorizesPlaybackWithTokenizedStreamUrl() throws Exception {
        requestPlayback(true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentId").value(contentId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.durationSeconds").value(3600))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.streamUrl").value(
                        matchesPattern("^/api/v1/media/stream/[A-Za-z0-9_\\-.]+/" + contentId + "/master\\.m3u8$")));
    }

    @Test
    void missingProfileIs400() throws Exception {
        requestPlayback(false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Select a profile first"));
    }

    @Test
    void missingSubscriptionIs400() throws Exception {
        when(entitlementClient.maxConcurrentStreams(any(UUID.class)))
                .thenThrow(new BadRequestException(SubscriptionEntitlementClient.SUBSCRIPTION_REQUIRED));
        requestPlayback(true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An active subscription is required"));
    }

    @Test
    void revokedDeviceIs403() throws Exception {
        when(deviceClient.deviceStatus(any(UUID.class), any(UUID.class))).thenReturn(DeviceStatus.REVOKED);
        requestPlayback(true)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This device has been signed out. Sign in again."));
    }

    @Test
    void mediaNotReadyIs409() throws Exception {
        when(mediaClient.mediaStatus(any(UUID.class))).thenReturn(Optional.of(new MediaStatus("FAILED", null)));
        requestPlayback(true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This title isn't available to stream yet"));
    }

    @Test
    void dependencyOutageIs503() throws Exception {
        when(deviceClient.deviceStatus(any(UUID.class), any(UUID.class)))
                .thenThrow(new ServiceUnavailableException("Device verification is temporarily unavailable. Try again shortly."));
        requestPlayback(true)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Device verification is temporarily unavailable. Try again shortly."));
    }

    @Test
    void missingDeviceIdIsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/playback/request")
                        .header(SecurityConstants.HEADER_X_ACCOUNT_ID, UUID.randomUUID().toString())
                        .header(SecurityConstants.HEADER_X_PROFILE_ID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentId\":\"" + contentId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.deviceId").value("Device ID is required"));
    }
}
