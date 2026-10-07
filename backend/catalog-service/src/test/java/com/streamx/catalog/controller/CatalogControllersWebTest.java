package com.streamx.catalog.controller;

import com.streamx.catalog.dto.CatalogLookupResponse;
import com.streamx.catalog.service.CatalogAdminService;
import com.streamx.catalog.service.CatalogService;
import com.streamx.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CatalogController.class, CatalogAdminController.class, CatalogInternalController.class})
@AutoConfigureMockMvc(addFilters = false)
class CatalogControllersWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private CatalogAdminService catalogAdminService;

    @Test
    void createSeason_rejectsNonPositiveNumber() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/admin/tv-shows/{id}/seasons", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seasonNumber\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Season number must be positive"));
        verifyNoInteractions(catalogAdminService);
    }

    @Test
    void createEpisode_requiresTitleAndNumber() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/admin/seasons/{id}/episodes", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runtimeMinutes\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.episodeNumber").exists())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.runtimeMinutes").exists());
        verifyNoInteractions(catalogAdminService);
    }

    @Test
    void mediaUrls_mustBeHttpLinks() throws Exception {
        mockMvc.perform(put("/api/v1/catalog/admin/movies/{id}", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"posterUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.posterUrl").exists());
        mockMvc.perform(post("/api/v1/catalog/admin/seasons/{id}/episodes", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"episodeNumber\":1,\"title\":\"E\",\"thumbnailUrl\":\"https://x.example/" + "a".repeat(2000) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.thumbnailUrl").exists());
        verifyNoInteractions(catalogAdminService);

        mockMvc.perform(put("/api/v1/catalog/admin/movies/{id}", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"posterUrl\":\"https://streamxapi.briankimathi.dev/api/v1/media/files/1/p.jpg\","
                                + "\"trailerUrl\":\"\"}"))
                .andExpect(status().isOk());
        verify(catalogAdminService).updateMovie(eq("00000000-0000-0000-0000-000000000001"), any());
    }

    @Test
    void publicMovies_ignoreStatusParam() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/movies").param("status", "DRAFT").param("search", "x"))
                .andExpect(status().isOk());
        verify(catalogService).getPublishedMovies(eq("x"), isNull(), any());
    }

    @Test
    void lookup_requiresIdsAndSplitsCommaList() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/lookup"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        when(catalogService.lookup(anyList())).thenReturn(new CatalogLookupResponse(List.of(), List.of(), List.of()));
        mockMvc.perform(get("/api/v1/catalog/lookup").param("ids", "a,b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.movies").isArray())
                .andExpect(jsonPath("$.data.tvShows").isArray())
                .andExpect(jsonPath("$.data.episodes").isArray());
        verify(catalogService).lookup(List.of("a", "b"));
    }

    @Test
    void internalTitle_notFoundMapsTo404() throws Exception {
        when(catalogService.getInternalTitle("missing")).thenThrow(new ResourceNotFoundException("Title not found"));

        mockMvc.perform(get("/api/v1/catalog/internal/titles/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Title not found"));
    }
}
