package com.streamx.billing.mpesa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safaricom Daraja HTTP client: OAuth (cached), STK Push and STK Push Query.
 */
@Component
public class MpesaClient implements MpesaGateway {

    private static final Logger log = LoggerFactory.getLogger(MpesaClient.class);

    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String STILL_PROCESSING_ERROR_CODE = "500.001.1001";
    private static final String STILL_PROCESSING_RESULT_CODE = "4999";
    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofSeconds(60);

    private final MpesaConfigProvider configProvider;
    private final RestClient restClient;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String cachedToken;
    private String cachedTokenScope;
    private Instant cachedTokenExpiresAt = Instant.EPOCH;

    @Autowired
    public MpesaClient(MpesaConfigProvider configProvider) {
        this(configProvider, RestClient.builder().requestFactory(requestFactory()), Clock.systemUTC());
    }

    MpesaClient(MpesaConfigProvider configProvider, RestClient.Builder builder, Clock clock) {
        this.configProvider = configProvider;
        this.restClient = builder.build();
        this.clock = clock;
    }

    static String limit(String value, int maxLength, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength).trim();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return factory;
    }

    @Override
    public StkPushResult initiateStkPush(String phoneNumber, long amount, String accountReference, String description) {
        MpesaConfig config = requireConfigured();
        String timestamp = timestamp(ZonedDateTime.now(clock));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", config.shortcode());
        body.put("Password", password(config.shortcode(), config.passkey(), timestamp));
        body.put("Timestamp", timestamp);
        body.put("TransactionType", config.transactionType());
        body.put("Amount", amount);
        body.put("PartyA", phoneNumber);
        body.put("PartyB", config.shortcode());
        body.put("PhoneNumber", phoneNumber);
        body.put("CallBackURL", config.callbackUrl());
        // Daraja rejects AccountReference longer than 12 and TransactionDesc longer than 13 characters.
        body.put("AccountReference", limit(accountReference, 12, "StreamX"));
        body.put("TransactionDesc", limit(description, 13, "StreamX plan"));

        JsonNode response;
        try {
            response = postAuthorized(config, "/mpesa/stkpush/v1/processrequest", body);
        } catch (RestClientResponseException e) {
            String message = darajaErrorMessage(e);
            log.warn("Daraja STK Push rejected (HTTP {}): {}", e.getStatusCode().value(), message);
            throw new MpesaException(message, e);
        } catch (RestClientException e) {
            log.warn("Could not reach Daraja for STK Push: {}", e.getMessage());
            throw new MpesaException("Could not reach M-Pesa. Please try again shortly.", e);
        }

        StkPushResult result = new StkPushResult(
                text(response, "MerchantRequestID"),
                text(response, "CheckoutRequestID"),
                text(response, "ResponseCode"),
                text(response, "ResponseDescription"),
                text(response, "CustomerMessage"));
        log.info("Daraja STK Push response code={} checkoutRequestId={}", result.responseCode(), result.checkoutRequestId());
        return result;
    }

    @Override
    public StkQueryResult queryStkPush(String checkoutRequestId) {
        MpesaConfig config = requireConfigured();
        String timestamp = timestamp(ZonedDateTime.now(clock));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", config.shortcode());
        body.put("Password", password(config.shortcode(), config.passkey(), timestamp));
        body.put("Timestamp", timestamp);
        body.put("CheckoutRequestID", checkoutRequestId);

        JsonNode response;
        try {
            response = postAuthorized(config, "/mpesa/stkpushquery/v1/query", body);
        } catch (RestClientResponseException e) {
            JsonNode error = parse(e.getResponseBodyAsString());
            if (STILL_PROCESSING_ERROR_CODE.equals(text(error, "errorCode"))) {
                return StkQueryResult.stillProcessing(text(error, "errorMessage"));
            }
            String message = darajaErrorMessage(e);
            log.warn("Daraja STK query failed (HTTP {}): {}", e.getStatusCode().value(), message);
            throw new MpesaException(message, e);
        } catch (RestClientException e) {
            throw new MpesaException("Could not reach M-Pesa. Please try again shortly.", e);
        }

        String resultCode = text(response, "ResultCode");
        String resultDesc = text(response, "ResultDesc");
        if (resultCode == null || STILL_PROCESSING_RESULT_CODE.equals(resultCode)) {
            return StkQueryResult.stillProcessing(resultDesc);
        }
        return StkQueryResult.completed(resultCode, resultDesc);
    }

    @Override
    public void verifyCredentials() {
        MpesaConfig config = configProvider.current();
        invalidateToken();
        accessToken(config);
    }

    @Override
    public void resetAuthentication() {
        invalidateToken();
    }

    private MpesaConfig requireConfigured() {
        MpesaConfig config = configProvider.current();
        if (!config.isConfigured()) {
            throw new MpesaException("M-Pesa payments are not configured yet");
        }
        return config;
    }

    private JsonNode postAuthorized(MpesaConfig config, String path, Map<String, Object> body) {
        String token = accessToken(config);
        try {
            return restClient.post()
                    .uri(config.baseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                invalidateToken();
            }
            throw e;
        }
    }

    /**
     * Returns a cached OAuth token when it was issued for the same environment and credentials and has not expired.
     */
    synchronized String accessToken(MpesaConfig config) {
        if (!config.hasCredentials()) {
            throw new MpesaException("M-Pesa consumer key and secret are not configured");
        }
        Instant now = clock.instant();
        String credentials = config.consumerKey() + ":" + config.consumerSecret();
        String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        String scope = config.baseUrl() + " " + basic;
        if (cachedToken != null && now.isBefore(cachedTokenExpiresAt) && scope.equals(cachedTokenScope)) {
            return cachedToken;
        }
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(config.baseUrl() + "/oauth/v1/generate?grant_type=client_credentials")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            String detail = oauthErrorDetail(e);
            log.error("Daraja OAuth failed ({} environment): {}", config.environmentLabel(), detail);
            throw new MpesaException("M-Pesa authentication failed (" + detail
                    + "). Check the consumer key, consumer secret and environment.", e);
        } catch (RestClientException e) {
            log.warn("Could not reach Daraja for OAuth: {}", e.getMessage());
            throw new MpesaException("Could not reach M-Pesa. Please try again shortly.", e);
        }
        String token = text(response, "access_token");
        if (token == null || token.isBlank()) {
            throw new MpesaException("M-Pesa authentication failed: no access token returned.");
        }
        long expiresIn = response.path("expires_in").asLong(3599);
        cachedToken = token;
        cachedTokenScope = scope;
        cachedTokenExpiresAt = now.plusSeconds(expiresIn).minus(TOKEN_EXPIRY_MARGIN);
        return token;
    }

    private synchronized void invalidateToken() {
        cachedToken = null;
        cachedTokenScope = null;
        cachedTokenExpiresAt = Instant.EPOCH;
    }

    private String oauthErrorDetail(RestClientResponseException e) {
        JsonNode error = parse(e.getResponseBodyAsString());
        String message = text(error, "errorMessage");
        if (message == null) {
            message = text(error, "resultDesc");
        }
        if (message == null) {
            message = text(error, "error_description");
        }
        String status = "HTTP " + e.getStatusCode().value();
        return message == null || message.isBlank() ? status : status + ": " + message;
    }

    public static String timestamp(ZonedDateTime at) {
        return at.withZoneSameInstant(NAIROBI).format(TIMESTAMP_FORMAT);
    }

    public static String password(String shortcode, String passkey, String timestamp) {
        return Base64.getEncoder().encodeToString((shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));
    }

    private String darajaErrorMessage(RestClientResponseException e) {
        JsonNode error = parse(e.getResponseBodyAsString());
        String message = text(error, "errorMessage");
        if (message == null) {
            message = text(error, "ResponseDescription");
        }
        return message != null ? message : "M-Pesa rejected the request (HTTP " + e.getStatusCode().value() + ")";
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
