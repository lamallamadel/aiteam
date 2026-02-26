package com.atlasia.ai.service;

import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.exception.PluginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * AgentStepFactory — A2A-aware router that maps pipeline roles to AgentStep beans.
 *
 * For each standard pipeline step (PM, QUALIFIER, ARCHITECT, TESTER, WRITER),
 * the factory:
 *   1. Queries the A2A registry for the best matching agent card.
 *   2. Checks for plugin-based agent extensions.
 *   3. Logs which card was selected (for audit / future remote routing).
 *   4. Returns the local AgentStep bean (remote adapter is a future extension).
 *
 * DeveloperStep is NOT routed through this factory because its contract
 * includes methods beyond AgentStep.execute() (generateCode + commitAndCreatePullRequest).
 * 
 * Plugin lifecycle hooks are integrated: init() during plugin load, destroy() during unload,
 * and healthCheck() via scheduled monitoring.
 */
@Service
public class AgentStepFactory {
    private static final Logger log = LoggerFactory.getLogger(AgentStepFactory.class);

    private final PmStep pmStep;
    private final QualifierStep qualifierStep;
    private final ArchitectStep architectStep;
    private final TesterStep testerStep;
    private final WriterStep writerStep;
    private final A2ADiscoveryService a2aDiscoveryService;
    private final PluginManager pluginManager;

    public AgentStepFactory(
            PmStep pmStep,
            QualifierStep qualifierStep,
            ArchitectStep architectStep,
            TesterStep testerStep,
            WriterStep writerStep,
            A2ADiscoveryService a2aDiscoveryService,
            PluginManager pluginManager) {
        this.pmStep = pmStep;
        this.qualifierStep = qualifierStep;
        this.architectStep = architectStep;
        this.testerStep = testerStep;
        this.writerStep = writerStep;
        this.a2aDiscoveryService = a2aDiscoveryService;
        this.pluginManager = pluginManager;
    }

    /**
     * Resolve the AgentStep for a given role.
     *
     * Consults the A2A registry to determine the best card for the role,
     * checks for plugin-based extensions, logs the selection, then returns 
     * the corresponding local step bean or plugin adapter.
     *
     * @param role                 pipeline role (PM, QUALIFIER, ARCHITECT, TESTER, WRITER)
     * @param requiredCapabilities capabilities needed for this execution
     * @return the resolved AgentStep bean
     * @throws IllegalArgumentException if the role is unknown
     */
    public AgentStep resolveForRole(String role, Set<String> requiredCapabilities) {
        List<AgentPlugin> plugins = findPluginsForRole(role, requiredCapabilities);
        if (!plugins.isEmpty()) {
            AgentPlugin selectedPlugin = plugins.get(0);
            log.info("PLUGIN ROUTE: selected plugin={} for role={} capabilities={}",
                    selectedPlugin.getName(), role, requiredCapabilities);
            return createPluginAdapter(selectedPlugin);
        }
        
        AgentCard card = a2aDiscoveryService.discoverForRole(role, requiredCapabilities);
        if (card != null) {
            log.info("A2A ROUTE: selected agent={} for role={} capabilities={}",
                    card.name(), role, requiredCapabilities);
        } else {
            log.debug("A2A ROUTE: no card found for role={}, using local default", role);
        }
        return localStepFor(role);
    }

    /**
     * Convenience method — returns the active AgentCard for a role without
     * triggering step resolution.
     *
     * @param role pipeline role
     * @return best matching AgentCard from the registry, or null if none found
     */
    public AgentCard getActiveCard(String role) {
        return a2aDiscoveryService.discoverForRole(role, Set.of());
    }

    /**
     * Execute a plugin directly by name.
     * 
     * @param pluginName plugin identifier
     * @param context execution context
     * @return execution result
     * @throws PluginException if plugin execution fails
     */
    public String executePlugin(String pluginName, RunContext context) throws PluginException {
        PluginContext pluginContext = new PluginContext(context, pluginName);
        return pluginManager.executePlugin(pluginName, pluginContext);
    }
    
    /**
     * Get all loaded plugins.
     * 
     * @return map of plugin names to plugin instances
     */
    public List<AgentPlugin> getAllPlugins() {
        return List.copyOf(pluginManager.getAllPlugins().values());
    }
    
    /**
     * Reload a specific plugin by name.
     * 
     * @param pluginName plugin identifier
     * @throws PluginException if plugin reload fails
     */
    public void reloadPlugin(String pluginName) throws PluginException {
        pluginManager.reloadPlugin(pluginName);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private AgentStep localStepFor(String role) {
        return switch (role) {
            case "PM"        -> pmStep;
            case "QUALIFIER" -> qualifierStep;
            case "ARCHITECT" -> architectStep;
            case "TESTER"    -> testerStep;
            case "WRITER"    -> writerStep;
            default          -> throw new IllegalArgumentException("No local step for role: " + role);
        };
    }
    
    /**
     * Find plugins that can handle a specific role and capabilities.
     */
    private List<AgentPlugin> findPluginsForRole(String role, Set<String> requiredCapabilities) {
        String roleCapability = "role:" + role.toLowerCase();
        
        return pluginManager.getAllPlugins().values().stream()
                .filter(plugin -> plugin.getCapabilities().contains(roleCapability))
                .filter(plugin -> plugin.getCapabilities().containsAll(requiredCapabilities))
                .toList();
    }
    
    /**
     * Create an AgentStep adapter for a plugin.
     */
    private AgentStep createPluginAdapter(AgentPlugin plugin) {
        return context -> {
            PluginContext pluginContext = new PluginContext(context, plugin.getName());
            try {
                return pluginManager.executePlugin(plugin.getName(), pluginContext);
            } catch (PluginException e) {
                throw new RuntimeException("Plugin execution failed: " + plugin.getName(), e);
            }
        };
    }
}
