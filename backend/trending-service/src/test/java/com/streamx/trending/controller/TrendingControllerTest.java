package com.streamx.trending.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.trending.dto.RecordEventRequestDto;
import com.streamx.trending.dto.TrendingItemResponseDto;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrendingController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrendingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrendingService trendingService;

    @Test
    void getTopTrending_Returns200() throws Exception {
        UUID contentId = UUID.randomUUID();
        TrendingItemResponseDto item = new TrendingItemResponseDto(
                contentId, "Top Title", "MOVIE", 10, 20, 5, 2, 110.0, Instant.now()
        );

        when(trendingService.getTopTrending(anyInt())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/trending?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("Top Title"))
                .andExpect(jsonPath("$.data[0].velocityScore").value(110.0));
    }

    @Test
    void recordEvent_Returns200() throws Exception {
        UUID contentId = UUID.randomUUID();
        RecordEventRequestDto request = new RecordEventRequestDto(contentId, "Top Title", "MOVIE", "VIEW");
        TrendingItemResponseDto response = new TrendingItemResponseDto(
                contentId, "Top Title", "MOVIE", 1, 1, 0, 0, 8.0, Instant.now()
        );

        when(trendingService.recordEvent(any(RecordEventRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/trending/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.views1h").value(1));
    }
}
