package com.streamx.billing.service;

import com.streamx.billing.client.NotificationClient;
import com.streamx.billing.client.PlanDetails;
import com.streamx.billing.client.SubscriptionClient;
import com.streamx.billing.config.MpesaProperties;
import com.streamx.billing.config.SettingsCipher;
import com.streamx.billing.domain.MpesaSettings;
import com.streamx.billing.dto.CheckoutRequest;
import com.streamx.billing.dto.MpesaConnectionTestResponse;
import com.streamx.billing.dto.MpesaSettingsResponse;
import com.streamx.billing.dto.UpdateMpesaSettingsRequest;
import com.streamx.billing.exception.ServiceUnavailableException;
import com.streamx.billing.mpesa.MpesaConfigProvider;
import com.streamx.billing.mpesa.MpesaException;
import com.streamx.billing.mpesa.MpesaGateway;
import com.streamx.billing.mpesa.MpesaSettingSource;
import com.streamx.billing.mpesa.StkPushResult;
import com.streamx.billing.repository.MpesaSettingsRepository;
import com.streamx.billing.repository.PaymentTransactionRepository;
import com.streamx.common.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class MpesaSettingsServiceTest {

    private static final String ADMIN = "finance@streamx.test";
    private static final String DB_KEY = "Gq7DbConsumerKeyValue9xY2";
    private static final String DB_SECRET = "Zt4DbConsumerSecretValue1kP";
    private static final String DB_PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919";

    @Autowired
    private MpesaSettingsService settingsService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private MpesaSettingsRepository settingsRepository;

    @Autowired
    private PaymentTransactionRepository transactionRepository;

    @Autowired
    private MpesaConfigProvider configProvider;

    @Autowired
    private MpesaProperties properties;

    @Autowired
    private SettingsCipher cipher;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private MpesaGateway mpesaGateway;

    @MockitoBean
    private SubscriptionClient subscriptionClient;

    @MockitoBean
    private NotificationClient notificationClient;

    private MockMvc mockMvc;
    private String originalConsumerKey;
    private String originalCallbackToken;

    @BeforeEach
    void setUp() {
        settingsRepository.deleteAll();
        transactionRepository.deleteAll();
        originalConsumerKey = properties.getConsumerKey();
        originalCallbackToken = properties.getCallbackToken();
        configProvider.invalidate();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        settingsRepository.deleteAll();
        properties.setConsumerKey(originalConsumerKey);
        properties.setCallbackToken(originalCallbackToken);
        configProvider.invalidate();
    }

    private static UpdateMpesaSettingsRequest request(String key, String secret, String passkey) {
        UpdateMpesaSettingsRequest request = new UpdateMpesaSettingsRequest();
        request.setEnvironment("sandbox");
        request.setShortcode("174379");
        request.setTransactionType("CustomerPayBillOnline");
        request.setCallbackBaseUrl("https://billing.test/api/v1/billing/mpesa/callback/");
        request.setConsumerKey(key);
        request.setConsumerSecret(secret);
        request.setPasskey(passkey);
        return request;
    }

    private MpesaSettings storedRow() {
        return settingsRepository.findById(MpesaSettings.SINGLETON_ID).orElseThrow();
    }

    @Test
    void reportsEnvironmentValuesWhenNothingIsStored() {
        MpesaSettingsResponse response = settingsService.getSettings();

        assertTrue(response.configured());
        assertTrue(response.consumerKeySet());
        assertEquals("••••-key", response.consumerKeyHint());
        assertTrue(response.callbackTokenSet());
        assertEquals(MpesaSettingSource.ENVIRONMENT, response.sources().get("consumerKey"));
        assertEquals(MpesaSettingSource.ENVIRONMENT, response.sources().get("callbackToken"));
        assertNull(response.updatedAt());
        assertNull(response.updatedBy());
    }

    @Test
    void storesEncryptedSecretsAndKeepsThemWhenBlank() {
        MpesaSettingsResponse saved = settingsService.updateSettings(request(DB_KEY, DB_SECRET, DB_PASSKEY), ADMIN);

        MpesaSettings row = storedRow();
        assertNotEquals(DB_KEY, row.getConsumerKeyEnc());
        assertFalse(row.getConsumerSecretEnc().contains(DB_SECRET));
        assertEquals(DB_KEY, cipher.decrypt(row.getConsumerKeyEnc()));
        assertEquals(DB_SECRET, cipher.decrypt(row.getConsumerSecretEnc()));
        assertEquals(DB_PASSKEY, cipher.decrypt(row.getPasskeyEnc()));
        assertEquals("https://billing.test/api/v1/billing/mpesa/callback", row.getCallbackBaseUrl());
        assertEquals(ADMIN, saved.updatedBy());
        assertNotNull(saved.updatedAt());
        assertEquals("••••" + DB_KEY.substring(DB_KEY.length() - 4), saved.consumerKeyHint());
        assertEquals(MpesaSettingSource.DATABASE, saved.sources().get("consumerKey"));
        assertEquals(MpesaSettingSource.DATABASE, saved.sources().get("consumerSecret"));
        assertEquals(MpesaSettingSource.DATABASE, saved.sources().get("passkey"));
        String keyEnc = row.getConsumerKeyEnc();
        String secretEnc = row.getConsumerSecretEnc();
        String passkeyEnc = row.getPasskeyEnc();

        UpdateMpesaSettingsRequest keep = request(null, "  ", "");
        keep.setShortcode("600987");
        MpesaSettingsResponse kept = settingsService.updateSettings(keep, "ops@streamx.test");

        MpesaSettings after = storedRow();
        assertEquals(keyEnc, after.getConsumerKeyEnc());
        assertEquals(secretEnc, after.getConsumerSecretEnc());
        assertEquals(passkeyEnc, after.getPasskeyEnc());
        assertEquals("600987", kept.shortcode());
        assertEquals("ops@streamx.test", kept.updatedBy());
        assertEquals(MpesaSettingSource.DATABASE, kept.sources().get("consumerSecret"));
        assertEquals(DB_SECRET, configProvider.current().consumerSecret());
        assertEquals("600987", configProvider.current().shortcode());
    }

    @Test
    void saveInvalidatesCachedConfigAndOauthToken() {
        assertEquals("test-consumer-key", configProvider.current().consumerKey());

        settingsService.updateSettings(request(DB_KEY, null, null), ADMIN);

        assertEquals(DB_KEY, configProvider.current().consumerKey());
        verify(mpesaGateway).resetAuthentication();
    }

    @Test
    void generatesCallbackTokenWhenNoneExists() {
        properties.setCallbackToken("");
        configProvider.invalidate();
        assertFalse(settingsService.getSettings().callbackTokenSet());

        MpesaSettingsResponse saved = settingsService.updateSettings(request(null, null, null), ADMIN);

        String token = cipher.decrypt(storedRow().getCallbackTokenEnc());
        assertTrue(token.matches("[0-9a-f]{48}"), "24 random bytes as hex");
        assertTrue(saved.callbackTokenSet());
        assertEquals(MpesaSettingSource.DATABASE, saved.sources().get("callbackToken"));
        assertTrue(billingService.isValidCallbackToken(token));
        assertEquals("https://billing.test/api/v1/billing/mpesa/callback/" + token, configProvider.current().callbackUrl());

        settingsService.updateSettings(request(null, null, null), ADMIN);
        assertEquals(token, cipher.decrypt(storedRow().getCallbackTokenEnc()), "existing token is kept");
    }

    @Test
    void keepsEnvironmentCallbackTokenWhenPresent() {
        MpesaSettingsResponse saved = settingsService.updateSettings(request(null, null, null), ADMIN);

        assertNull(storedRow().getCallbackTokenEnc());
        assertEquals(MpesaSettingSource.ENVIRONMENT, saved.sources().get("callbackToken"));
        assertTrue(billingService.isValidCallbackToken("test-callback-token"));
    }

    @Test
    void regeneratesCallbackToken() {
        UpdateMpesaSettingsRequest regenerate = request(null, null, null);
        regenerate.setRegenerateCallbackToken(true);

        settingsService.updateSettings(regenerate, ADMIN);
        String first = cipher.decrypt(storedRow().getCallbackTokenEnc());
        settingsService.updateSettings(regenerate, ADMIN);
        String second = cipher.decrypt(storedRow().getCallbackTokenEnc());

        assertNotEquals(first, second);
        assertTrue(second.matches("[0-9a-f]{48}"));
        assertFalse(billingService.isValidCallbackToken("test-callback-token"), "database token overrides env");
        assertFalse(billingService.isValidCallbackToken(first));
        assertTrue(billingService.isValidCallbackToken(second));
    }

    @Test
    void clearingStoredSecretsFallsBackToEnvironment() {
        UpdateMpesaSettingsRequest withToken = request(DB_KEY, DB_SECRET, DB_PASSKEY);
        withToken.setRegenerateCallbackToken(true);
        settingsService.updateSettings(withToken, ADMIN);

        MpesaSettingsResponse cleared = settingsService.clearStoredSecrets("ops@streamx.test");

        MpesaSettings row = storedRow();
        assertNull(row.getConsumerKeyEnc());
        assertNull(row.getConsumerSecretEnc());
        assertNull(row.getPasskeyEnc());
        assertNotNull(row.getCallbackTokenEnc(), "callback token is not a Daraja credential and stays");
        assertEquals(MpesaSettingSource.ENVIRONMENT, cleared.sources().get("consumerKey"));
        assertEquals(MpesaSettingSource.ENVIRONMENT, cleared.sources().get("passkey"));
        assertEquals("test-consumer-key", configProvider.current().consumerKey());
        assertEquals("ops@streamx.test", cleared.updatedBy());
    }

    @Test
    void checkoutUsesEffectiveConfiguration() {
        properties.setConsumerKey("");
        configProvider.invalidate();
        UUID accountId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        CheckoutRequest checkout = new CheckoutRequest(planId.toString(), "0712345678");
        assertThrows(ServiceUnavailableException.class, () -> billingService.checkout(accountId.toString(), checkout));

        settingsService.updateSettings(request(DB_KEY, null, null), ADMIN);
        when(subscriptionClient.getPlan(planId))
                .thenReturn(new PlanDetails(planId, "Premium", new BigDecimal("999"), "KES", true));
        when(mpesaGateway.initiateStkPush(anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new StkPushResult("29115-1", "ws_CO_settings", "0", "Success", "Success"));

        assertEquals("ws_CO_settings", transactionRepository.findById(UUID.fromString(
                billingService.checkout(accountId.toString(), checkout).getId())).orElseThrow().getCheckoutRequestId());
    }

    @Test
    void rejectsCallbackUrlWithQueryOrWithoutHost() {
        UpdateMpesaSettingsRequest withQuery = request(null, null, null);
        withQuery.setCallbackBaseUrl("https://billing.test/callback?x=1");
        assertThrows(BadRequestException.class, () -> settingsService.updateSettings(withQuery, ADMIN));

        UpdateMpesaSettingsRequest noHost = request(null, null, null);
        noHost.setCallbackBaseUrl("https:///callback");
        assertThrows(BadRequestException.class, () -> settingsService.updateSettings(noHost, ADMIN));

        UpdateMpesaSettingsRequest userInfo = request(null, null, null);
        userInfo.setCallbackBaseUrl("https://user:pw@billing.test/callback");
        assertThrows(BadRequestException.class, () -> settingsService.updateSettings(userInfo, ADMIN));
        assertTrue(settingsRepository.findAll().isEmpty());
    }

    @Test
    void connectionTestReportsResultWithoutPaying() {
        MpesaConnectionTestResponse ok = settingsService.testConnection();
        assertTrue(ok.ok());
        assertEquals("Connected to M-Pesa sandbox", ok.message());

        doThrow(new MpesaException("M-Pesa authentication failed (HTTP 400: Invalid Authentication passed). "
                + "Check the consumer key, consumer secret and environment.")).when(mpesaGateway).verifyCredentials();
        MpesaConnectionTestResponse failed = settingsService.testConnection();
        assertFalse(failed.ok());
        assertTrue(failed.message().contains("Invalid Authentication passed"));

        verify(mpesaGateway, never()).initiateStkPush(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void connectionTestWithoutCredentialsDoesNotCallDaraja() {
        properties.setConsumerKey(null);
        configProvider.invalidate();

        MpesaConnectionTestResponse result = settingsService.testConnection();

        assertFalse(result.ok());
        assertEquals("M-Pesa is not configured yet: add the consumer key and consumer secret", result.message());
        verify(mpesaGateway, never()).verifyCredentials();
    }

    @Test
    void httpResponsesNeverContainSecretsOrCallbackToken() throws Exception {
        String body = """
                {"environment":"sandbox","shortcode":"174379","transactionType":"CustomerPayBillOnline",
                 "callbackBaseUrl":"https://billing.test/api/v1/billing/mpesa/callback",
                 "consumerKey":"%s","consumerSecret":"%s","passkey":"%s","regenerateCallbackToken":true}"""
                .formatted(DB_KEY, DB_SECRET, DB_PASSKEY);

        String putResponse = mockMvc.perform(put("/api/v1/billing/admin/mpesa-settings")
                        .header("X-User-Email", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.consumerKeySet").value(true))
                .andExpect(jsonPath("$.data.consumerKeyHint").value("••••" + DB_KEY.substring(DB_KEY.length() - 4)))
                .andExpect(jsonPath("$.data.consumerSecretSet").value(true))
                .andExpect(jsonPath("$.data.passkeySet").value(true))
                .andExpect(jsonPath("$.data.callbackTokenSet").value(true))
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.sources.consumerKey").value("DATABASE"))
                .andExpect(jsonPath("$.data.sources.callbackToken").value("DATABASE"))
                .andExpect(jsonPath("$.data.updatedBy").value(ADMIN))
                .andExpect(jsonPath("$.data.consumerKey").doesNotExist())
                .andExpect(jsonPath("$.data.callbackToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String getResponse = mockMvc.perform(get("/api/v1/billing/admin/mpesa-settings"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String deleteResponse = mockMvc.perform(delete("/api/v1/billing/admin/mpesa-settings/secrets")
                        .header("X-User-Email", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sources.consumerKey").value("ENVIRONMENT"))
                .andReturn().getResponse().getContentAsString();

        String callbackToken = cipher.decrypt(storedRow().getCallbackTokenEnc());
        MpesaSettings row = storedRow();
        for (String response : List.of(putResponse, getResponse, deleteResponse)) {
            assertFalse(response.contains(DB_KEY), response);
            assertFalse(response.contains(DB_SECRET), response);
            assertFalse(response.contains(DB_PASSKEY), response);
            assertFalse(response.contains(callbackToken), response);
            assertFalse(response.contains("test-consumer-secret"), response);
            assertFalse(response.contains("test-passkey"), response);
            assertFalse(response.contains(row.getCallbackTokenEnc()), response);
        }
    }
}
