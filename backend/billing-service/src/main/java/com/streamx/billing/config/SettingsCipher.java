package com.streamx.billing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for secrets stored in the database. Values are stored as {@code base64(iv)|base64(ciphertext)}
 * with a random 12-byte IV per value. The key is SHA-256 of {@code SETTINGS_ENCRYPTION_KEY}, or of
 * {@code "mpesa-settings:" + jwt.secret} when that variable is empty.
 */
@Component
public class SettingsCipher {

    private static final Logger log = LoggerFactory.getLogger(SettingsCipher.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String FALLBACK_PREFIX = "mpesa-settings:";
    private static final String SEPARATOR = "|";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SettingsCipher(@Value("${billing.settings.encryption-key:}") String encryptionKey,
                          @Value("${jwt.secret:}") String jwtSecret) {
        String material;
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            material = encryptionKey.trim();
            if (material.length() < 32) {
                log.warn("SETTINGS_ENCRYPTION_KEY is shorter than 32 characters; use 64 random hex characters");
            }
        } else {
            if (jwtSecret == null || jwtSecret.isBlank()) {
                throw new IllegalStateException("Set SETTINGS_ENCRYPTION_KEY to store M-Pesa settings");
            }
            log.warn("SETTINGS_ENCRYPTION_KEY is not set; deriving the settings encryption key from the JWT secret. "
                    + "Set SETTINGS_ENCRYPTION_KEY (64 hex characters) so rotating the JWT secret does not make "
                    + "stored M-Pesa credentials unreadable.");
            material = FALLBACK_PREFIX + jwtSecret;
        }
        this.key = new SecretKeySpec(sha256(material), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Nothing to encrypt");
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getEncoder();
            return encoder.encodeToString(iv) + SEPARATOR + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt setting", e);
        }
    }

    /**
     * @throws SettingsDecryptionException when the value is malformed, was tampered with or was encrypted with
     *                                     another key
     */
    public String decrypt(String stored) {
        if (stored == null) {
            throw new SettingsDecryptionException();
        }
        int separator = stored.indexOf(SEPARATOR);
        if (separator <= 0 || separator != stored.lastIndexOf(SEPARATOR)) {
            throw new SettingsDecryptionException();
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] iv = decoder.decode(stored.substring(0, separator));
            byte[] ciphertext = decoder.decode(stored.substring(separator + 1));
            if (iv.length != IV_LENGTH) {
                throw new SettingsDecryptionException();
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new SettingsDecryptionException();
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static class SettingsDecryptionException extends RuntimeException {
        public SettingsDecryptionException() {
            super("Stored setting could not be decrypted");
        }
    }
}
