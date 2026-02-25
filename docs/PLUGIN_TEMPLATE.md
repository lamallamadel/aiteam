# Plugin Template

This document provides a template for creating custom plugins for the Atlasia AI Orchestrator.

## Project Structure

```
my-plugin/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── myplugin/
│       │               └── MyCustomPlugin.java
│       └── resources/
│           └── META-INF/
│               └── services/
│                   └── com.atlasia.ai.service.AgentPlugin
├── pom.xml
└── README.md
```

## Maven POM Template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-plugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- Plugin API dependency - provided scope -->
        <dependency>
            <groupId>com.atlasia</groupId>
            <artifactId>ai-orchestrator</artifactId>
            <version>0.1.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- Add your plugin dependencies here -->
        <!-- Use Maven Shade Plugin to bundle dependencies -->
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>

            <!-- Maven Shade Plugin for bundling dependencies -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                            </transformers>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## Plugin Implementation Template

```java
package com.example.myplugin;

import com.atlasia.ai.service.AgentPlugin;
import com.atlasia.ai.service.PluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom plugin implementation.
 * 
 * Replace this template with your specific plugin logic.
 */
public class MyCustomPlugin implements AgentPlugin {
    
    private static final Logger log = LoggerFactory.getLogger(MyCustomPlugin.class);
    
    private Map<String, Object> config;
    private final AtomicLong executionCount = new AtomicLong(0);
    private volatile boolean initialized = false;
    
    @Override
    public String getName() {
        // Return a unique plugin name
        // Convention: use lowercase with hyphens
        return "my-custom-plugin";
    }
    
    @Override
    public Set<String> getCapabilities() {
        // Define capabilities this plugin provides
        // Include role capabilities to replace pipeline steps
        return Set.of(
            "role:qualifier",           // Replaces QualifierStep
            "custom-analysis",          // Custom capability
            "domain-specific-review"    // Domain-specific capability
        );
    }
    
    @Override
    public String execute(PluginContext context) throws Exception {
        if (!initialized) {
            throw new IllegalStateException("Plugin not initialized");
        }
        
        long count = executionCount.incrementAndGet();
        log.info("Executing plugin: {} (execution #{})", getName(), count);
        
        // Access run context
        String owner = context.getRunContext().getOwner();
        String repo = context.getRunContext().getRepo();
        
        log.debug("Processing repository: {}/{}", owner, repo);
        
        // Access shared attributes
        context.setAttribute("executionCount", count);
        context.setAttribute("timestamp", System.currentTimeMillis());
        
        // Implement your plugin logic here
        String result = performAnalysis(context);
        
        log.info("Plugin execution completed: {}", getName());
        return result;
    }
    
    @Override
    public void init(Map<String, Object> config) throws Exception {
        log.info("Initializing plugin: {}", getName());
        
        this.config = config;
        
        // Perform initialization
        // Load configuration, establish connections, etc.
        String pluginDir = (String) config.get("plugin.directory");
        log.debug("Plugin directory: {}", pluginDir);
        
        // Validate configuration
        validateConfiguration();
        
        initialized = true;
        log.info("Plugin initialized successfully: {}", getName());
    }
    
    @Override
    public void destroy() throws Exception {
        log.info("Destroying plugin: {}", getName());
        
        // Cleanup resources
        // Close connections, release resources, etc.
        
        initialized = false;
        log.info("Plugin destroyed: {} (total executions: {})", getName(), executionCount.get());
    }
    
    @Override
    public HealthCheckResult healthCheck() {
        if (!initialized) {
            return HealthCheckResult.unhealthy("Plugin not initialized");
        }
        
        Map<String, Object> details = Map.of(
            "executionCount", executionCount.get(),
            "initialized", initialized,
            "configLoaded", config != null
        );
        
        try {
            // Perform health checks
            // Check connections, verify state, etc.
            performHealthCheck();
            
            return new HealthCheckResult(
                HealthCheckResult.Status.HEALTHY,
                "Plugin is healthy",
                details
            );
        } catch (Exception e) {
            log.error("Health check failed", e);
            return new HealthCheckResult(
                HealthCheckResult.Status.UNHEALTHY,
                "Health check failed: " + e.getMessage(),
                details
            );
        }
    }
    
    // -------------------------------------------------------------------------
    // Private Helper Methods
    // -------------------------------------------------------------------------
    
    private String performAnalysis(PluginContext context) throws Exception {
        // Implement your core plugin logic here
        StringBuilder report = new StringBuilder();
        report.append("Custom Analysis Report\n");
        report.append("=====================\n\n");
        
        String owner = context.getRunContext().getOwner();
        String repo = context.getRunContext().getRepo();
        
        report.append("Repository: ").append(owner).append("/").append(repo).append("\n");
        report.append("Execution: #").append(executionCount.get()).append("\n\n");
        
        // Your analysis logic
        report.append("Analysis Results:\n");
        report.append("- Custom check 1: PASSED\n");
        report.append("- Custom check 2: PASSED\n");
        report.append("- Custom check 3: PASSED\n\n");
        
        report.append("Recommendations:\n");
        report.append("- Consider implementing feature X\n");
        report.append("- Optimize area Y\n");
        
        return report.toString();
    }
    
    private void validateConfiguration() throws Exception {
        if (config == null || config.isEmpty()) {
            throw new IllegalStateException("Configuration is empty");
        }
        
        // Add your configuration validation logic
    }
    
    private void performHealthCheck() throws Exception {
        // Add your health check logic
        // Verify connections, check state, etc.
    }
}
```

## SPI Descriptor

File: `src/main/resources/META-INF/services/com.atlasia.ai.service.AgentPlugin`

```
com.example.myplugin.MyCustomPlugin
```

## Build Instructions

```bash
# Clean and package
mvn clean package

# The plugin JAR will be in target/my-plugin-1.0.0.jar
```

## Deployment

```bash
# Copy to plugins directory
cp target/my-plugin-1.0.0.jar /path/to/orchestrator/plugins/

# Plugin will be automatically loaded
# Check orchestrator logs for confirmation
```

## Testing

### Unit Testing

```java
package com.example.myplugin;

import com.atlasia.ai.service.AgentPlugin;
import com.atlasia.ai.service.PluginContext;
import com.atlasia.ai.service.RunContext;
import com.atlasia.ai.model.RunEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MyCustomPluginTest {
    
    private MyCustomPlugin plugin;
    private PluginContext context;
    
    @BeforeEach
    void setUp() throws Exception {
        plugin = new MyCustomPlugin();
        
        Map<String, Object> config = new HashMap<>();
        config.put("plugin.name", "my-custom-plugin");
        config.put("plugin.directory", "./plugins");
        
        plugin.init(config);
        
        RunEntity runEntity = new RunEntity();
        RunContext runContext = new RunContext(runEntity, "owner", "repo");
        context = new PluginContext(runContext, "my-custom-plugin");
    }
    
    @Test
    void testExecute() throws Exception {
        String result = plugin.execute(context);
        assertNotNull(result);
        assertTrue(result.contains("Custom Analysis Report"));
    }
    
    @Test
    void testHealthCheck() {
        AgentPlugin.HealthCheckResult result = plugin.healthCheck();
        assertEquals(AgentPlugin.HealthCheckResult.Status.HEALTHY, result.status());
    }
    
    @Test
    void testCapabilities() {
        assertTrue(plugin.getCapabilities().contains("role:qualifier"));
        assertTrue(plugin.getCapabilities().contains("custom-analysis"));
    }
}
```

## Best Practices

1. **Thread Safety**: Ensure your plugin is thread-safe
2. **Resource Management**: Always clean up resources in `destroy()`
3. **Error Handling**: Handle exceptions gracefully
4. **Logging**: Use SLF4J for logging
5. **Configuration**: Validate configuration in `init()`
6. **Health Checks**: Implement meaningful health checks
7. **Dependencies**: Shade dependencies to avoid conflicts
8. **Versioning**: Use semantic versioning
9. **Documentation**: Document capabilities and configuration
10. **Testing**: Write comprehensive unit tests

## Common Patterns

### Stateful Plugin

```java
public class StatefulPlugin implements AgentPlugin {
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    
    @Override
    public String execute(PluginContext context) throws Exception {
        String key = context.getRunContext().getOwner() + "/" + 
                     context.getRunContext().getRepo();
        state.put(key, System.currentTimeMillis());
        return "State updated";
    }
}
```

### External Service Integration

```java
public class ServicePlugin implements AgentPlugin {
    private HttpClient httpClient;
    
    @Override
    public void init(Map<String, Object> config) throws Exception {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    @Override
    public void destroy() throws Exception {
        httpClient = null;
    }
}
```

### Database Plugin

```java
public class DatabasePlugin implements AgentPlugin {
    private DataSource dataSource;
    
    @Override
    public void init(Map<String, Object> config) throws Exception {
        String jdbcUrl = (String) config.get("jdbc.url");
        // Initialize datasource
    }
    
    @Override
    public void destroy() throws Exception {
        if (dataSource instanceof AutoCloseable) {
            ((AutoCloseable) dataSource).close();
        }
    }
}
```
