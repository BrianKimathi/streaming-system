package com.streamx.billing.controller;

import com.streamx.billing.config.SecurityConfig;
import com.streamx.billing.dto.MpesaConnectionTestResponse;
import com.streamx.billing.dto.MpesaSettingsResponse;
import com.streamx.billing.dto.UpdateMpesaSettingsRequest;
import com.streamx.billing.mpesa.MpesaSettingSource;
import com.streamx.billing.service.MpesaSettingsService;
import com.streamx.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MpesaSettingsController.class)
@Import(SecurityConfig.class)
class MpesaSettingsControllerTest {

    private static final String URL = "/api/v1/billing/admin/mpesa-settings";
    private static final String VALID_BODY = """
            {"environment":"production","shortcode":"600987","transactionType":"CustomerBuyGoodsOnline",
             "callbackBaseUrl":"https://streamxapi.briankimathi.dev/api/v1/billing/mpesa/callback",
             "consumerKey":"new-key-value","consumerSecret":"","passkey":null,"regenerateCallbackToken":true}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MpesaSettingsService mpesaSettingsService;

    private static MpesaSettingsResponse response() {
        Map<String, MpesaSettingSource> sources = new LinkedHashMap<>();
        sources.put("environment", MpesaSettingSource.DATABASE);
        sources.put("shortcode", MpesaSettingSource.DATABASE);
        sources.put("transactionType", MpesaSettingSource.DATABASE);
        sources.put("callbackBaseUrl", MpesaSettingSource.DATABASE);
        sources.put("consumerKey", MpesaSettingSource.DATABASE);
        sources.put("consumerSecret", MpesaSettingSource.ENVIRONMENT);
        sources.put("passkey", MpesaSettingSource.NONE);
        sources.put("callbackToken", MpesaSettingSource.DATABASE);
        return new MpesaSettingsResponse("production", "600987", "CustomerBuyGoodsOnline",
                "https://streamxapi.briankimathi.dev/api/v1/billing/mpesa/callback",
                true, "••••alue", true, false, true, false, sources,
                LocalDateTime.parse("2026-10-07T20:15:00"), "finance@streamx.test");
    }

    @Test
    void getReturnsSettingsShape() throws Exception {
        when(mpesaSettingsService.getSettings()).thenReturn(response());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.environment").value("production"))
                .andExpect(jsonPath("$.data.shortcode").value("600987"))
                .andExpect(jsonPath("$.data.transactionType").value("CustomerBuyGoodsOnline"))
                .andExpect(jsonPath("$.data.consumerKeySet").value(true))
                .andExpect(jsonPath("$.data.consumerKeyHint").value("••••alue"))
                .andExpect(jsonPath("$.data.consumerSecretSet").value(true))
                .andExpect(jsonPath("$.data.passkeySet").value(false))
                .andExpect(jsonPath("$.data.callbackTokenSet").value(true))
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.sources.consumerSecret").value("ENVIRONMENT"))
                .andExpect(jsonPath("$.data.sources.passkey").value("NONE"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-10-07T20:15:00"))
                .andExpect(jsonPath("$.data.updatedBy").value("finance@streamx.test"));
    }

    @Test
    void putPassesBodyAndAdminEmail() throws Exception {
        when(mpesaSettingsService.updateSettings(any(), eq("finance@streamx.test"))).thenReturn(response());

        mockMvc.perform(put(URL)
                        .header("X-User-Email", "finance@streamx.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("M-Pesa settings saved"))
                .andExpect(jsonPath("$.data.consumerKeyHint").value("••••alue"));

        ArgumentCaptor<UpdateMpesaSettingsRequest> captor = ArgumentCaptor.forClass(UpdateMpesaSettingsRequest.class);
        verify(mpesaSettingsService).updateSettings(captor.capture(), eq("finance@streamx.test"));
        UpdateMpesaSettingsRequest sent = captor.getValue();
        assertEquals("production", sent.getEnvironment());
        assertEquals("new-key-value", sent.getConsumerKey());
        assertEquals("", sent.getConsumerSecret());
        assertNull(sent.getPasskey());
        assertTrue(sent.getRegenerateCallbackToken());
    }

    @Test
    void putValidatesFields() throws Exception {
        String[] invalidBodies = {
                VALID_BODY.replace("\"production\"", "\"live\""),
                VALID_BODY.replace("\"600987\"", "\"60A987\""),
                VALID_BODY.replace("\"600987\"", "\"1234\""),
                VALID_BODY.replace("\"600987\"", "\"123456789\""),
                VALID_BODY.replace("CustomerBuyGoodsOnline", "BusinessPayment"),
                VALID_BODY.replace("https://streamxapi", "http://streamxapi"),
                VALID_BODY.replace("\"environment\":\"production\",", ""),
                VALID_BODY.replace("\"new-key-value\"", "\"" + "k".repeat(513) + "\""),
        };
        for (String body : invalidBodies) {
            mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation failed"));
        }
        verify(mpesaSettingsService, never()).updateSettings(any(), any());
    }

    @Test
    void putServiceValidationErrorIs400() throws Exception {
        when(mpesaSettingsService.updateSettings(any(), any()))
                .thenThrow(new BadRequestException("Callback base URL must be a valid https URL without a query string or fragment"));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Callback base URL must be a valid https URL without a query string or fragment"));
    }

    @Test
    void deleteClearsStoredSecrets() throws Exception {
        when(mpesaSettingsService.clearStoredSecrets("finance@streamx.test")).thenReturn(response());

        mockMvc.perform(delete(URL + "/secrets").header("X-User-Email", "finance@streamx.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stored M-Pesa credentials cleared"))
                .andExpect(jsonPath("$.data.sources.consumerKey").value("DATABASE"));
        verify(mpesaSettingsService).clearStoredSecrets("finance@streamx.test");
    }

    @Test
    void connectionTestReturns200EitherWay() throws Exception {
        when(mpesaSettingsService.testConnection())
                .thenReturn(new MpesaConnectionTestResponse(true, "Connected to M-Pesa sandbox"));
        mockMvc.perform(post(URL + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.message").value("Connected to M-Pesa sandbox"));

        when(mpesaSettingsService.testConnection())
                .thenReturn(new MpesaConnectionTestResponse(false, "M-Pesa authentication failed (HTTP 400)"));
        mockMvc.perform(post(URL + "/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.message").value("M-Pesa authentication failed (HTTP 400)"));
    }
}
