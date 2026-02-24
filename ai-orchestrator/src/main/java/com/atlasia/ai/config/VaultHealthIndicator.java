package com.atlasia.ai.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;

@Component
@ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true")
public class VaultHealthIndicator implements HealthIndicator {

    private final VaultTemplate vaultTemplate;

    public VaultHealthIndicator(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    @Override
    public Health health() {
        try {
            VaultHealth vaultHealth = vaultTemplate.opsForSys().health();
            
            if (vaultHealth.isInitialized() && !vaultHealth.isSealed()) {
                return Health.up()
                        .withDetail("initialized", vaultHealth.isInitialized())
                        .withDetail("sealed", vaultHealth.isSealed())
                        .withDetail("standby", vaultHealth.isStandby())
                        .withDetail("serverTime", vaultHealth.getServerTimeUtc())
                        .withDetail("version", vaultHealth.getVersion())
                        .build();
            } else if (vaultHealth.isSealed()) {
                return Health.down()
                        .withDetail("reason", "Vault is sealed")
                        .withDetail("initialized", vaultHealth.isInitialized())
                        .build();
            } else {
                return Health.down()
                        .withDetail("reason", "Vault is not initialized")
                        .build();
            }
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withException(e)
                    .build();
        }
    }
}
