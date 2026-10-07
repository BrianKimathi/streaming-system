package com.streamx.analytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.analytics.dto.AnalyticsDashboardDto;
import com.streamx.analytics.dto.RecordStreamEventDto;
import com.streamx.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    void getDashboard_Returns200() throws Exception {
        LocalDate today = LocalDate.now();
        AnalyticsDashboardDto dashboard = new AnalyticsDashboardDto(
                today, 1000, 10000, 500.0, 85.0, 2000, 9500, 50000.0
        );

        when(analyticsService.getDashboard(any())).thenReturn(dashboard);

        mockMvc.perform(get("/api/v1/analytics/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.dailyActiveUsers").value(1000))
                .andExpect(jsonPath("$.data.totalRevenue").value(50000.0));
    }

    @Test
    void recordEvent_Returns200() throws Exception {
        RecordStreamEventDto event = new RecordStreamEventDto(3600, 100.0, true, 14.99);
        AnalyticsDashboardDto dashboard = new AnalyticsDashboardDto(
                LocalDate.now(), 1, 1, 1.0, 100.0, 1, 1, 14.99
        );

        when(analyticsService.recordStreamEvent(any(RecordStreamEventDto.class))).thenReturn(dashboard);

        mockMvc.perform(post("/api/v1/analytics/record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(event)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalWatchTimeHours").value(1.0));
    }
}
