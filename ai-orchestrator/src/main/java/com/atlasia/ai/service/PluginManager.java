package com.atlasia.ai.service;

import com.atlasia.ai.service.exception.PluginException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Plugin manager using Java SPI (ServiceLoader) with isolated URLClassLoader per plugin JAR.
 * Supports hot-reload via file watcher on plugins/ directory.
 */
@Service
public class PluginManager {
    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);
    
    private final Map<String, LoadedPlugin> loadedPlugins = new ConcurrentHashMap<>();
    private final PluginSecurityManager securityManager;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    
    private WatchService watchService;
    private Path pluginsDirectory;
    private volatile boolean watcherRunning = false;
    
    @Value("${atlasia.plugins.directory:plugins}")
    private String pluginsDirectoryPath;
    
    @Value("${atlasia.plugins.hot-reload.enabled:true}")
    private boolean hotReloadEnabled;
    
    @Value("${atlasia.plugins.health-check.interval-seconds:60}")
    private int healthCheckIntervalSeconds;
    
    public PluginManager(PluginSecurityManager securityManager) {
        this.securityManager = securityManager;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("plugin-executor-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        this.scheduledExecutorService = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("plugin-scheduler-" + t.getId());
            t.setDaemon(true);
            return t;
        });
    }
    
    @PostConstruct
    public void initialize() {
        try {
            pluginsDirectory = Paths.get(pluginsDirectoryPath);
            if (!Files.exists(pluginsDirectory)) {
                Files.createDirectories(pluginsDirectory);
                log.info("Created plugins directory: {}", pluginsDirectory.toAbsolutePath());
            }
            
            loadAllPlugins();
            
            if (hotReloadEnabled) {
                startFileWatcher();
            }
            
            scheduleHealthChecks();
            
            log.info("PluginManager initialized. Loaded {} plugins from {}", 
                    loadedPlugins.size(), pluginsDirectory.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to initialize PluginManager", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down PluginManager");
        watcherRunning = false;
        
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log.error("Error closing watch service", e);
        }
        
        loadedPlugins.values().forEach(this::unloadPlugin);
        loadedPlugins.clear();
        
        executorService.shutdown();
        scheduledExecutorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("PluginManager shutdown complete");
    }
    
    /**
     * Load all plugins from the plugins directory.
     */
    public void loadAllPlugins() {
        try (Stream<Path> paths = Files.list(pluginsDirectory)) {
            paths.filter(path -> path.toString().endsWith(".jar"))
                 .forEach(this::loadPluginFromJar);
        } catch (IOException e) {
            log.error("Error scanning plugins directory", e);
        }
    }
    
    /**
     * Load a plugin from a JAR file.
     */
    private void loadPluginFromJar(Path jarPath) {
        try {
            log.info("Loading plugin from: {}", jarPath);
            
            URL[] urls = { jarPath.toUri().toURL() };
            URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader());
            
            ServiceLoader<AgentPlugin> serviceLoader = ServiceLoader.load(AgentPlugin.class, classLoader);
            
            for (AgentPlugin plugin : serviceLoader) {
                String pluginName = plugin.getName();
                
                if (loadedPlugins.containsKey(pluginName)) {
                    log.warn("Plugin {} already loaded, skipping", pluginName);
                    continue;
                }
                
                try {
                    Map<String, Object> config = loadPluginConfig(pluginName);
                    plugin.init(config);
                    
                    securityManager.registerPermissions(pluginName, plugin.getCapabilities());
                    
                    PluginSecurityManager.ResourceQuota quota = extractQuotaFromConfig(config);
                    securityManager.registerQuota(pluginName, quota);
                    
                    LoadedPlugin loadedPlugin = new LoadedPlugin(plugin, classLoader, jarPath, System.currentTimeMillis());
                    loadedPlugins.put(pluginName, loadedPlugin);
                    
                    log.info("Successfully loaded plugin: {} with capabilities: {}", 
                            pluginName, plugin.getCapabilities());
                } catch (Exception e) {
                    log.error("Failed to initialize plugin: {}", pluginName, e);
                    try {
                        classLoader.close();
                    } catch (IOException ex) {
                        log.error("Error closing class loader", ex);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error loading plugin from {}", jarPath, e);
        }
    }
    
    /**
     * Unload a plugin and cleanup resources.
     */
    private void unloadPlugin(LoadedPlugin loadedPlugin) {
        try {
            log.info("Unloading plugin: {}", loadedPlugin.plugin().getName());
            loadedPlugin.plugin().destroy();
            securityManager.revokePermissions(loadedPlugin.plugin().getName());
            
            if (loadedPlugin.classLoader() instanceof URLClassLoader) {
                ((URLClassLoader) loadedPlugin.classLoader()).close();
            }
        } catch (Exception e) {
            log.error("Error unloading plugin: {}", loadedPlugin.plugin().getName(), e);
        }
    }
    
    /**
     * Execute a plugin with timeout and resource limits.
     */
    public String executePlugin(String pluginName, PluginContext context) throws PluginException {
        LoadedPlugin loadedPlugin = loadedPlugins.get(pluginName);
        if (loadedPlugin == null) {
            throw new PluginException("Plugin not found: " + pluginName);
        }
        
        AgentPlugin plugin = loadedPlugin.plugin();
        
        if (!securityManager.canExecute(pluginName, plugin.getCapabilities())) {
            throw new PluginException("Plugin " + pluginName + " does not have permission to execute");
        }
        
        PluginSecurityManager.ResourceQuota quota = securityManager.getQuota(pluginName);
        
        Future<String> future = executorService.submit(() -> {
            Thread.currentThread().setContextClassLoader(loadedPlugin.classLoader());
            try {
                return plugin.execute(context);
            } finally {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            }
        });
        
        try {
            return future.get(quota.operationTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PluginException("Plugin execution timeout: " + pluginName, e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new PluginException("Plugin execution interrupted: " + pluginName, e);
        } catch (ExecutionException e) {
            throw new PluginException("Plugin execution failed: " + pluginName, e.getCause());
        }
    }
    
    /**
     * Get a plugin by name.
     */
    public AgentPlugin getPlugin(String pluginName) {
        LoadedPlugin loadedPlugin = loadedPlugins.get(pluginName);
        return loadedPlugin != null ? loadedPlugin.plugin() : null;
    }
    
    /**
     * Get all loaded plugins.
     */
    public Map<String, AgentPlugin> getAllPlugins() {
        Map<String, AgentPlugin> result = new HashMap<>();
        loadedPlugins.forEach((name, loaded) -> result.put(name, loaded.plugin()));
        return result;
    }
    
    /**
     * Find plugins by capability.
     */
    public List<AgentPlugin> findPluginsByCapability(String capability) {
        return loadedPlugins.values().stream()
                .map(LoadedPlugin::plugin)
                .filter(plugin -> plugin.getCapabilities().contains(capability))
                .toList();
    }
    
    /**
     * Reload a specific plugin.
     */
    public void reloadPlugin(String pluginName) throws PluginException {
        LoadedPlugin loadedPlugin = loadedPlugins.remove(pluginName);
        if (loadedPlugin == null) {
            throw new PluginException("Plugin not found: " + pluginName);
        }
        
        unloadPlugin(loadedPlugin);
        loadPluginFromJar(loadedPlugin.jarPath());
    }
    
    /**
     * Start file watcher for hot-reload support.
     */
    private void startFileWatcher() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            pluginsDirectory.register(watchService, 
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            
            watcherRunning = true;
            
            Thread watcherThread = new Thread(this::watchForChanges, "plugin-file-watcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
            
            log.info("File watcher started for plugins directory: {}", pluginsDirectory);
        } catch (IOException e) {
            log.error("Failed to start file watcher", e);
        }
    }
    
    /**
     * Watch for file system changes in plugins directory.
     */
    private void watchForChanges() {
        while (watcherRunning) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path filePath = pluginsDirectory.resolve(filename);
                    
                    if (!filename.toString().endsWith(".jar")) {
                        continue;
                    }
                    
                    log.info("Plugin file change detected: {} ({})", filePath, kind.name());
                    
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE || 
                        kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Thread.sleep(500);
                        handlePluginUpdate(filePath);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        handlePluginDeletion(filename.toString());
                    }
                }
                
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in file watcher", e);
            }
        }
    }
    
    /**
     * Handle plugin JAR update or creation.
     */
    private void handlePluginUpdate(Path jarPath) {
        try {
            String jarName = jarPath.getFileName().toString();
            
            Optional<String> existingPluginName = loadedPlugins.entrySet().stream()
                    .filter(entry -> entry.getValue().jarPath().getFileName().toString().equals(jarName))
                    .map(Map.Entry::getKey)
                    .findFirst();
            
            if (existingPluginName.isPresent()) {
                log.info("Reloading modified plugin: {}", existingPluginName.get());
                reloadPlugin(existingPluginName.get());
            } else {
                log.info("Loading new plugin from: {}", jarPath);
                loadPluginFromJar(jarPath);
            }
        } catch (Exception e) {
            log.error("Error handling plugin update: {}", jarPath, e);
        }
    }
    
    /**
     * Handle plugin JAR deletion.
     */
    private void handlePluginDeletion(String jarName) {
        loadedPlugins.entrySet().stream()
                .filter(entry -> entry.getValue().jarPath().getFileName().toString().equals(jarName))
                .map(Map.Entry::getKey)
                .findFirst()
                .ifPresent(pluginName -> {
                    log.info("Unloading deleted plugin: {}", pluginName);
                    LoadedPlugin loadedPlugin = loadedPlugins.remove(pluginName);
                    if (loadedPlugin != null) {
                        unloadPlugin(loadedPlugin);
                    }
                });
    }
    
    /**
     * Schedule periodic health checks for all plugins.
     */
    private void scheduleHealthChecks() {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            loadedPlugins.forEach((name, loadedPlugin) -> {
                try {
                    AgentPlugin.HealthCheckResult result = loadedPlugin.plugin().healthCheck();
                    if (result.status() != AgentPlugin.HealthCheckResult.Status.HEALTHY) {
                        log.warn("Plugin {} health check: {} - {}", name, result.status(), result.message());
                    }
                } catch (Exception e) {
                    log.error("Health check failed for plugin: {}", name, e);
                }
            });
        }, healthCheckIntervalSeconds, healthCheckIntervalSeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Load plugin-specific configuration.
     */
    private Map<String, Object> loadPluginConfig(String pluginName) {
        Map<String, Object> config = new HashMap<>();
        config.put("plugin.name", pluginName);
        config.put("plugin.directory", pluginsDirectory.toAbsolutePath().toString());
        return config;
    }
    
    /**
     * Extract resource quota from plugin configuration.
     */
    private PluginSecurityManager.ResourceQuota extractQuotaFromConfig(Map<String, Object> config) {
        long cpuTimeout = config.containsKey("quota.cpu.timeout.ms") 
                ? ((Number) config.get("quota.cpu.timeout.ms")).longValue() 
                : PluginSecurityManager.ResourceQuota.defaultQuota().cpuTimeoutMs();
        
        long memoryLimit = config.containsKey("quota.memory.limit.mb") 
                ? ((Number) config.get("quota.memory.limit.mb")).longValue() 
                : PluginSecurityManager.ResourceQuota.defaultQuota().memoryLimitMb();
        
        long operationTimeout = config.containsKey("quota.operation.timeout.ms") 
                ? ((Number) config.get("quota.operation.timeout.ms")).longValue() 
                : PluginSecurityManager.ResourceQuota.defaultQuota().operationTimeoutMs();
        
        return PluginSecurityManager.ResourceQuota.of(cpuTimeout, memoryLimit, operationTimeout);
    }
    
    /**
     * Loaded plugin record containing plugin instance and metadata.
     */
    private record LoadedPlugin(
            AgentPlugin plugin,
            ClassLoader classLoader,
            Path jarPath,
            long loadedAt
    ) {}
}
