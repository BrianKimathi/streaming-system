package com.streamx.billing.service;

import com.streamx.billing.config.MpesaProperties;
import com.streamx.billing.config.SettingsCipher;
import com.streamx.billing.domain.MpesaSettings;
import com.streamx.billing.dto.MpesaConnectionTestResponse;
import com.streamx.billing.dto.MpesaSettingsResponse;
import com.streamx.billing.dto.UpdateMpesaSettingsRequest;
import com.streamx.billing.mpesa.MpesaConfig;
import com.streamx.billing.mpesa.MpesaConfigProvider;
import com.streamx.billing.mpesa.MpesaException;
import com.streamx.billing.mpesa.MpesaGateway;
import com.streamx.billing.repository.MpesaSettingsRepository;
import com.streamx.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Admin management of the Daraja settings stored in {@code mpesa_settings}. Secret values are encrypted at rest and
 * never returned or logged.
 */
@Service
public class MpesaSettingsService {

    private static final Logger log = LoggerFactory.getLogger(MpesaSettingsService.class);

    static final String HINT_PREFIX = "••••";
    private static final int CALLBACK_TOKEN_BYTES = 24;
    private static final int MAX_UPDATED_BY_LENGTH = 255;
    private static final String INVALID_CALLBACK_URL_MESSAGE =
            "Callback base URL must be a valid https URL without a query string or fragment";

    private final MpesaSettingsRepository repository;
    private final MpesaConfigProvider configProvider;
    private final MpesaProperties properties;
    private final SettingsCipher cipher;
    private final MpesaGateway mpesaGateway;
    private final SecureRandom random = new SecureRandom();

    public MpesaSettingsService(MpesaSettingsRepository repository,
                                MpesaConfigProvider configProvider,
                                MpesaProperties properties,
                                SettingsCipher cipher,
                                MpesaGateway mpesaGateway) {
        this.repository = repository;
        this.configProvider = configProvider;
        this.properties = properties;
        this.cipher = cipher;
        this.mpesaGateway = mpesaGateway;
    }

    public MpesaSettingsResponse getSettings() {
        MpesaConfig config = configProvider.current();
        MpesaSettings stored = repository.findById(MpesaSettings.SINGLETON_ID).orElse(null);
        return new MpesaSettingsResponse(
                config.environment(),
                config.shortcode(),
                config.transactionType(),
                config.callbackBaseUrl(),
                config.consumerKey() != null,
                hint(config.consumerKey()),
                config.consumerSecret() != null,
                config.passkey() != null,
                config.callbackToken() != null,
                config.isConfigured(),
                config.sources(),
                stored != null ? stored.getUpdatedAt() : null,
                stored != null ? stored.getUpdatedBy() : null);
    }

    public synchronized MpesaSettingsResponse updateSettings(UpdateMpesaSettingsRequest request, String updatedBy) {
        String callbackBaseUrl = normalizeCallbackBaseUrl(request.getCallbackBaseUrl());

        MpesaSettings settings = repository.findById(MpesaSettings.SINGLETON_ID).orElseGet(MpesaSettings::new);
        settings.setEnvironment(request.getEnvironment().trim());
        settings.setShortcode(request.getShortcode().trim());
        settings.setTransactionType(request.getTransactionType().trim());
        settings.setCallbackBaseUrl(callbackBaseUrl);

        boolean consumerKeyChanged = hasText(request.getConsumerKey());
        boolean consumerSecretChanged = hasText(request.getConsumerSecret());
        boolean passkeyChanged = hasText(request.getPasskey());
        if (consumerKeyChanged) {
            settings.setConsumerKeyEnc(cipher.encrypt(request.getConsumerKey().trim()));
        }
        if (consumerSecretChanged) {
            settings.setConsumerSecretEnc(cipher.encrypt(request.getConsumerSecret().trim()));
        }
        if (passkeyChanged) {
            settings.setPasskeyEnc(cipher.encrypt(request.getPasskey().trim()));
        }

        boolean regenerate = Boolean.TRUE.equals(request.getRegenerateCallbackToken());
        boolean tokenAvailable = configProvider.decryptOrNull(MpesaConfig.FIELD_CALLBACK_TOKEN,
                settings.getCallbackTokenEnc()) != null || hasText(properties.getCallbackToken());
        boolean tokenGenerated = regenerate || !tokenAvailable;
        if (tokenGenerated) {
            settings.setCallbackTokenEnc(cipher.encrypt(newCallbackToken()));
        }
        settings.setUpdatedBy(cleanUpdatedBy(updatedBy));

        repository.save(settings);
        settingsChanged();
        log.info("M-Pesa settings updated by {} (environment={}, shortcode={}, transactionType={}, consumerKeyChanged={}, "
                        + "consumerSecretChanged={}, passkeyChanged={}, callbackTokenGenerated={})",
                settings.getUpdatedBy(), settings.getEnvironment(), settings.getShortcode(),
                settings.getTransactionType(), consumerKeyChanged, consumerSecretChanged, passkeyChanged,
                tokenGenerated);
        return getSettings();
    }

    /**
     * Clears the database-stored consumer key, consumer secret and passkey; the environment values apply again.
     */
    public synchronized MpesaSettingsResponse clearStoredSecrets(String updatedBy) {
        MpesaSettings settings = repository.findById(MpesaSettings.SINGLETON_ID).orElse(null);
        if (settings != null) {
            settings.setConsumerKeyEnc(null);
            settings.setConsumerSecretEnc(null);
            settings.setPasskeyEnc(null);
            settings.setUpdatedBy(cleanUpdatedBy(updatedBy));
            repository.save(settings);
            settingsChanged();
            log.info("Stored M-Pesa consumer key, consumer secret and passkey cleared by {}", settings.getUpdatedBy());
        }
        return getSettings();
    }

    /**
     * Requests a Daraja OAuth token with the effective settings. Never initiates a payment.
     */
    public MpesaConnectionTestResponse testConnection() {
        MpesaConfig config = configProvider.current();
        if (!config.hasCredentials()) {
            return new MpesaConnectionTestResponse(false,
                    "M-Pesa is not configured yet: add the consumer key and consumer secret");
        }
        try {
            mpesaGateway.verifyCredentials();
            return new MpesaConnectionTestResponse(true, "Connected to M-Pesa " + config.environmentLabel());
        } catch (MpesaException e) {
            return new MpesaConnectionTestResponse(false, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("M-Pesa connection test failed unexpectedly: {}", e.getMessage());
            return new MpesaConnectionTestResponse(false, "Could not reach M-Pesa. Please try again shortly.");
        }
    }

    private void settingsChanged() {
        configProvider.invalidate();
        mpesaGateway.resetAuthentication();
    }

    private String newCallbackToken() {
        byte[] bytes = new byte[CALLBACK_TOKEN_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String hint(String consumerKey) {
        if (consumerKey == null || consumerKey.isBlank()) {
            return null;
        }
        if (consumerKey.length() <= 4) {
            return HINT_PREFIX;
        }
        return HINT_PREFIX + consumerKey.substring(consumerKey.length() - 4);
    }

    static String normalizeCallbackBaseUrl(String value) {
        if (value == null) {
            throw new BadRequestException(INVALID_CALLBACK_URL_MESSAGE);
        }
        String url = value.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BadRequestException(INVALID_CALLBACK_URL_MESSAGE);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new BadRequestException(INVALID_CALLBACK_URL_MESSAGE);
        }
        return url;
    }

    private static String cleanUpdatedBy(String updatedBy) {
        if (updatedBy == null || updatedBy.isBlank()) {
            return null;
        }
        String trimmed = updatedBy.trim();
        return trimmed.length() <= MAX_UPDATED_BY_LENGTH ? trimmed : trimmed.substring(0, MAX_UPDATED_BY_LENGTH);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
