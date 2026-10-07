package com.streamx.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.auth.client.ProfileDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProfileDirectoryClient profileDirectoryClient;

    private JsonNode registerAccount(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    @Test
    void registerWithShortPasswordReturns400WithMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Password must be at least 8 characters long"));
    }

    @Test
    void registerReturnsAuthResponseShape() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" Shape@Example.com \",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("shape@example.com"))
                .andExpect(jsonPath("$.data.accountId").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_USER"));
    }

    @Test
    void logoutWithoutBodyRevokesAllSessions() throws Exception {
        JsonNode account = registerAccount("logout.nobody@example.com");

        mockMvc.perform(post("/api/v1/auth/logout").header("X-Account-Id", account.path("accountId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + account.path("refreshToken").asText() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsAccountResponse() throws Exception {
        JsonNode account = registerAccount("me.api@example.com");

        mockMvc.perform(get("/api/v1/auth/me").header("X-Account-Id", account.path("accountId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(account.path("accountId").asText()))
                .andExpect(jsonPath("$.data.email").value("me.api@example.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.emailVerified").value(false));
    }

    @Test
    void meWithoutIdentityIs401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordIs400() throws Exception {
        JsonNode account = registerAccount("change.api@example.com");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("X-Account-Id", account.path("accountId").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"NewPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void sendOtpReturns503WhenSmsIsNotConfigured() throws Exception {
        mockMvc.perform(post("/api/v1/auth/phone/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+254700000000\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("SMS delivery is not configured"));
    }
}
