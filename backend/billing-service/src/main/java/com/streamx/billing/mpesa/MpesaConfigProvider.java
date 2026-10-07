package com.streamx.billing.mpesa;

import com.streamx.billing.config.MpesaProperties;
import com.streamx.billing.config.SettingsCipher;
import com.streamx.billing.domain.MpesaSettings;
import com.streamx.billing.repository.MpesaSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.streamx.billing.mpesa.MpesaConfig.*;

/**
 * Resolves the effective Daraja configuration: the admin-managed database value per field when set, otherwise the
 * environment ({@link MpesaProperties}). The result is cached in memory until {@link #invalidate()}.
 */
@Component
public class MpesaConfigProvider {

    private static final Logger log = LoggerFactory.getLogger(MpesaConfigProvider.class);

    static final String DEFAULT_TRANSACTION_TYPE = "CustomerPayBillOnline";

    private final MpesaProperties properties;
    private final MpesaSettingsRepository repository;
    private final SettingsCipher cipher;

    private volatile MpesaConfig cached;

    public MpesaConfigProvider(MpesaProperties properties, MpesaSettingsRepository repository, SettingsCipher cipher) {
        this.properties = properties;
        this.repository = repository;
        this.cipher = cipher;
    }

    public MpesaConfig current() {
        MpesaConfig config = cached;
        if (config != null) {
            return config;
        }
        synchronized (this) {
            if (cached == null) {
                cached = load();
            }
            return cached;
        }
    }

    public synchronized void invalidate() {
        cached = null;
    }

    /**
     * Decrypts a stored secret; returns {@code null} (and logs the field name only) when it can't be decrypted.
     */
    public String decryptOrNull(String field, String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return clean(cipher.decrypt(stored));
        } catch (SettingsCipher.SettingsDecryptionException e) {
            log.error("Stored M-Pesa {} could not be decrypted (was SETTINGS_ENCRYPTION_KEY changed?); "
                    + "ignoring it until it is saved again", field);
            return null;
        }
    }

    private MpesaConfig load() {
        MpesaSettings stored = repository.findById(MpesaSettings.SINGLETON_ID).orElse(null);
        boolean hasRow = stored != null;
        Map<String, MpesaSettingSource> sources = new LinkedHashMap<>();

        String environment = pick(FIELD_ENVIRONMENT, hasRow ? stored.getEnvironment() : null,
                properties.getEnvironment(), sources);
        String shortcode = pick(FIELD_SHORTCODE, hasRow ? stored.getShortcode() : null,
                properties.getShortcode(), sources);
        String transactionType = pick(FIELD_TRANSACTION_TYPE, hasRow ? stored.getTransactionType() : null,
                properties.getTransactionType(), sources);
        String callbackBaseUrl = pick(FIELD_CALLBACK_BASE_URL, hasRow ? stored.getCallbackBaseUrl() : null,
                properties.getCallbackBaseUrl(), sources);
        String consumerKey = pick(FIELD_CONSUMER_KEY,
                hasRow ? decryptOrNull(FIELD_CONSUMER_KEY, stored.getConsumerKeyEnc()) : null,
                properties.getConsumerKey(), sources);
        String consumerSecret = pick(FIELD_CONSUMER_SECRET,
                hasRow ? decryptOrNull(FIELD_CONSUMER_SECRET, stored.getConsumerSecretEnc()) : null,
                properties.getConsumerSecret(), sources);
        String passkey = pick(FIELD_PASSKEY,
                hasRow ? decryptOrNull(FIELD_PASSKEY, stored.getPasskeyEnc()) : null,
                properties.getPasskey(), sources);
        String callbackToken = pick(FIELD_CALLBACK_TOKEN,
                hasRow ? decryptOrNull(FIELD_CALLBACK_TOKEN, stored.getCallbackTokenEnc()) : null,
                properties.getCallbackToken(), sources);

        return new MpesaConfig(
                environment != null ? environment : SANDBOX,
                consumerKey,
                consumerSecret,
                shortcode,
                passkey,
                transactionType != null ? transactionType : DEFAULT_TRANSACTION_TYPE,
                callbackBaseUrl,
                callbackToken,
                sources);
    }

    private static String pick(String field, String databaseValue, String environmentValue,
                               Map<String, MpesaSettingSource> sources) {
        String fromDatabase = clean(databaseValue);
        if (fromDatabase != null) {
            sources.put(field, MpesaSettingSource.DATABASE);
            return fromDatabase;
        }
        String fromEnvironment = clean(environmentValue);
        sources.put(field, fromEnvironment != null ? MpesaSettingSource.ENVIRONMENT : MpesaSettingSource.NONE);
        return fromEnvironment;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
