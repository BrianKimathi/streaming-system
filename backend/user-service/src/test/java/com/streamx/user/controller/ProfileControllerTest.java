package com.streamx.user.controller;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.common.exception.UnauthorizedException;
import com.streamx.user.domain.TitleType;
import com.streamx.user.dto.ProfileOwnerResponse;
import com.streamx.user.dto.ProfileResponse;
import com.streamx.user.dto.SelectProfileResponse;
import com.streamx.user.dto.WatchlistItemResponse;
import com.streamx.user.service.ProfileService;
import com.streamx.user.service.WatchlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProfileController.class, ProfileInternalController.class})
@AutoConfigureMockMvc(addFilters = false)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private WatchlistService watchlistService;

    @Test
    void selectProfileUsesEmailAndSplitsRolesFromGatewayHeaders() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String profileId = UUID.randomUUID().toString();
        ProfileResponse profile = new ProfileResponse();
        profile.setId(profileId);
        when(profileService.selectProfile(any(), any(), any(), any(), any()))
                .thenReturn(new SelectProfileResponse("profile-token", profile));

        mockMvc.perform(post("/api/v1/profiles/{id}/select", profileId)
                        .header("X-Account-Id", accountId)
                        .header("X-User-Email", "jane@example.com")
                        .header("X-User-Roles", "ROLE_USER, ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileAccessToken").value("profile-token"));

        verify(profileService).selectProfile(eq(accountId), eq("jane@example.com"),
                eq(List.of("ROLE_USER", "ROLE_ADMIN")), eq(profileId), isNull());
    }

    @Test
    void wrongPinReturns401() throws Exception {
        when(profileService.selectProfile(any(), any(), any(), any(), any()))
                .thenThrow(new UnauthorizedException("Incorrect PIN code."));

        mockMvc.perform(post("/api/v1/profiles/{id}/select", UUID.randomUUID())
                        .param("pin", "0000")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .header("X-User-Email", "jane@example.com")
                        .header("X-User-Roles", "ROLE_USER"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect PIN code."));
    }

    @Test
    void watchlistRoutesDoNotCollideWithProfileIdRoutes() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String profileId = UUID.randomUUID().toString();
        String titleId = UUID.randomUUID().toString();
        when(watchlistService.getWatchlist(accountId, profileId))
                .thenReturn(List.of(new WatchlistItemResponse(titleId, TitleType.MOVIE, LocalDateTime.now())));
        when(watchlistService.addToWatchlist(accountId, profileId, titleId, TitleType.SERIES))
                .thenReturn(new WatchlistItemResponse(titleId, TitleType.SERIES, LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/profiles/me/watchlist")
                        .header("X-Account-Id", accountId)
                        .header("X-Profile-Id", profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].titleId").value(titleId))
                .andExpect(jsonPath("$.data[0].titleType").value("MOVIE"));

        mockMvc.perform(put("/api/v1/profiles/me/watchlist/{titleId}", titleId)
                        .header("X-Account-Id", accountId)
                        .header("X-Profile-Id", profileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleType\":\"SERIES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titleType").value("SERIES"));

        mockMvc.perform(delete("/api/v1/profiles/me/watchlist/{titleId}", titleId)
                        .header("X-Account-Id", accountId)
                        .header("X-Profile-Id", profileId))
                .andExpect(status().isOk());

        verify(watchlistService).removeFromWatchlist(accountId, profileId, titleId);
    }

    @Test
    void watchlistWithoutProfileReturns400() throws Exception {
        String accountId = UUID.randomUUID().toString();
        when(watchlistService.getWatchlist(accountId, null)).thenThrow(new BadRequestException("Select a profile first"));

        mockMvc.perform(get("/api/v1/profiles/me/watchlist").header("X-Account-Id", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Select a profile first"));
    }

    @Test
    void addToWatchlistWithoutTitleTypeReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/profiles/me/watchlist/{titleId}", UUID.randomUUID())
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .header("X-Profile-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalOwnerLookup() throws Exception {
        String profileId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();
        when(profileService.getProfileOwner(profileId)).thenReturn(new ProfileOwnerResponse(accountId));

        mockMvc.perform(get("/api/v1/profiles/internal/{id}/owner", profileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(accountId));
    }

    @Test
    void internalOwnerLookupMissingProfileReturns404() throws Exception {
        String profileId = UUID.randomUUID().toString();
        when(profileService.getProfileOwner(profileId)).thenThrow(new ResourceNotFoundException("Profile not found"));

        mockMvc.perform(get("/api/v1/profiles/internal/{id}/owner", profileId))
                .andExpect(status().isNotFound());
    }
}
