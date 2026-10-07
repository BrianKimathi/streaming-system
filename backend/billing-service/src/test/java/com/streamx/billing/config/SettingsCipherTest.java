package com.streamx.billing.config;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SettingsCipherTest {

    private static final String KEY = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0";
    private static final String JWT = "9a6f8b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a";

    private final SettingsCipher cipher = new SettingsCipher(KEY, JWT);

    @Test
    void roundTripsWithRandomIvInIvPipeCiphertextFormat() {
        String first = cipher.encrypt("consumer-secret-value");
        String second = cipher.encrypt("consumer-secret-value");

        assertNotEquals(first, second, "every value gets a fresh IV");
        assertFalse(first.contains("consumer-secret-value"));
        String[] parts = first.split("\\|");
        assertEquals(2, parts.length);
        assertEquals(12, Base64.getDecoder().decode(parts[0]).length);
        assertEquals("consumer-secret-value", cipher.decrypt(first));
        assertEquals("consumer-secret-value", cipher.decrypt(second));
        assertEquals("", cipher.decrypt(cipher.encrypt("")));
    }

    @Test
    void detectsTamperedCiphertext() {
        String stored = cipher.encrypt("passkey-value");
        String[] parts = stored.split("\\|");
        byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
        ciphertext[0] ^= 0x01;
        String tampered = parts[0] + "|" + Base64.getEncoder().encodeToString(ciphertext);

        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void detectsTamperedIv() {
        String stored = cipher.encrypt("passkey-value");
        String[] parts = stored.split("\\|");
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        iv[5] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(iv) + "|" + parts[1];

        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void rejectsMalformedValues() {
        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt(null));
        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt("not-encrypted"));
        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt("a|b|c"));
        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt("|abcd"));
        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt("!!!|???"));
        String shortIv = Base64.getEncoder().encodeToString(new byte[8]) + "|"
                + Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt(shortIv));
    }

    @Test
    void anotherKeyCannotDecrypt() {
        String stored = cipher.encrypt("consumer-key-value");
        SettingsCipher other = new SettingsCipher("another-encryption-key-of-sufficient-length-1234", JWT);

        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> other.decrypt(stored));
    }

    @Test
    void fallsBackToKeyDerivedFromJwtSecret() {
        SettingsCipher fallback = new SettingsCipher("", JWT);
        SettingsCipher sameFallback = new SettingsCipher(null, JWT);
        String stored = fallback.encrypt("value");

        assertEquals("value", sameFallback.decrypt(stored));
        assertThrows(SettingsCipher.SettingsDecryptionException.class, () -> cipher.decrypt(stored));
        assertThrows(SettingsCipher.SettingsDecryptionException.class,
                () -> new SettingsCipher("", JWT + "x").decrypt(stored));
        assertThrows(SettingsCipher.SettingsDecryptionException.class,
                () -> new SettingsCipher(JWT, "").decrypt(stored), "fallback key is not SHA-256 of the raw JWT secret");
    }

    @Test
    void requiresSomeKeyMaterial() {
        assertThrows(IllegalStateException.class, () -> new SettingsCipher("", ""));
    }
}
