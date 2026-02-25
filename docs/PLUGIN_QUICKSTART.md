# Plugin Architecture - Quick Start

## 5-Minute Plugin Tutorial

### 1. Create Plugin Class

```java
package com.example;

import com.atlasia.ai.service.AgentPlugin;
import com.atlasia.ai.service.PluginContext;
import java.util.*;

public class HelloPlugin implements AgentPlugin {
    public String getName() { return "hello-plugin"; }
    
    public Set<String> getCapabilities() {
        return Set.of("role:qualifier", "hello-world");
    }
    
    public String execute(PluginContext ctx) {
        return "Hello from plugin! Repo: " + 
               ctx.getRunContext().getOwner() + "/" + 
               ctx.getRunContext().getRepo();
    }
    
    public void init(Map<String, Object> config) {}
    public void destroy() {}
    
    public HealthCheckResult healthCheck() {
        return HealthCheckResult.healthy();
    }
}
```

### 2. Create SPI Descriptor

File: `META-INF/services/com.atlasia.ai.service.AgentPlugin`
```
com.example.HelloPlugin
```

### 3. Build & Deploy

```bash
# Compile
javac -cp ai-orchestrator-0.1.0.jar HelloPlugin.java

# Package
jar cf hello-plugin.jar \
    com/example/HelloPlugin.class \
    META-INF/services/com.atlasia.ai.service.AgentPlugin

# Deploy
cp hello-plugin.jar plugins/
```

Done! Plugin loads automatically.

## Key Concepts

### Capabilities

Control plugin routing and permissions:

```java
Set.of(
    "role:qualifier",      // Replace QualifierStep
    "security-audit",      // Custom capability
    "code-review"          // Domain capability
)
```

### Resource Quotas

Default limits per plugin:
- CPU: 5 minutes
- Memory: 512 MB  
- Operation: 10 minutes

### Hot Reload

Changes detected automatically:
- Add: Drop JAR in `plugins/`
- Update: Replace JAR file
- Delete: Remove JAR file

No restart needed!

### Management API

```bash
# List plugins
GET /api/plugins

# Check health
GET /api/plugins/health

# Reload plugin
POST /api/plugins/{name}/reload
```

### Health Monitoring

Available at actuator endpoint:
```bash
GET /actuator/health
```

## Configuration

`application.yml`:
```yaml
atlasia:
  plugins:
    directory: plugins
    hot-reload:
      enabled: true
    health-check:
      interval-seconds: 60
```

## Plugin Lifecycle

1. **Load**: JAR detected in plugins directory
2. **Init**: `init(config)` called with configuration
3. **Execute**: `execute(context)` called on demand
4. **Health**: `healthCheck()` called periodically
5. **Destroy**: `destroy()` called on unload/shutdown

## Common Use Cases

### Custom Review Role

```java
public class SecurityReviewPlugin implements AgentPlugin {
    public Set<String> getCapabilities() {
        return Set.of("role:qualifier", "security-audit");
    }
    
    public String execute(PluginContext ctx) {
        // Perform security analysis
        return "Security review complete";
    }
}
```

### External Tool Integration

```java
public class LinterPlugin implements AgentPlugin {
    private HttpClient client;
    
    public void init(Map<String, Object> config) {
        client = HttpClient.newHttpClient();
    }
    
    public String execute(PluginContext ctx) {
        // Call external linter API
        return "Linting results";
    }
}
```

### Metrics Collection

```java
public class MetricsPlugin implements AgentPlugin {
    private AtomicLong counter = new AtomicLong(0);
    
    public String execute(PluginContext ctx) {
        long count = counter.incrementAndGet();
        return "Execution #" + count;
    }
    
    public HealthCheckResult healthCheck() {
        return new HealthCheckResult(
            Status.HEALTHY,
            "Executions: " + counter.get(),
            Map.of("count", counter.get())
        );
    }
}
```

## Troubleshooting

### Plugin Not Loading

```
ERROR: Plugin not found in JAR
```
→ Check SPI descriptor exists at correct path

### Name Collision

```
WARN: Plugin already loaded, skipping
```
→ Use unique plugin names

### Permission Denied

```
ERROR: Plugin lacks required capabilities
```
→ Add capabilities to plugin implementation

### Execution Timeout

```
ERROR: Plugin execution timeout
```
→ Optimize plugin logic or increase timeout

## Best Practices

✅ **DO**:
- Use unique plugin names
- Implement health checks
- Clean up in destroy()
- Handle exceptions
- Use SLF4J logging

❌ **DON'T**:
- Block indefinitely
- Leak resources
- Use System.out
- Skip error handling
- Ignore health checks

## Next Steps

- Read full docs: `docs/PLUGIN_ARCHITECTURE.md`
- Use template: `docs/PLUGIN_TEMPLATE.md`
- Check examples in documentation

## Support

For issues or questions:
1. Check logs in orchestrator
2. Verify SPI descriptor
3. Test plugin in isolation
4. Review documentation
