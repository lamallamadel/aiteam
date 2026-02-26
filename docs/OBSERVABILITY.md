# Observability & Distributed Tracing

Atlasia AI Orchestrator implements comprehensive observability with OpenTelemetry distributed tracing, enabling deep visibility into workflow execution, GitHub API interactions, and real-time collaboration events.

## Architecture Overview

The observability stack consists of:

- **OpenTelemetry SDK**: Automatic and manual instrumentation
- **Jaeger**: Distributed tracing backend with OTLP ingestion
- **Prometheus**: Metrics collection and storage
- **Grafana**: Unified visualization for metrics and traces
- **Correlation IDs**: End-to-end request tracking across services

## OpenTelemetry Configuration

### Dependencies

The following OpenTelemetry dependencies are configured in `pom.xml`:

```xml
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-api</artifactId>
  <version>1.35.0</version>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-sdk</artifactId>
  <version>1.35.0</version>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
  <version>1.35.0</version>
</dependency>
<dependency>
  <groupId>io.opentelemetry.instrumentation</groupId>
  <artifactId>opentelemetry-spring-boot-starter</artifactId>
  <version>1.32.0-alpha</version>
</dependency>
<dependency>
  <groupId>io.opentelemetry.instrumentation</groupId>
  <artifactId>opentelemetry-jdbc</artifactId>
  <version>1.32.0-alpha</version>
</dependency>
```

### Application Configuration

Configure OpenTelemetry in `application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}

otel:
  service:
    name: ${OTEL_SERVICE_NAME:ai-orchestrator}
  exporter:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://jaeger:4317}
  traces:
    exporter: otlp
  metrics:
    exporter: none
  logs:
    exporter: none
  instrumentation:
    spring-webmvc:
      enabled: true
    spring-webflux:
      enabled: true
    jdbc:
      enabled: true
    logback-appender:
      enabled: true
```

### Environment Variables

- `OTEL_SERVICE_NAME`: Service name in traces (default: `ai-orchestrator`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: Jaeger OTLP endpoint (default: `http://jaeger:4317`)
- `TRACING_SAMPLING_PROBABILITY`: Trace sampling rate 0.0-1.0 (default: `1.0`)

## Auto-Instrumentation

OpenTelemetry automatically instruments:

### HTTP Requests (Spring WebMVC/WebFlux)
- All incoming REST API requests to `/api/*`
- HTTP method, URL, status code, duration
- Request/response headers (excluding sensitive data)

### Database Queries (JDBC)
- All PostgreSQL database queries
- Query text, execution time, connection pool metrics
- Transaction boundaries and isolation levels

### HTTP Client Calls (WebClient)
- All outbound GitHub API calls via `WebClient`
- Request/response details, retry attempts, circuit breaker state

## Custom Instrumentation

### WorkflowEngine

Critical workflow operations are instrumented with custom spans:

```java
Span workflowSpan = tracer.spanBuilder("workflow.execute")
    .setAttribute("run.id", runId.toString())
    .setAttribute("correlation.id", correlationId)
    .setAttribute("run.repo", repo)
    .setAttribute("run.issue_number", issueNumber)
    .setAttribute("run.autonomy", autonomy)
    .startSpan();

try (Scope scope = workflowSpan.makeCurrent()) {
    // Execute workflow steps
    workflowSpan.setStatus(StatusCode.OK);
    workflowSpan.setAttribute("workflow.duration_ms", duration);
} catch (Exception e) {
    workflowSpan.setStatus(StatusCode.ERROR, e.getMessage());
    workflowSpan.recordException(e);
    throw e;
} finally {
    workflowSpan.end();
}
```

**Instrumented Operations:**
- `workflow.execute`: Overall workflow execution
- `workflow.step.pm`: Product Manager step
- `workflow.step.qualifier`: Work qualification step
- `workflow.step.architect`: Architecture planning step
- `workflow.step.developer`: Code generation step
- `workflow.step.review`: Code review step
- `workflow.step.tester`: Testing step
- `workflow.step.writer`: Documentation step

**Captured Attributes:**
- `run.id`: Unique workflow run identifier
- `run.repo`: GitHub repository (owner/name)
- `run.issue_number`: GitHub issue number
- `run.autonomy`: Autonomy level (auto/confirm/observe)
- `step.name`: Agent step name
- `step.action`: Step action (execute/generate/review)
- `step.duration_ms`: Step duration in milliseconds
- `agent.type`: Local or remote agent
- `agent.name`: Agent card name (if remote)
- `workflow.status`: Final workflow status
- `escalation.reason`: Escalation reason (if applicable)
- `error.type`: Error class name (if failed)

### GitHubApiClient

GitHub API calls are instrumented for latency and error tracking:

```java
Span span = tracer.spanBuilder("github.api.createPullRequest")
    .setAttribute("http.method", "POST")
    .setAttribute("http.url", endpoint)
    .setAttribute("github.owner", owner)
    .setAttribute("github.repo", repo)
    .setAttribute("github.pr.head", head)
    .setAttribute("github.pr.base", base)
    .startSpan();

try (Scope scope = span.makeCurrent()) {
    // Make API call
    span.setStatus(StatusCode.OK);
    span.setAttribute("http.status_code", 201);
    span.setAttribute("github.pr.number", prNumber);
} catch (WebClientResponseException e) {
    span.setStatus(StatusCode.ERROR, e.getMessage());
    span.setAttribute("http.status_code", e.getStatusCode().value());
    span.recordException(e);
    throw e;
} finally {
    span.end();
}
```

**Instrumented Operations:**
- `github.api.readIssue`: Fetch issue details
- `github.api.createPullRequest`: Create pull request
- `github.api.createBranch`: Create feature branch
- `github.api.getWorkflowRun`: Check CI status
- All other GitHub API operations

**Captured Attributes:**
- `http.method`: HTTP method (GET/POST/PUT/PATCH)
- `http.url`: API endpoint path
- `http.status_code`: HTTP response status
- `github.owner`: Repository owner
- `github.repo`: Repository name
- `github.issue_number`: Issue number
- `github.pr.number`: Pull request number
- `github.pr.head`: PR head branch
- `github.pr.base`: PR base branch

### CollaborationService

Real-time collaboration events are traced for debugging distributed CRDT operations:

```java
Span span = tracer.spanBuilder("collaboration.graft")
    .setAttribute("run.id", runId.toString())
    .setAttribute("user.id", userId)
    .setAttribute("event.type", "GRAFT")
    .startSpan();

try (Scope scope = span.makeCurrent()) {
    // Apply CRDT mutation
    span.setStatus(StatusCode.OK);
    span.setAttribute("lamport.timestamp", lamportTimestamp);
} catch (Exception e) {
    span.setStatus(StatusCode.ERROR, e.getMessage());
    span.recordException(e);
    throw e;
} finally {
    span.end();
}
```

**Instrumented Operations:**
- `collaboration.graft`: Agent graft mutation
- `collaboration.prune`: Step prune mutation
- `collaboration.flag`: Flag/annotation mutation
- `collaboration.user_join`: User session start
- `collaboration.user_leave`: User session end

**Captured Attributes:**
- `run.id`: Workflow run ID
- `user.id`: User identifier
- `event.type`: Collaboration event type
- `lamport.timestamp`: Logical clock timestamp
- `active_users.count`: Number of active collaborators

## Correlation ID Propagation

Correlation IDs enable end-to-end request tracing across service boundaries.

### Header Management

The `CorrelationIdFilter` automatically:

1. Extracts `X-Correlation-ID` from incoming requests
2. Generates a new ID if missing (format: `UUID`)
3. Sets the ID in the response header
4. Attaches the ID to the current trace span
5. Propagates the ID to all downstream calls

```java
String correlationId = request.getHeader("X-Correlation-ID");
if (!StringUtils.hasText(correlationId)) {
    correlationId = CorrelationIdHolder.generateCorrelationId();
}

CorrelationIdHolder.setCorrelationId(correlationId);
response.setHeader("X-Correlation-ID", correlationId);

Span currentSpan = Span.current();
if (currentSpan != null) {
    currentSpan.setAttribute("correlation.id", correlationId);
}
```

### ThreadLocal Context

The `CorrelationIdHolder` provides thread-safe correlation ID storage:

```java
// Set correlation ID
CorrelationIdHolder.setCorrelationId(correlationId);

// Get correlation ID in any service layer
String correlationId = CorrelationIdHolder.getCorrelationId();

// Clear after request processing
CorrelationIdHolder.clear();
```

### Async Context Propagation

For asynchronous workflow execution, correlation IDs are propagated manually:

```java
@Async("workflowExecutor")
public void executeWorkflowAsync(UUID runId, String gitHubToken) {
    String correlationId = CorrelationIdHolder.generateCorrelationId();
    CorrelationIdHolder.setCorrelationId(correlationId);
    CorrelationIdHolder.setRunId(runId);
    
    try {
        self.executeWorkflow(runId);
    } finally {
        CorrelationIdHolder.clear();
    }
}
```

### Log Correlation

Correlation IDs are automatically included in structured logs via MDC:

```json
{
  "timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "message": "Workflow completed successfully",
  "correlation_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "run_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "agent": "DEVELOPER",
  "duration_ms": 45230
}
```

## Jaeger Backend

### Deployment

Jaeger is deployed via Docker Compose with the `all-in-one` image:

```yaml
jaeger:
  image: jaegertracing/all-in-one:1.54
  container_name: jaeger-prod
  environment:
    - COLLECTOR_OTLP_ENABLED=true
    - SPAN_STORAGE_TYPE=badger
    - BADGER_EPHEMERAL=false
    - BADGER_DIRECTORY_VALUE=/badger/data
    - BADGER_DIRECTORY_KEY=/badger/key
    - QUERY_BASE_PATH=/jaeger
  volumes:
    - jaeger-prod-data:/badger
  ports:
    - "16686:16686"  # Jaeger UI
    - "4317:4317"    # OTLP gRPC
    - "4318:4318"    # OTLP HTTP
    - "14250:14250"  # Jaeger gRPC
  networks:
    - monitoring-prod
    - ai_prod_network
```

### Access

- **Jaeger UI**: `http://localhost:16686` (dev) or `https://${DOMAIN}/jaeger` (prod)
- **OTLP Endpoint**: `jaeger:4317` (within Docker network)

### Storage

Jaeger uses **Badger** embedded database for persistent trace storage:

- **Data Volume**: `jaeger-prod-data` (persistent across restarts)
- **Retention**: 72 hours (configurable via `SPAN_STORAGE_BADGER_TTL`)
- **Compression**: Automatic background compaction

For production deployments with high traffic, consider:
- **Elasticsearch**: Scalable storage with full-text search
- **Cassandra**: High-throughput distributed storage
- **Kafka**: Buffering and stream processing

## Querying Traces

### By Correlation ID

Find all spans for a specific request:

```
correlation.id = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
```

### By Run ID

Track entire workflow execution:

```
run.id = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

### By Operation

Find specific operations:

```
operation = "workflow.execute"
operation = "github.api.createPullRequest"
operation = "collaboration.graft"
```

### By Error Status

Find failed operations:

```
error = true
```

### By Duration

Find slow operations:

```
duration > 5s
```

## Tracing Best Practices

### 1. Span Granularity

**DO**: Create spans for logical operations with business value
```java
Span span = tracer.spanBuilder("workflow.step.developer")
    .setAttribute("step.name", "DEVELOPER")
    .startSpan();
```

**DON'T**: Create spans for trivial operations
```java
// Too granular - avoid
Span span = tracer.spanBuilder("string.concatenation").startSpan();
```

### 2. Semantic Attributes

Use standard semantic conventions for attributes:

```java
// HTTP attributes
span.setAttribute("http.method", "POST");
span.setAttribute("http.url", "/api/runs");
span.setAttribute("http.status_code", 201);

// Database attributes
span.setAttribute("db.system", "postgresql");
span.setAttribute("db.operation", "SELECT");
span.setAttribute("db.statement", "SELECT * FROM runs WHERE id = ?");

// Custom attributes
span.setAttribute("run.id", runId.toString());
span.setAttribute("github.repo", "owner/repo");
```

### 3. Error Handling

Always record exceptions and set error status:

```java
try {
    // Operation
    span.setStatus(StatusCode.OK);
} catch (Exception e) {
    span.setStatus(StatusCode.ERROR, e.getMessage());
    span.recordException(e);  // Includes stack trace
    throw e;
} finally {
    span.end();
}
```

### 4. Span Lifecycle

Always close spans with try-with-resources or finally blocks:

```java
Span span = tracer.spanBuilder("operation").startSpan();
try (Scope scope = span.makeCurrent()) {
    // Operation
} finally {
    span.end();  // Critical: ensures span is exported
}
```

### 5. Context Propagation

Use `Scope` to propagate trace context:

```java
Span parentSpan = tracer.spanBuilder("parent").startSpan();
try (Scope scope = parentSpan.makeCurrent()) {
    // Child spans automatically link to parent
    Span childSpan = tracer.spanBuilder("child").startSpan();
    try (Scope childScope = childSpan.makeCurrent()) {
        // Work
    } finally {
        childSpan.end();
    }
} finally {
    parentSpan.end();
}
```

### 6. Sampling Strategy

Configure appropriate sampling rates:

- **Development**: 100% sampling (`TRACING_SAMPLING_PROBABILITY=1.0`)
- **Staging**: 50-100% sampling (`TRACING_SAMPLING_PROBABILITY=0.5`)
- **Production (low traffic)**: 100% sampling
- **Production (high traffic)**: 10-25% sampling (`TRACING_SAMPLING_PROBABILITY=0.1`)

Implement adaptive sampling for critical operations:

```java
// Always sample workflow executions
if (operation.equals("workflow.execute")) {
    return Sampler.alwaysOn();
}
// Sample based on configured rate for others
return Sampler.traceIdRatioBased(samplingProbability);
```

### 7. Performance Considerations

- **Minimize span count**: Aim for 10-50 spans per trace
- **Batch span export**: Use default batch processor (5s delay, 512 batch size)
- **Async export**: Never block application threads for export
- **Resource limits**: Set memory and CPU limits for Jaeger

### 8. Sensitive Data

Never include sensitive data in span attributes:

```java
// DON'T
span.setAttribute("github.token", token);
span.setAttribute("user.password", password);

// DO
span.setAttribute("github.token_prefix", token.substring(0, 8));
span.setAttribute("user.id", userId);
```

## Integration with Grafana

### Adding Jaeger Data Source

1. Navigate to Grafana → Configuration → Data Sources
2. Click "Add data source" → Select "Jaeger"
3. Configure:
   - **URL**: `http://jaeger:16686`
   - **Access**: Server (default)
4. Click "Save & Test"

### Creating Trace Dashboards

Example dashboard panels:

**Trace Duration by Operation**
```promql
histogram_quantile(0.95, 
  sum(rate(traces_duration_bucket[5m])) by (operation, le)
)
```

**Error Rate by Service**
```promql
sum(rate(traces_error_total[5m])) by (service_name) / 
sum(rate(traces_total[5m])) by (service_name)
```

**Top 10 Slowest Operations**
```promql
topk(10, 
  avg(traces_duration_seconds) by (operation)
)
```

### Trace-to-Logs Correlation

Link traces to logs using correlation IDs:

1. In Jaeger UI, click on a span
2. Copy the `correlation.id` attribute
3. Query logs in Grafana Loki:
   ```logql
   {job="ai-orchestrator"} |= "f47ac10b-58cc-4372-a567-0e02b2c3d479"
   ```

## Troubleshooting

### No Traces Appearing

1. **Check OTLP endpoint connectivity**:
   ```bash
   docker exec ai-orchestrator curl -v http://jaeger:4317
   ```

2. **Verify sampling configuration**:
   ```bash
   docker exec ai-orchestrator env | grep TRACING
   ```

3. **Check Jaeger collector logs**:
   ```bash
   docker logs jaeger-prod | grep collector
   ```

4. **Verify OpenTelemetry SDK initialization**:
   ```bash
   docker logs ai-orchestrator | grep -i opentelemetry
   ```

### High Cardinality Attributes

Avoid attributes with unbounded cardinality:

```java
// DON'T - unique per request
span.setAttribute("timestamp", System.currentTimeMillis());
span.setAttribute("request.body", requestBody);

// DO - bounded cardinality
span.setAttribute("http.status_code", statusCode);
span.setAttribute("github.owner", owner);
```

### Memory Issues

If Jaeger consumes excessive memory:

1. **Reduce retention period**:
   ```yaml
   environment:
     - SPAN_STORAGE_BADGER_TTL=24h
   ```

2. **Enable memory limits**:
   ```yaml
   deploy:
     resources:
       limits:
         memory: 1G
   ```

3. **Switch to external storage** (Elasticsearch/Cassandra)

### Span Export Delays

Tune batch processor configuration:

```java
BatchSpanProcessor.builder(spanExporter)
    .setScheduleDelay(Duration.ofSeconds(2))  // Faster export
    .setMaxQueueSize(4096)                     // Larger buffer
    .setMaxExportBatchSize(512)                // Smaller batches
    .build()
```

## Metrics vs. Traces

Use the right tool for the job:

| Use Case | Tool | Example |
|----------|------|---------|
| Request rate | Metrics (Prometheus) | `http_requests_total` |
| Request latency (p95) | Metrics (Prometheus) | `http_request_duration_seconds` |
| Error rate | Metrics (Prometheus) | `http_requests_failed_total` |
| Individual request debugging | Traces (Jaeger) | Find spans with `run.id` |
| Cross-service dependencies | Traces (Jaeger) | Trace visualization |
| Performance bottlenecks | Traces (Jaeger) | Find slowest spans |

## Security Considerations

1. **Network Isolation**: Jaeger runs on internal Docker network
2. **No Authentication**: Jaeger UI has no built-in auth - use reverse proxy
3. **TLS**: Enable TLS for OTLP ingestion in production
4. **Data Retention**: Purge traces after 72 hours to comply with GDPR
5. **Sensitive Data**: Never include PII, secrets, or tokens in spans

## Further Reading

- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [PROMETHEUS_METRICS.md](./PROMETHEUS_METRICS.md)
- [WEBSOCKET_MONITORING.md](./WEBSOCKET_MONITORING.md)
