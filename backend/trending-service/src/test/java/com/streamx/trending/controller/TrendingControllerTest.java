package com.streamx.trending.controller;

import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.trending.domain.TrendingEventType;
import com.streamx.trending.dto.RecordTrendingEventRequest;
import com.streamx.trending.dto.TrendingItemResponseDto;
import com.streamx.trending.exception.CatalogUnavailableException;
import com.streamx.trending.service.TrendingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrendingController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrendingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrendingService trendingService;

    @Test
    void getTopTrending_Returns200() throws Exception {
        UUID contentId = UUID.randomUUID();
        TrendingItemResponseDto item = new TrendingItemResponseDto(
                contentId, "Top Title", "MOVIE", 10, 20, 5, 0, 120.0, Instant.now()
        );
        when(trendingService.getTopTrending(anyInt())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/trending?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].contentId").value(contentId.toString()))
                .andExpect(jsonPath("$.data[0].title").value("Top Title"))
                .andExpect(jsonPath("$.data[0].contentType").value("MOVIE"))
                .andExpect(jsonPath("$.data[0].velocityScore").value(120.0));
        verify(trendingService).getTopTrending(10);
    }

    @Test
    void getTopTrending_clampsLimit() throws Exception {
        mockMvc.perform(get("/api/v1/trending?limit=500")).andExpect(status().isOk());
        verify(trendingService).getTopTrending(50);

        mockMvc.perform(get("/api/v1/trending?limit=-3")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/trending?limit=0")).andExpect(status().isOk());
        verify(trendingService, times(2)).getTopTrending(1);

        mockMvc.perform(get("/api/v1/trending")).andExpect(status().isOk());
        verify(trendingService).getTopTrending(20);
    }

    @Test
    void internalRecord_Returns200() throws Exception {
        UUID titleId = UUID.randomUUID();
        when(trendingService.recordEvent(any(RecordTrendingEventRequest.class))).thenReturn(
                new TrendingItemResponseDto(titleId, "Top Title", "SERIES", 1, 1, 0, 0, 8.0, Instant.now()));

        mockMvc.perform(post("/api/v1/trending/internal/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleId\":\"" + titleId + "\",\"eventType\":\"VIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.views1h").value(1));
        verify(trendingService).recordEvent(new RecordTrendingEventRequest(titleId, TrendingEventType.VIEW));
    }

    @Test
    void internalRecord_validatesBody() throws Exception {
        mockMvc.perform(post("/api/v1/trending/internal/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"VIEW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(post("/api/v1/trending/internal/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"LIKE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(trendingService);
    }

    @Test
    void internalRecord_mapsServiceErrors() throws Exception {
        when(trendingService.recordEvent(any())).thenThrow(new ResourceNotFoundException("Title not found"));
        mockMvc.perform(post("/api/v1/trending/internal/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"COMPLETION\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Title not found"));

        reset(trendingService);
        when(trendingService.recordEvent(any()))
                .thenThrow(new CatalogUnavailableException("Catalog service is unavailable", null));
        mockMvc.perform(post("/api/v1/trending/internal/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"VIEW\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void publicRecordEndpointIsGone() throws Exception {
        mockMvc.perform(post("/api/v1/trending/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"VIEW\"}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(trendingService);
    }
}
