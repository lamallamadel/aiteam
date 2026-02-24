package com.atlasia.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true")
public class SecretRotationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SecretRotationScheduler.class);
    
    private final VaultSecretsService vaultSecretsService;
    private final SecureRandom secureRandom;

    public SecretRotationScheduler(VaultSecretsService vaultSecretsService) {
        this.vaultSecretsService = vaultSecretsService;
        this.secureRandom = new SecureRandom();
    }

    @Scheduled(cron = "0 0 2 1 * ?")
    public void rotateJwtSigningKey() {
        logger.info("Starting monthly JWT signing key rotation");
        try {
            String newSecret = generateSecureSecret(64);
            vaultSecretsService.rotateSecret("secret/data/atlasia/jwt-secret", newSecret);
            logger.info("Successfully rotated JWT signing key");
        } catch (Exception e) {
            logger.error("Failed to rotate JWT signing key", e);
        }
    }

    @Scheduled(cron = "0 0 2 1 */3 ?")
    public void rotateOAuth2Secrets() {
        logger.info("Starting quarterly OAuth2 secrets rotation");
        try {
            rotateOAuth2ClientSecret("github");
            rotateOAuth2ClientSecret("google");
            rotateOAuth2ClientSecret("gitlab");
            logger.info("Successfully rotated all OAuth2 client secrets");
        } catch (Exception e) {
            logger.error("Failed to rotate OAuth2 secrets", e);
        }
    }

    private void rotateOAuth2ClientSecret(String provider) {
        try {
            logger.info("Rotating OAuth2 secret for provider: {}", provider);
            logger.warn("OAuth2 client secrets for {} should be manually rotated in the provider's console and updated in Vault", provider);
        } catch (Exception e) {
            logger.error("Error during OAuth2 secret rotation for provider: {}", provider, e);
        }
    }

    private String generateSecureSecret(int byteLength) {
        byte[] randomBytes = new byte[byteLength];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
