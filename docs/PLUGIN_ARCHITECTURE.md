# Plugin Architecture

## Overview

The Atlasia AI Orchestrator supports a Java SPI-based plugin architecture for custom review roles and agent extensions. Plugins can be dynamically loaded, executed with resource limits, and hot-reloaded without orchestrator restart.

## Architecture Components

### 1. AgentPlugin Interface

Core SPI interface that all plugins must implement:

```java
public interface AgentPlugin {
    String getName();                               // Unique plugin identifier
    Set<String> getCapabilities();                  // Capabilities for routing
    String execute(PluginContext context);          // Main execution logic
    void init(Map<String, Object> config);          // Initialization hook
    void destroy();                                 // Cleanup hook
    HealthCheckResult healthCheck();                // Health monitoring
}
```

### 2. PluginManager

Service responsible for plugin lifecycle management:

- **Loading**: Uses `ServiceLoader<AgentPlugin>` with isolated `URLClassLoader` per JAR
- **Execution**: Enforces timeouts via `ExecutorService.invokeWithTimeout()`
- **Hot Reload**: File watcher on `plugins/` directory for automatic reload
- **Health Monitoring**: Periodic health checks (60s default)
- **Resource Isolation**: Separate ClassLoader per plugin JAR

### 3. PluginSecurityManager

Capability-based permission checking and resource quotas:

- **Permissions**: Capability-based access control
- **CPU Timeout**: Default 5 minutes via `ExecutorService`
- **Memory Limit**: Default 512 MB (per ClassLoader isolation)
- **Operation Timeout**: Default 10 minutes

### 4. PluginContext

Execution context passed to plugins:

```java
public class PluginContext {
    RunContext getRunContext();                     // Access to run information
    Map<String, Object> getAttributes();            // Shared state
    void setAttribute(String key, Object value);    // Set context attributes
    <T> T getAttribute(String key, Class<T> type);  // Get typed attributes
}
```

### 5. AgentStepFactory Integration

Plugin lifecycle hooks integrated into the agent pipeline:

- **Plugin Discovery**: Checks plugins before A2A registry
- **Role Routing**: Plugins with `role:*` capabilities can replace steps
- **Adapter Pattern**: Plugins wrapped as `AgentStep` for seamless integration

## Capabilities

Capabilities enable role-based routing and permission checking:

### Role Capabilities

- `role:pm` - Product Manager step
- `role:qualifier` - Qualifier step
- `role:architect` - Architect step
- `role:tester` - Tester step
- `role:writer` - Writer step

### Domain Capabilities

Examples:
- `security-audit` - Security review
- `performance-analysis` - Performance optimization
- `code-review` - Code quality review
- `vulnerability-scan` - Security scanning
- `dependency-check` - Dependency auditing

## Creating a Plugin

### Step 1: Implement AgentPlugin

```java
package com.example.myplugin;

import com.atlasia.ai.service.AgentPlugin;
import com.atlasia.ai.service.PluginContext;
import java.util.Map;
import java.util.Set;

public class MyCustomPlugin implements AgentPlugin {
    
    private Map<String, Object> config;
    
    @Override
    public String getName() {
        return "my-custom-plugin";
    }
    
    @Override
    public Set<String> getCapabilities() {
        return Set.of("role:qualifier", "security-audit");
    }
    
    @Override
    public String execute(PluginContext context) throws Exception {
        String owner = context.getRunContext().getOwner();
        String repo = context.getRunContext().getRepo();
        
        // Plugin logic here
        return "Analysis complete for " + owner + "/" + repo;
    }
    
    @Override
    public void init(Map<String, Object> config) throws Exception {
        this.config = config;
        System.out.println("Plugin initialized: " + getName());
    }
    
    @Override
    public void destroy() throws Exception {
        System.out.println("Plugin destroyed: " + getName());
    }
    
    @Override
    public HealthCheckResult healthCheck() {
        return HealthCheckResult.healthy("Plugin is operational");
    }
}
```

### Step 2: Create SPI Descriptor

Create file: `META-INF/services/com.atlasia.ai.service.AgentPlugin`

```
com.example.myplugin.MyCustomPlugin
```

### Step 3: Build Plugin JAR

```bash
# Compile plugin
javac -cp ai-orchestrator-0.1.0.jar:. com/example/myplugin/MyCustomPlugin.java

# Create JAR with SPI descriptor
jar cf my-plugin.jar \
    com/example/myplugin/*.class \
    META-INF/services/com.atlasia.ai.service.AgentPlugin
```

### Step 4: Deploy Plugin

```bash
# Copy to plugins directory
mkdir -p plugins
cp my-plugin.jar plugins/

# Plugin loads automatically (hot-reload)
```

## Configuration

Configuration passed to `init()` method:

```java
{
  "plugin.name": "my-custom-plugin",
  "plugin.directory": "/path/to/plugins",
  "quota.cpu.timeout.ms": 300000,
  "quota.memory.limit.mb": 512,
  "quota.operation.timeout.ms": 600000
}
```

### Application Properties

```yaml
atlasia:
  plugins:
    directory: plugins
    hot-reload:
      enabled: true
    health-check:
      interval-seconds: 60
```

## Resource Quotas

Default resource limits per plugin:

| Resource | Default | Description |
|----------|---------|-------------|
| CPU Timeout | 5 minutes | Maximum CPU execution time |
| Memory Limit | 512 MB | Memory limit per ClassLoader |
| Operation Timeout | 10 minutes | Total operation timeout |

Configure custom quotas in plugin configuration:

```java
config.put("quota.cpu.timeout.ms", 600000);       // 10 minutes
config.put("quota.memory.limit.mb", 1024);        // 1 GB
config.put("quota.operation.timeout.ms", 900000); // 15 minutes
```

## Security & Isolation

### ClassLoader Isolation

Each plugin JAR loads in its own `URLClassLoader`:

- Prevents class conflicts between plugins
- Allows independent versioning of dependencies
- Enables clean unloading of plugin classes

### Permission Checking

`PluginSecurityManager` enforces capability-based permissions:

```java
// Plugin must have required capabilities
if (!securityManager.hasPermission(pluginName, requiredCapabilities)) {
    throw new PluginException("Insufficient permissions");
}
```

### Execution Timeout

Plugins execute with enforced timeouts:

```java
Future<String> future = executorService.submit(() -> plugin.execute(context));
return future.get(quota.operationTimeoutMs(), TimeUnit.MILLISECONDS);
```

## Hot Reload

File watcher monitors `plugins/` directory for changes:

- **Add**: New JAR automatically loaded
- **Modify**: Plugin reloaded on JAR update
- **Delete**: Plugin unloaded on JAR removal

Changes detected within 1 second and applied without orchestrator restart.

### Manual Reload

Reload plugin via API:

```bash
POST /api/plugins/{pluginName}/reload
```

## Health Monitoring

Plugins implement health checks called periodically:

```java
@Override
public HealthCheckResult healthCheck() {
    Map<String, Object> details = Map.of(
        "executionCount", count,
        "lastExecution", lastTime
    );
    
    return new HealthCheckResult(
        Status.HEALTHY,
        "Plugin operational",
        details
    );
}
```

Health status levels:
- `HEALTHY` - Plugin functioning normally
- `DEGRADED` - Plugin operational but with issues
- `UNHEALTHY` - Plugin not functioning

## Management API

REST endpoints for plugin administration (Admin only):

### List Plugins

```bash
GET /api/plugins

Response:
[
  {
    "name": "security-review-plugin",
    "capabilities": ["role:qualifier", "security-audit"],
    "healthStatus": "HEALTHY",
    "healthMessage": "Plugin is operational"
  }
]
```

### Plugin Health

```bash
GET /api/plugins/health

Response:
{
  "security-review-plugin": {
    "status": "HEALTHY",
    "message": "Plugin is operational",
    "details": {}
  }
}
```

### Reload Plugin

```bash
POST /api/plugins/{pluginName}/reload

Response:
{
  "message": "Plugin reloaded successfully: security-review-plugin"
}
```

## Example Plugins

### Security Review Plugin

```java
public class SecurityReviewPlugin implements AgentPlugin {
    @Override
    public String getName() {
        return "security-review-plugin";
    }
    
    @Override
    public Set<String> getCapabilities() {
        return Set.of(
            "role:qualifier",
            "security-audit",
            "vulnerability-scan"
        );
    }
    
    @Override
    public String execute(PluginContext context) throws Exception {
        // Perform security analysis
        return "Security scan complete - no vulnerabilities found";
    }
    
    // ... init, destroy, healthCheck implementations
}
```

### Performance Analysis Plugin

```java
public class PerformancePlugin implements AgentPlugin {
    @Override
    public String getName() {
        return "performance-analysis-plugin";
    }
    
    @Override
    public Set<String> getCapabilities() {
        return Set.of(
            "role:architect",
            "performance-analysis",
            "bottleneck-detection"
        );
    }
    
    @Override
    public String execute(PluginContext context) throws Exception {
        // Analyze performance metrics
        return "Performance analysis complete - optimal";
    }
    
    // ... init, destroy, healthCheck implementations
}
```

## Best Practices

1. **Unique Names**: Use globally unique plugin names
2. **Capability Design**: Define specific, granular capabilities
3. **Resource Cleanup**: Always clean up in `destroy()`
4. **Error Handling**: Handle exceptions gracefully
5. **Health Checks**: Implement meaningful health monitoring
6. **Logging**: Use appropriate log levels
7. **Dependencies**: Shade dependencies to avoid conflicts
8. **Versioning**: Include version in plugin name
9. **Thread Safety**: Ensure thread-safe implementations
10. **Testing**: Test plugins in isolation before deployment

## Troubleshooting

### Plugin Not Loading

Check logs for loading errors:

```
INFO  PluginManager - Loading plugin from: plugins/my-plugin.jar
ERROR PluginManager - Error loading plugin: ...
```

Common issues:
- Missing SPI descriptor at `META-INF/services/com.atlasia.ai.service.AgentPlugin`
- Class not found (missing dependencies)
- Invalid JAR structure

### Plugin Execution Timeout

```
ERROR PluginManager - Plugin execution timeout: my-plugin
```

Solutions:
- Increase operation timeout in configuration
- Optimize plugin execution logic
- Check for infinite loops or blocking operations

### Permission Denied

```
WARN  PluginSecurityManager - Plugin lacks required capabilities
```

Solutions:
- Add required capabilities to plugin
- Register permissions with `PluginSecurityManager`
- Verify capability names match exactly

### Name Collision

```
WARN  PluginManager - Plugin already loaded, skipping
```

Solutions:
- Ensure unique plugin names
- Remove duplicate plugin JARs
- Version plugin names appropriately

## Integration with AgentStepFactory

Plugins integrate seamlessly with the agent pipeline:

```java
// Plugin with role:qualifier capability replaces QualifierStep
AgentStep step = agentStepFactory.resolveForRole("QUALIFIER", requiredCaps);
String result = step.execute(context);
```

Priority order:
1. **Plugin-based agents** (if matching capabilities)
2. **A2A registry agents** (if available)
3. **Local default steps** (fallback)

## Migration Path

Existing custom review roles can migrate to plugins:

1. Extract custom logic into `AgentPlugin` implementation
2. Define appropriate capabilities
3. Package as plugin JAR with SPI descriptor
4. Deploy to `plugins/` directory
5. Remove custom code from orchestrator

Benefits:
- Hot-reload without restart
- Better isolation and security
- Easier version management
- Simplified testing and deployment
