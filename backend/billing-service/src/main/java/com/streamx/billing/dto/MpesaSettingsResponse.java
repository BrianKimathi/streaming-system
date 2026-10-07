package com.streamx.billing.dto;

import com.streamx.billing.mpesa.MpesaSettingSource;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Effective Daraja settings for the admin panel. Secrets are only reported as "set" flags (plus a consumer key hint).
 */
public record MpesaSettingsResponse(String environment,
                                    String shortcode,
                                    String transactionType,
                                    String callbackBaseUrl,
                                    boolean consumerKeySet,
                                    String consumerKeyHint,
                                    boolean consumerSecretSet,
                                    boolean passkeySet,
                                    boolean callbackTokenSet,
                                    boolean configured,
                                    Map<String, MpesaSettingSource> sources,
                                    LocalDateTime updatedAt,
                                    String updatedBy) {
}
