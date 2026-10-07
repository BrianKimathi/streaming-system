package com.streamx.billing.mpesa;

import com.streamx.billing.config.MpesaProperties;
import com.streamx.billing.config.SettingsCipher;
import com.streamx.billing.domain.MpesaSettings;
import com.streamx.billing.repository.MpesaSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MpesaConfigProviderTest {

    private MpesaProperties properties;
    private MpesaSettingsRepository repository;
    private SettingsCipher cipher;
    private MpesaConfigProvider provider;

    @BeforeEach
    void setUp() {
        properties = new MpesaProperties();
        repository = mock(MpesaSettingsRepository.class);
        when(repository.findById(MpesaSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        cipher = new SettingsCipher("provider-test-encryption-key-0123456789abcdef", "");
        provider = new MpesaConfigProvider(properties, repository, cipher);
    }

    private void fullEnvironment() {
        properties.setEnvironment("sandbox");
        properties.setConsumerKey("env-key");
        properties.setConsumerSecret("env-secret");
        properties.setShortcode("174379");
        properties.setPasskey("env-passkey");
        properties.setTransactionType("CustomerPayBillOnline");
        properties.setCallbackBaseUrl("https://env.billing.test/callback");
        properties.setCallbackToken("env-token");
    }

    @Test
    void usesEnvironmentWhenNothingIsStored() {
        fullEnvironment();

        MpesaConfig config = provider.current();

        assertTrue(config.isConfigured());
        assertEquals("env-key", config.consumerKey());
        assertEquals("env-token", config.callbackToken());
        assertEquals(MpesaConfig.SANDBOX_BASE_URL, config.baseUrl());
        assertEquals("https://env.billing.test/callback/env-token", config.callbackUrl());
        assertTrue(config.sources().values().stream().allMatch(s -> s == MpesaSettingSource.ENVIRONMENT));
        assertEquals(8, config.sources().size());
    }

    @Test
    void databaseOverridesEnvironmentPerField() {
        fullEnvironment();
        MpesaSettings stored = new MpesaSettings();
        stored.setEnvironment("production");
        stored.setShortcode("600987");
        stored.setConsumerKeyEnc(cipher.encrypt("db-key"));
        stored.setPasskeyEnc(cipher.encrypt("db-passkey"));
        when(repository.findById(MpesaSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));

        MpesaConfig config = provider.current();

        assertEquals("production", config.environment());
        assertEquals(MpesaConfig.PRODUCTION_BASE_URL, config.baseUrl());
        assertEquals("600987", config.shortcode());
        assertEquals("db-key", config.consumerKey());
        assertEquals("env-secret", config.consumerSecret());
        assertEquals("db-passkey", config.passkey());
        assertEquals("env-token", config.callbackToken());
        assertEquals(MpesaSettingSource.DATABASE, config.sources().get("environment"));
        assertEquals(MpesaSettingSource.DATABASE, config.sources().get("shortcode"));
        assertEquals(MpesaSettingSource.DATABASE, config.sources().get("consumerKey"));
        assertEquals(MpesaSettingSource.ENVIRONMENT, config.sources().get("consumerSecret"));
        assertEquals(MpesaSettingSource.DATABASE, config.sources().get("passkey"));
        assertEquals(MpesaSettingSource.ENVIRONMENT, config.sources().get("callbackToken"));
        assertEquals(MpesaSettingSource.ENVIRONMENT, config.sources().get("callbackBaseUrl"));
        assertTrue(config.isConfigured());
    }

    @Test
    void nothingConfiguredReportsNone() {
        properties.setEnvironment("");
        properties.setTransactionType(" ");

        MpesaConfig config = provider.current();

        assertFalse(config.isConfigured());
        assertFalse(config.hasCredentials());
        assertNull(config.consumerKey());
        assertNull(config.callbackToken());
        assertEquals("sandbox", config.environment());
        assertEquals("CustomerPayBillOnline", config.transactionType());
        assertTrue(config.sources().values().stream().allMatch(s -> s == MpesaSettingSource.NONE));
    }

    @Test
    void blankValuesAreTreatedAsUnset() {
        fullEnvironment();
        properties.setPasskey("   ");
        MpesaSettings stored = new MpesaSettings();
        stored.setShortcode("  ");
        when(repository.findById(MpesaSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));

        MpesaConfig config = provider.current();

        assertEquals("174379", config.shortcode());
        assertEquals(MpesaSettingSource.ENVIRONMENT, config.sources().get("shortcode"));
        assertNull(config.passkey());
        assertEquals(MpesaSettingSource.NONE, config.sources().get("passkey"));
        assertFalse(config.isConfigured());
    }

    @Test
    void cachesUntilInvalidated() {
        fullEnvironment();

        MpesaConfig first = provider.current();
        assertSame(first, provider.current());
        verify(repository, times(1)).findById(MpesaSettings.SINGLETON_ID);

        MpesaSettings stored = new MpesaSettings();
        stored.setConsumerKeyEnc(cipher.encrypt("rotated-key"));
        when(repository.findById(MpesaSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));
        assertEquals("env-key", provider.current().consumerKey());

        provider.invalidate();
        assertEquals("rotated-key", provider.current().consumerKey());
        verify(repository, times(2)).findById(MpesaSettings.SINGLETON_ID);
    }

    @Test
    void undecryptableStoredValueFallsBackToEnvironment() {
        fullEnvironment();
        SettingsCipher otherKey = new SettingsCipher("a-different-encryption-key-0123456789abcdef", "");
        MpesaSettings stored = new MpesaSettings();
        stored.setConsumerSecretEnc(otherKey.encrypt("db-secret"));
        when(repository.findById(MpesaSettings.SINGLETON_ID)).thenReturn(Optional.of(stored));

        MpesaConfig config = provider.current();

        assertEquals("env-secret", config.consumerSecret());
        assertEquals(MpesaSettingSource.ENVIRONMENT, config.sources().get("consumerSecret"));
    }

    @Test
    void toStringNeverContainsSecrets() {
        fullEnvironment();

        String text = provider.current().toString();

        assertFalse(text.contains("env-key"));
        assertFalse(text.contains("env-secret"));
        assertFalse(text.contains("env-passkey"));
        assertFalse(text.contains("env-token"));
    }
}
