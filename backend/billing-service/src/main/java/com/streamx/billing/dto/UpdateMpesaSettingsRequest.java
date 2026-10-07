package com.streamx.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin update of the Daraja settings. Null or blank secret fields keep the stored value.
 */
public class UpdateMpesaSettingsRequest {

    @NotBlank(message = "Environment is required")
    @Pattern(regexp = "sandbox|production", message = "Environment must be sandbox or production")
    private String environment;

    @NotBlank(message = "Shortcode is required")
    @Pattern(regexp = "\\d{5,8}", message = "Shortcode must be 5 to 8 digits")
    private String shortcode;

    @NotBlank(message = "Transaction type is required")
    @Pattern(regexp = "CustomerPayBillOnline|CustomerBuyGoodsOnline",
            message = "Transaction type must be CustomerPayBillOnline or CustomerBuyGoodsOnline")
    private String transactionType;

    @NotBlank(message = "Callback base URL is required")
    @Size(max = 500, message = "Callback base URL must be at most 500 characters")
    @Pattern(regexp = "(?i)https://\\S+", message = "Callback base URL must be an https URL")
    private String callbackBaseUrl;

    @Size(max = 512, message = "Consumer key is too long")
    private String consumerKey;

    @Size(max = 512, message = "Consumer secret is too long")
    private String consumerSecret;

    @Size(max = 512, message = "Passkey is too long")
    private String passkey;

    private Boolean regenerateCallbackToken;

    public UpdateMpesaSettingsRequest() {
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getShortcode() {
        return shortcode;
    }

    public void setShortcode(String shortcode) {
        this.shortcode = shortcode;
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

    public String getPasskey() {
        return passkey;
    }

    public void setPasskey(String passkey) {
        this.passkey = passkey;
    }

    public Boolean getRegenerateCallbackToken() {
        return regenerateCallbackToken;
    }

    public void setRegenerateCallbackToken(Boolean regenerateCallbackToken) {
        this.regenerateCallbackToken = regenerateCallbackToken;
    }
}
