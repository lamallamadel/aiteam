package com.atlasia.ai.config;

import com.atlasia.ai.service.AgentPlugin;
import com.atlasia.ai.service.PluginManager;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Health indicator for plugin system monitoring.
 * Exposes plugin health status via Spring Boot Actuator.
 */
@Component
public class PluginHealthIndicator implements HealthIndicator {
    
    private final PluginManager pluginManager;
    
    public PluginHealthIndicator(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }
    
    @Override
    public Health health() {
        Map<String, AgentPlugin> plugins = pluginManager.getAllPlugins();
        
        if (plugins.isEmpty()) {
            return Health.up()
                    .withDetail("pluginCount", 0)
                    .withDetail("message", "No plugins loaded")
                    .build();
        }
        
        Map<String, Object> details = new HashMap<>();
        details.put("pluginCount", plugins.size());
        
        Map<String, Map<String, Object>> pluginHealth = new HashMap<>();
        int healthyCount = 0;
        int degradedCount = 0;
        int unhealthyCount = 0;
        
        for (AgentPlugin plugin : plugins.values()) {
            try {
                AgentPlugin.HealthCheckResult result = plugin.healthCheck();
                
                Map<String, Object> pluginDetails = new HashMap<>();
                pluginDetails.put("status", result.status().name());
                pluginDetails.put("message", result.message());
                pluginDetails.put("capabilities", plugin.getCapabilities());
                
                if (!result.details().isEmpty()) {
                    pluginDetails.put("details", result.details());
                }
                
                pluginHealth.put(plugin.getName(), pluginDetails);
                
                switch (result.status()) {
                    case HEALTHY -> healthyCount++;
                    case DEGRADED -> degradedCount++;
                    case UNHEALTHY -> unhealthyCount++;
                }
            } catch (Exception e) {
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("status", "ERROR");
                errorDetails.put("message", "Health check failed: " + e.getMessage());
                pluginHealth.put(plugin.getName(), errorDetails);
                unhealthyCount++;
            }
        }
        
        details.put("plugins", pluginHealth);
        details.put("healthy", healthyCount);
        details.put("degraded", degradedCount);
        details.put("unhealthy", unhealthyCount);
        
        if (unhealthyCount > 0) {
            return Health.down()
                    .withDetails(details)
                    .build();
        } else if (degradedCount > 0) {
            return Health.up()
                    .withDetail("status", "DEGRADED")
                    .withDetails(details)
                    .build();
        } else {
            return Health.up()
                    .withDetails(details)
                    .build();
        }
    }
}
