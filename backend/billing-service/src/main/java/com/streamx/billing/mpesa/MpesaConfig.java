package com.streamx.billing.mpesa;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Effective Daraja configuration (database value per field, otherwise environment). Values are trimmed; blank
 * values are {@code null}. {@link #toString()} never includes secrets.
 */
public record MpesaConfig(String environment,
                          String consumerKey,
                          String consumerSecret,
                          String shortcode,
                          String passkey,
                          String transactionType,
                          String callbackBaseUrl,
                          String callbackToken,
                          Map<String, MpesaSettingSource> sources) {

    public static final String SANDBOX = "sandbox";
    public static final String PRODUCTION = "production";
    public static final String SANDBOX_BASE_URL = "https://sandbox.safaricom.co.ke";
    public static final String PRODUCTION_BASE_URL = "https://api.safaricom.co.ke";

    public static final String FIELD_ENVIRONMENT = "environment";
    public static final String FIELD_SHORTCODE = "shortcode";
    public static final String FIELD_TRANSACTION_TYPE = "transactionType";
    public static final String FIELD_CALLBACK_BASE_URL = "callbackBaseUrl";
    public static final String FIELD_CONSUMER_KEY = "consumerKey";
    public static final String FIELD_CONSUMER_SECRET = "consumerSecret";
    public static final String FIELD_PASSKEY = "passkey";
    public static final String FIELD_CALLBACK_TOKEN = "callbackToken";

    public MpesaConfig {
        sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources == null ? Map.of() : sources));
    }

    public boolean isConfigured() {
        return hasCredentials() && hasText(shortcode) && hasText(passkey)
                && hasText(callbackToken) && hasText(callbackBaseUrl);
    }

    public boolean hasCredentials() {
        return hasText(consumerKey) && hasText(consumerSecret);
    }

    public boolean isProduction() {
        return PRODUCTION.equalsIgnoreCase(environment);
    }

    public String environmentLabel() {
        return isProduction() ? PRODUCTION : SANDBOX;
    }

    public String baseUrl() {
        return isProduction() ? PRODUCTION_BASE_URL : SANDBOX_BASE_URL;
    }

    public String callbackUrl() {
        String base = callbackBaseUrl == null ? "" : callbackBaseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + (callbackToken == null ? "" : callbackToken);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "MpesaConfig[environment=" + environmentLabel() + ", shortcode=" + shortcode
                + ", transactionType=" + transactionType + ", configured=" + isConfigured()
                + ", sources=" + sources + "]";
    }
}
