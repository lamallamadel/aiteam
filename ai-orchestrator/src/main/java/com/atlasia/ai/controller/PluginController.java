package com.atlasia.ai.controller;

import com.atlasia.ai.service.AgentPlugin;
import com.atlasia.ai.service.AgentStepFactory;
import com.atlasia.ai.service.exception.PluginException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for plugin management and monitoring.
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginController {
    
    private final AgentStepFactory agentStepFactory;
    
    public PluginController(AgentStepFactory agentStepFactory) {
        this.agentStepFactory = agentStepFactory;
    }
    
    /**
     * List all loaded plugins.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PluginInfoDto>> listPlugins() {
        List<PluginInfoDto> plugins = agentStepFactory.getAllPlugins().stream()
                .map(this::toPluginInfo)
                .toList();
        return ResponseEntity.ok(plugins);
    }
    
    /**
     * Get health status of all plugins.
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, AgentPlugin.HealthCheckResult>> getPluginsHealth() {
        Map<String, AgentPlugin.HealthCheckResult> health = agentStepFactory.getAllPlugins().stream()
                .collect(Collectors.toMap(
                        AgentPlugin::getName,
                        AgentPlugin::healthCheck
                ));
        return ResponseEntity.ok(health);
    }
    
    /**
     * Reload a specific plugin.
     */
    @PostMapping("/{pluginName}/reload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageDto> reloadPlugin(@PathVariable String pluginName) {
        try {
            agentStepFactory.reloadPlugin(pluginName);
            return ResponseEntity.ok(new MessageDto("Plugin reloaded successfully: " + pluginName));
        } catch (PluginException e) {
            return ResponseEntity.badRequest()
                    .body(new MessageDto("Failed to reload plugin: " + e.getMessage()));
        }
    }
    
    private PluginInfoDto toPluginInfo(AgentPlugin plugin) {
        AgentPlugin.HealthCheckResult health = plugin.healthCheck();
        return new PluginInfoDto(
                plugin.getName(),
                plugin.getCapabilities(),
                health.status().name(),
                health.message()
        );
    }
    
    public record PluginInfoDto(
            String name,
            java.util.Set<String> capabilities,
            String healthStatus,
            String healthMessage
    ) {}
    
    public record MessageDto(String message) {}
}
