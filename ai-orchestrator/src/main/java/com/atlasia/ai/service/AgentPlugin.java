package com.atlasia.ai.service;

import java.util.Map;
import java.util.Set;

/**
 * AgentPlugin SPI interface for custom review roles and agent extensions.
 * 
 * Plugins implement this interface and register via Java SPI mechanism by adding
 * a provider-configuration file at META-INF/services/com.atlasia.ai.service.AgentPlugin
 */
public interface AgentPlugin {
    
    /**
     * Unique name identifying this plugin.
     * 
     * @return plugin name (must be unique across all loaded plugins)
     */
    String getName();
    
    /**
     * Capabilities provided by this plugin.
     * Used for capability-based permission checking and routing.
     * 
     * @return set of capability strings (e.g., "code-review", "security-audit", "performance-analysis")
     */
    Set<String> getCapabilities();
    
    /**
     * Execute the plugin's primary logic.
     * 
     * @param context execution context containing run information and shared state
     * @return execution result (typically a string summary or output)
     * @throws Exception if execution fails
     */
    String execute(PluginContext context) throws Exception;
    
    /**
     * Initialize the plugin with configuration.
     * Called once when the plugin is loaded.
     * 
     * @param config configuration map provided by the plugin manager
     * @throws Exception if initialization fails
     */
    void init(Map<String, Object> config) throws Exception;
    
    /**
     * Cleanup resources before plugin unload.
     * Called when the plugin is being unloaded or the system is shutting down.
     * 
     * @throws Exception if cleanup fails
     */
    void destroy() throws Exception;
    
    /**
     * Check plugin health status.
     * Called periodically to verify the plugin is functioning correctly.
     * 
     * @return health check result
     */
    HealthCheckResult healthCheck();
    
    /**
     * Health check result containing status and optional details.
     */
    record HealthCheckResult(Status status, String message, Map<String, Object> details) {
        public enum Status {
            HEALTHY,
            DEGRADED,
            UNHEALTHY
        }
        
        public static HealthCheckResult healthy() {
            return new HealthCheckResult(Status.HEALTHY, "Plugin is healthy", Map.of());
        }
        
        public static HealthCheckResult healthy(String message) {
            return new HealthCheckResult(Status.HEALTHY, message, Map.of());
        }
        
        public static HealthCheckResult degraded(String message) {
            return new HealthCheckResult(Status.DEGRADED, message, Map.of());
        }
        
        public static HealthCheckResult unhealthy(String message) {
            return new HealthCheckResult(Status.UNHEALTHY, message, Map.of());
        }
    }
}
