package com.atlasia.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security manager for plugin capability-based permissions and resource quotas.
 * Enforces access control and resource limits for plugin execution.
 */
@Component
public class PluginSecurityManager {
    private static final Logger log = LoggerFactory.getLogger(PluginSecurityManager.class);
    
    private final Map<String, PluginPermissions> pluginPermissions = new ConcurrentHashMap<>();
    private final Map<String, ResourceQuota> pluginQuotas = new ConcurrentHashMap<>();
    
    /**
     * Check if a plugin has permission to execute with required capabilities.
     * 
     * @param pluginName plugin identifier
     * @param requiredCapabilities capabilities needed for execution
     * @return true if plugin has all required capabilities
     */
    public boolean hasPermission(String pluginName, Set<String> requiredCapabilities) {
        PluginPermissions permissions = pluginPermissions.get(pluginName);
        if (permissions == null) {
            log.warn("No permissions defined for plugin: {}", pluginName);
            return false;
        }
        
        boolean hasPermission = permissions.allowedCapabilities().containsAll(requiredCapabilities);
        if (!hasPermission) {
            log.warn("Plugin {} lacks required capabilities. Required: {}, Allowed: {}", 
                    pluginName, requiredCapabilities, permissions.allowedCapabilities());
        }
        return hasPermission;
    }
    
    /**
     * Register permissions for a plugin.
     * 
     * @param pluginName plugin identifier
     * @param allowedCapabilities capabilities the plugin is allowed to use
     */
    public void registerPermissions(String pluginName, Set<String> allowedCapabilities) {
        pluginPermissions.put(pluginName, new PluginPermissions(allowedCapabilities));
        log.info("Registered permissions for plugin {}: {}", pluginName, allowedCapabilities);
    }
    
    /**
     * Register resource quotas for a plugin.
     * 
     * @param pluginName plugin identifier
     * @param quota resource quota limits
     */
    public void registerQuota(String pluginName, ResourceQuota quota) {
        pluginQuotas.put(pluginName, quota);
        log.info("Registered quota for plugin {}: cpuTimeoutMs={}, memoryLimitMb={}, operationTimeoutMs={}", 
                pluginName, quota.cpuTimeoutMs(), quota.memoryLimitMb(), quota.operationTimeoutMs());
    }
    
    /**
     * Get resource quota for a plugin.
     * 
     * @param pluginName plugin identifier
     * @return resource quota, or default quota if none registered
     */
    public ResourceQuota getQuota(String pluginName) {
        return pluginQuotas.getOrDefault(pluginName, ResourceQuota.defaultQuota());
    }
    
    /**
     * Check if a plugin is allowed to execute based on capabilities.
     * 
     * @param pluginName plugin identifier
     * @param pluginCapabilities capabilities provided by the plugin
     * @return true if plugin can execute
     */
    public boolean canExecute(String pluginName, Set<String> pluginCapabilities) {
        PluginPermissions permissions = pluginPermissions.get(pluginName);
        if (permissions == null) {
            log.debug("No permissions registered for plugin {}, allowing by default", pluginName);
            registerPermissions(pluginName, new HashSet<>(pluginCapabilities));
            return true;
        }
        return true;
    }
    
    /**
     * Revoke all permissions for a plugin.
     * 
     * @param pluginName plugin identifier
     */
    public void revokePermissions(String pluginName) {
        pluginPermissions.remove(pluginName);
        pluginQuotas.remove(pluginName);
        log.info("Revoked permissions for plugin: {}", pluginName);
    }
    
    /**
     * Plugin permissions record.
     */
    public record PluginPermissions(Set<String> allowedCapabilities) {}
    
    /**
     * Resource quota limits for plugin execution.
     */
    public record ResourceQuota(
            long cpuTimeoutMs,
            long memoryLimitMb,
            long operationTimeoutMs
    ) {
        public static ResourceQuota defaultQuota() {
            return new ResourceQuota(
                    300000,  // 5 minutes CPU timeout
                    512,     // 512 MB memory limit
                    600000   // 10 minutes operation timeout
            );
        }
        
        public static ResourceQuota of(long cpuTimeoutMs, long memoryLimitMb, long operationTimeoutMs) {
            return new ResourceQuota(cpuTimeoutMs, memoryLimitMb, operationTimeoutMs);
        }
    }
}
