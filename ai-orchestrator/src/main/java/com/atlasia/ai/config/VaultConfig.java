package com.atlasia.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.core.VaultTemplate;

@Configuration
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultConfig {
    
    private final VaultTemplate vaultTemplate;

    public VaultConfig(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }
}
