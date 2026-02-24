package com.atlasia.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(VaultConfig.class);
    
    private final VaultTemplate vaultTemplate;

    public VaultConfig(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    @PostConstruct
    public void verifyVaultConnection() {
        try {
            VaultHealth health = vaultTemplate.opsForSys().health();
            if (health.isInitialized()) {
                logger.info("Successfully connected to Vault (version: {})", health.getVersion());
            } else {
                logger.warn("Connected to Vault but it is not initialized");
            }
        } catch (Exception e) {
            logger.error("Failed to connect to Vault. Ensure Vault is running and accessible.", e);
        }
    }

    @Bean
    public VaultTemplate vaultTemplate() {
        return vaultTemplate;
    }
}
