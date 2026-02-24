package com.atlasia.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ColumnEncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(ColumnEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 256;

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom;

    public ColumnEncryptionService(@Value("${atlasia.encryption.key:}") String encryptionKeyBase64) {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isEmpty()) {
            logger.warn("Encryption key not configured - encryption features will be disabled. Set atlasia.encryption.key in application.yml or Vault for production use");
            this.encryptionKey = null;
            this.secureRandom = null;
            return;
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != AES_KEY_SIZE / 8) {
                throw new IllegalArgumentException("Encryption key must be 256 bits (32 bytes) when base64 decoded");
            }
            this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
            this.secureRandom = new SecureRandom();
            logger.info("ColumnEncryptionService initialized with AES-256-GCM");
        } catch (IllegalArgumentException e) {
            logger.error("Failed to decode encryption key", e);
            throw new IllegalStateException("Invalid encryption key format", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        if (!isConfigured()) {
            logger.warn("Encryption key not configured - returning plaintext (not recommended for production)");
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes("UTF-8"));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            logger.error("Failed to encrypt data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return null;
        }

        if (!isConfigured()) {
            logger.warn("Encryption key not configured - returning ciphertext as-is (not recommended for production)");
            return ciphertext;
        }

        try {
            byte[] decodedData = Base64.getDecoder().decode(ciphertext);
            ByteBuffer byteBuffer = ByteBuffer.wrap(decodedData);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] cipherTextBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherTextBytes);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherTextBytes);

            return new String(plainText, "UTF-8");
        } catch (Exception e) {
            logger.error("Failed to decrypt data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public boolean isConfigured() {
        return encryptionKey != null;
    }
}
