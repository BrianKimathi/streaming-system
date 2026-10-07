package com.streamx.billing.mpesa;

import com.streamx.billing.config.MpesaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MpesaClientTest {

    private static final String SANDBOX = "https://sandbox.safaricom.co.ke";
    private static final String OAUTH_URL = SANDBOX + "/oauth/v1/generate?grant_type=client_credentials";
    private static final String STK_URL = SANDBOX + "/mpesa/stkpush/v1/processrequest";
    private static final String QUERY_URL = SANDBOX + "/mpesa/stkpushquery/v1/query";
    // 16:01:02 UTC is 19:01:02 in Nairobi (UTC+3).
    private static final Instant NOW = Instant.parse("2026-10-07T16:01:02Z");
    private static final String EXPECTED_TIMESTAMP = "20261007190102";

    private MockRestServiceServer server;
    private MpesaClient client;

    @Test
    void limitsFieldsToDarajaMaximumLengths() {
        assertEquals("Premium plan", MpesaClient.limit("Premium plan", 13, "StreamX plan"));
        assertEquals("Family Ultra", MpesaClient.limit("Family Ultra HD plan", 13, "StreamX plan"));
        assertEquals("StreamX plan", MpesaClient.limit("  ", 13, "StreamX plan"));
        assertEquals("StreamX", MpesaClient.limit(null, 12, "StreamX"));
    }

    @BeforeEach
    void setUp() {
        MpesaProperties properties = new MpesaProperties();
        properties.setEnvironment("sandbox");
        properties.setConsumerKey("key");
        properties.setConsumerSecret("secret");
        properties.setShortcode("174379");
        properties.setPasskey("passkey");
        properties.setCallbackBaseUrl("https://billing.test/callback/");
        properties.setCallbackToken("cb-token");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MpesaClient(properties, builder, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void expectOauth() {
        String basic = Base64.getEncoder().encodeToString("key:secret".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(OAUTH_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic " + basic))
                .andRespond(withSuccess("{\"access_token\":\"tok123\",\"expires_in\":\"3599\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void timestampUsesNairobiTimeAndPasswordIsBase64() {
        assertEquals(EXPECTED_TIMESTAMP, MpesaClient.timestamp(ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC)));
        String password = MpesaClient.password("174379", "passkey", EXPECTED_TIMESTAMP);
        assertEquals("174379passkey" + EXPECTED_TIMESTAMP,
                new String(Base64.getDecoder().decode(password), StandardCharsets.UTF_8));
    }

    @Test
    void stkPushSendsDarajaPayloadAndCachesToken() {
        expectOauth();
        String expectedPassword = MpesaClient.password("174379", "passkey", EXPECTED_TIMESTAMP);
        String success = """
                {"MerchantRequestID":"29115-1","CheckoutRequestID":"ws_CO_1","ResponseCode":"0",
                 "ResponseDescription":"Success. Request accepted for processing","CustomerMessage":"Success"}""";
        server.expect(requestTo(STK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok123"))
                .andExpect(jsonPath("$.BusinessShortCode").value("174379"))
                .andExpect(jsonPath("$.Password").value(expectedPassword))
                .andExpect(jsonPath("$.Timestamp").value(EXPECTED_TIMESTAMP))
                .andExpect(jsonPath("$.TransactionType").value("CustomerPayBillOnline"))
                .andExpect(jsonPath("$.Amount").value(1000))
                .andExpect(jsonPath("$.PartyA").value("254712345678"))
                .andExpect(jsonPath("$.PartyB").value("174379"))
                .andExpect(jsonPath("$.PhoneNumber").value("254712345678"))
                .andExpect(jsonPath("$.CallBackURL").value("https://billing.test/callback/cb-token"))
                .andExpect(jsonPath("$.AccountReference").value("StreamX"))
                .andExpect(jsonPath("$.TransactionDesc").value("Premium plan"))
                .andRespond(withSuccess(success, MediaType.APPLICATION_JSON));
        server.expect(requestTo(STK_URL))
                .andExpect(header("Authorization", "Bearer tok123"))
                .andRespond(withSuccess(success, MediaType.APPLICATION_JSON));

        StkPushResult first = client.initiateStkPush("254712345678", 1000, "StreamX", "Premium plan");
        StkPushResult second = client.initiateStkPush("254712345678", 1000, "StreamX", "Premium plan");

        assertTrue(first.accepted());
        assertEquals("ws_CO_1", first.checkoutRequestId());
        assertEquals("29115-1", first.merchantRequestId());
        assertTrue(second.accepted());
        server.verify();
    }

    @Test
    void stkPushRejectionSurfacesDarajaMessage() {
        expectOauth();
        server.expect(requestTo(STK_URL))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"requestId\":\"1\",\"errorCode\":\"400.002.02\",\"errorMessage\":\"Bad Request - Invalid Amount\"}"));

        MpesaException ex = assertThrows(MpesaException.class,
                () -> client.initiateStkPush("254712345678", 1000, "StreamX", "Premium plan"));
        assertEquals("Bad Request - Invalid Amount", ex.getMessage());
    }

    @Test
    void stkQueryStillProcessingIsPending() {
        expectOauth();
        server.expect(requestTo(QUERY_URL))
                .andExpect(jsonPath("$.CheckoutRequestID").value("ws_CO_1"))
                .andExpect(jsonPath("$.Timestamp").value(EXPECTED_TIMESTAMP))
                .andRespond(withServerError()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"requestId\":\"1\",\"errorCode\":\"500.001.1001\",\"errorMessage\":\"The transaction is being processed\"}"));

        StkQueryResult result = client.queryStkPush("ws_CO_1");

        assertTrue(result.pending());
    }

    @Test
    void stkQueryReturnsFinalResultCode() {
        expectOauth();
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("""
                        {"ResponseCode":"0","ResponseDescription":"The service request has been accepted successsfully",
                         "MerchantRequestID":"29115-1","CheckoutRequestID":"ws_CO_1",
                         "ResultCode":"1032","ResultDesc":"Request cancelled by user"}""", MediaType.APPLICATION_JSON));

        StkQueryResult result = client.queryStkPush("ws_CO_1");

        assertFalse(result.pending());
        assertEquals("1032", result.resultCode());
        assertEquals("Request cancelled by user", result.resultDesc());
    }

    @Test
    void oauthFailureIsReportedAsMpesaException() {
        server.expect(requestTo(OAUTH_URL))
                .andRespond(withBadRequest().body("{\"errorMessage\":\"Invalid Authentication passed\"}"));

        assertThrows(MpesaException.class, () -> client.initiateStkPush("254712345678", 10, "StreamX", "Basic plan"));
    }
}
