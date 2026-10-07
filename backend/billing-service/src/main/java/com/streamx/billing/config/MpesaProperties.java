package com.streamx.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mpesa")
public class MpesaProperties {

    static final String SANDBOX_BASE_URL = "https://sandbox.safaricom.co.ke";
    static final String PRODUCTION_BASE_URL = "https://api.safaricom.co.ke";

    private String environment = "sandbox";
    private String consumerKey;
    private String consumerSecret;
    private String shortcode;
    private String passkey;
    private String transactionType = "CustomerPayBillOnline";
    private String callbackBaseUrl;
    private String callbackToken;

    public boolean isConfigured() {
        return hasText(consumerKey) && hasText(consumerSecret) && hasText(shortcode)
                && hasText(passkey) && hasText(callbackToken) && hasText(callbackBaseUrl);
    }

    public String getBaseUrl() {
        return "production".equalsIgnoreCase(trim(environment)) ? PRODUCTION_BASE_URL : SANDBOX_BASE_URL;
    }

    public String getCallbackUrl() {
        String base = trim(callbackBaseUrl);
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + trim(callbackToken);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String getShortcode() {
        return shortcode;
    }

    public void setShortcode(String shortcode) {
        this.shortcode = shortcode;
    }

    public String getPasskey() {
        return passkey;
    }

    public void setPasskey(String passkey) {
        this.passkey = passkey;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }

    public void setCallbackBaseUrl(String callbackBaseUrl) {
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public String getCallbackToken() {
        return callbackToken;
    }

    public void setCallbackToken(String callbackToken) {
        this.callbackToken = callbackToken;
    }
}
