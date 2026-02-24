package com.atlasia.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true")
public class VaultSecretsService {

    private static final Logger logger = LoggerFactory.getLogger(VaultSecretsService.class);
    
    private final VaultTemplate vaultTemplate;

    public VaultSecretsService(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    public String getSecret(String path) {
        try {
            VaultResponse response = vaultTemplate.read(path);
            if (response != null && response.getData() != null) {
                Object value = response.getData().get("value");
                if (value != null) {
                    return value.toString();
                }
            }
            logger.warn("Secret not found at path: {}", path);
            return null;
        } catch (Exception e) {
            logger.error("Error reading secret from path: {}", path, e);
            throw new SecretNotFoundException("Failed to read secret from path: " + path, e);
        }
    }

    public Map<String, Object> getSecretData(String path) {
        try {
            VaultResponse response = vaultTemplate.read(path);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            logger.warn("Secret data not found at path: {}", path);
            return Map.of();
        } catch (Exception e) {
            logger.error("Error reading secret data from path: {}", path, e);
            throw new SecretNotFoundException("Failed to read secret data from path: " + path, e);
        }
    }

    public void rotateSecret(String path, String newValue) {
        try {
            Map<String, Object> data = Map.of("value", newValue);
            vaultTemplate.write(path, data);
            logger.info("Successfully rotated secret at path: {}", path);
        } catch (Exception e) {
            logger.error("Error rotating secret at path: {}", path, e);
            throw new SecretRotationException("Failed to rotate secret at path: " + path, e);
        }
    }

    public void writeSecret(String path, Map<String, Object> data) {
        try {
            vaultTemplate.write(path, data);
            logger.info("Successfully wrote secret to path: {}", path);
        } catch (Exception e) {
            logger.error("Error writing secret to path: {}", path, e);
            throw new SecretRotationException("Failed to write secret to path: " + path, e);
        }
    }

    public static class SecretNotFoundException extends RuntimeException {
        public SecretNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class SecretRotationException extends RuntimeException {
        public SecretRotationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
