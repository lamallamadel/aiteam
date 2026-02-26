# Observability & Distributed Tracing

Atlasia AI Orchestrator implements comprehensive observability with OpenTelemetry distributed tracing, centralized logging with Loki, and Prometheus metrics, enabling deep visibility into workflow execution, GitHub API interactions, and real-time collaboration events.

## Architecture Overview

The observability stack consists of:

- **OpenTelemetry SDK**: Automatic and manual instrumentation
- **Jaeger**: Distributed tracing backend with OTLP ingestion
- **Loki + Promtail**: Centralized log aggregation and querying
- **Prometheus**: Metrics collection and storage
- **Grafana**: Unified visualization for metrics, traces, and logs
- **Correlation IDs**: End-to-end request tracking across services

## Centralized Logging with Loki

### Overview

Loki is a horizontally-scalable, highly-available log aggregation system inspired by Prometheus. Unlike traditional logging systems, Loki is designed to be cost-effective and easy to operate, as it indexes only metadata (labels) rather than the full log content.

### Architecture

```
Application (JSON logs) → Promtail → Loki → Grafana Explore
                                      ↓
                                  Alertmanager
```

### Structured JSON Logging

The application uses `logstash-logback-encoder` to emit structured JSON logs in production:

```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "com.atlasia.ai.service.WorkflowEngine",
  "thread": "http-nio-8080-exec-1",
  "message": "Workflow execution started",
  "correlationId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "runId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "userId": "user@example.com",
  "agentName": "DEVELOPER",
  "service": "ai-orchestrator",
  "environment": "production"
}
```

### MDC Context Propagation

The application automatically propagates contextual information via SLF4J MDC (Mapped Diagnostic Context):

- **correlationId**: Unique request identifier (UUID)
- **runId**: Workflow run identifier
- **userId**: Authenticated user identifier
- **agentName**: Current AI agent being executed

MDC context is set in `CorrelationIdFilter` and automatically included in all log statements.

### Log Retention Policies

Loki implements tiered retention based on log level and category:

| Log Level/Category | Retention Period | Purpose |
|-------------------|------------------|---------|
| INFO | 7 days | General application logs |
| WARN/ERROR | 30 days | Error investigation and debugging |
| Security Events | 90 days | Compliance and audit requirements |

Retention is configured in `loki-config.yml`:

```yaml
limits_config:
  retention_period: 90d

compactor:
  retention_enabled: true
  retention_delete_delay: 2h
```

Promtail adds retention labels based on log level and category, which Loki uses for selective retention.

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

## Log Query Patterns

### LogQL Basics

Loki uses LogQL, a query language inspired by PromQL. Basic query structure:

```logql
{label="value"} |= "search text" | json | filter expression
```

### Common Query Patterns

#### 1. Search All Logs by Service

```logql
{service="ai-orchestrator"}
```

#### 2. Filter by Log Level

```logql
{service="ai-orchestrator"} | json | level="ERROR"
```

#### 3. Search by Correlation ID

```logql
{service="ai-orchestrator"} | json | correlationId="f47ac10b-58cc-4372-a567-0e02b2c3d479"
```

#### 4. Track Workflow Execution

```logql
{service="ai-orchestrator"} | json | runId="a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

#### 5. User Activity Logs

```logql
{service="ai-orchestrator"} | json | userId="user@example.com"
```

#### 6. Authentication Failures

```logql
{service="ai-orchestrator"} |= "authentication" | json | level=~"WARN|ERROR"
```

#### 7. AI Agent Errors

```logql
{service="ai-orchestrator"} |~ "agent|workflow" | json | level="ERROR"
```

#### 8. Rate Limit Violations

```logql
{service="ai-orchestrator"} |= "rate limit" | json | level=~"WARN|ERROR"
```

#### 9. Database Connection Issues

```logql
{service="ai-orchestrator"} |~ "database|connection|hikari" | json | level="ERROR"
```

#### 10. Vault Connectivity Problems

```logql
{service="ai-orchestrator"} |~ "vault|secret" | json | level=~"WARN|ERROR"
```

#### 11. Unhandled Exceptions

```logql
{service="ai-orchestrator"} |= "Exception" | json | level="ERROR"
```

#### 12. Security Events

```logql
{service="ai-orchestrator", category="security"} | json
```

#### 13. Logs by Agent Type

```logql
{service="ai-orchestrator"} | json | agentName="DEVELOPER"
```

#### 14. Time Range Queries

```logql
{service="ai-orchestrator"} | json | level="ERROR" [5m]
```

#### 15. Count Errors Per Minute

```logql
sum(count_over_time({service="ai-orchestrator"} | json | level="ERROR" [1m]))
```

#### 16. Error Rate Calculation

```logql
sum(rate({service="ai-orchestrator"} | json | level="ERROR" [5m]))
```

#### 17. Pattern Matching (Case-Insensitive)

```logql
{service="ai-orchestrator"} |~ "(?i)timeout|error"
```

#### 18. Multi-Label Filter

```logql
{service="ai-orchestrator", level="ERROR", userId="user@example.com"}
```

#### 19. Exclude Patterns

```logql
{service="ai-orchestrator"} != "health check" | json
```

#### 20. Extract and Format Fields

```logql
{service="ai-orchestrator"} | json | line_format "{{.timestamp}} [{{.level}}] {{.message}}"
```

### Advanced Query Patterns

#### Aggregate Error Count by Logger

```logql
sum by (logger) (count_over_time({service="ai-orchestrator"} | json | level="ERROR" [1h]))
```

#### Top 10 Error Messages

```logql
topk(10, sum by (message) (count_over_time({service="ai-orchestrator"} | json | level="ERROR" [24h])))
```

#### Compare Error Rates Between Time Periods

```logql
sum(rate({service="ai-orchestrator"} | json | level="ERROR" [5m] offset 1h)) /
sum(rate({service="ai-orchestrator"} | json | level="ERROR" [5m]))
```

#### Find Slow Operations (with duration field)

```logql
{service="ai-orchestrator"} | json | duration_ms > 5000
```

## Grafana Dashboards

### Logs Overview Dashboard

The **Logs Overview** dashboard (`dashboard-logs-overview.json`) provides:

1. **Log Volume by Level**: Time-series graph showing log volume by severity
2. **Authentication Failures**: Panel showing auth-related warnings/errors
3. **AI Agent Errors**: Panel displaying agent execution failures
4. **Rate Limit Violations**: Panel tracking rate limiting events
5. **Database Failures**: Panel showing database connectivity issues
6. **Vault Connectivity Loss**: Panel for Vault-related errors
7. **Unhandled Exceptions**: Panel for unexpected exceptions
8. **Security Events**: Panel with 90-day retention security logs

### Creating Custom Dashboards

To create a custom log dashboard:

1. Navigate to Grafana → Dashboards → New Dashboard
2. Add Panel → Select Loki as data source
3. Enter LogQL query
4. Configure visualization (Logs, Time series, Table, etc.)
5. Save dashboard

Example panel configuration:

```json
{
  "datasource": {
    "type": "loki",
    "uid": "loki"
  },
  "targets": [
    {
      "expr": "{service=\"ai-orchestrator\"} | json | level=\"ERROR\"",
      "refId": "A"
    }
  ],
  "type": "logs"
}
```

## Log-Based Alerting

### Alert Rules

Log-based alerts are configured in `loki-rules.yml` and `monitoring/alerts.yml`.

#### Critical Alerts

1. **DatabaseConnectionFailuresHigh**
   - Condition: >5 database errors per second
   - Duration: 2 minutes
   - Severity: Critical

2. **VaultConnectivityLoss**
   - Condition: Any Vault connectivity errors
   - Duration: 2 minutes
   - Severity: Critical

3. **UnhandledExceptionsHigh**
   - Condition: >10 exceptions per second
   - Duration: 3 minutes
   - Severity: Critical

#### Security Alerts

1. **AuthenticationFailuresSpike**
   - Condition: >2 auth failures per second
   - Duration: 5 minutes
   - Severity: Warning

2. **RateLimitViolationsHigh**
   - Condition: >5 rate limit violations per second
   - Duration: 5 minutes
   - Severity: Warning

3. **SecurityEventsUnusual**
   - Condition: >1 security event per second
   - Duration: 5 minutes
   - Severity: Warning

#### Application Alerts

1. **AIAgentExecutionFailuresHigh**
   - Condition: >1 agent failure per second
   - Duration: 5 minutes
   - Severity: Warning

2. **OutOfMemoryIndicators**
   - Condition: Any memory-related errors
   - Duration: 2 minutes
   - Severity: Warning

3. **GitHubAPIFailuresHigh**
   - Condition: >2 GitHub API errors per second
   - Duration: 5 minutes
   - Severity: Warning

### Alert Configuration

Alerts are evaluated by Loki's ruler and forwarded to Alertmanager:

```yaml
ruler:
  storage:
    type: local
    local:
      directory: /loki/rules
  alertmanager_url: http://alertmanager-prod:9093
  enable_alertmanager_v2: true
```

### Testing Alerts

To test log-based alerts:

```bash
# Generate test error logs
docker exec ai-orchestrator bash -c 'for i in {1..10}; do logger -t ai-orchestrator "ERROR: Test database connection failure"; done'

# Check alert status in Prometheus
curl http://localhost:9090/api/v1/alerts | jq '.data.alerts[] | select(.labels.alertname=="DatabaseConnectionFailuresLogged")'

# Check Alertmanager
curl http://localhost:9093/api/v2/alerts | jq '.[] | select(.labels.alertname=="DatabaseConnectionFailuresLogged")'
```

## Deployment

### Prerequisites

- Docker Compose 1.29+
- 2GB available memory for Loki
- 500MB available disk space for initial setup

### Starting the Stack

```bash
cd infra/deployments/prod

# Set environment variables
export GRAFANA_ADMIN_PASSWORD="your-secure-password"
export DB_PASSWORD="your-db-password"

# Start monitoring stack
docker-compose -f docker-compose-monitoring.yml up -d

# Verify services
docker-compose -f docker-compose-monitoring.yml ps

# Check Loki health
curl http://localhost:3100/ready

# Check Promtail
docker logs promtail-prod --tail 50
```

### Accessing UIs

- **Grafana**: http://localhost:3001 (or https://${DOMAIN}/grafana)
- **Loki API**: http://localhost:3100
- **Prometheus**: http://localhost:9090
- **Jaeger**: http://localhost:16686
- **Alertmanager**: http://localhost:9093

## Troubleshooting

### No Logs Appearing in Loki

1. **Check Promtail is running**:
   ```bash
   docker logs promtail-prod
   ```

2. **Verify Promtail can reach Loki**:
   ```bash
   docker exec promtail-prod wget -O- http://loki-prod:3100/ready
   ```

3. **Check application is producing JSON logs**:
   ```bash
   docker logs ai-orchestrator | head -1 | jq .
   ```

4. **Verify Promtail scraping configuration**:
   ```bash
   docker exec promtail-prod cat /etc/promtail/config.yml
   ```

5. **Check Promtail targets**:
   ```bash
   curl http://localhost:9080/targets
   ```

### Loki Running Out of Disk Space

1. **Check current disk usage**:
   ```bash
   docker exec loki-prod du -sh /loki/*
   ```

2. **Manually trigger compaction**:
   ```bash
   docker exec loki-prod wget -O- --post-data='' http://localhost:3100/loki/api/v1/compact
   ```

3. **Reduce retention period** (edit `loki-config.yml`):
   ```yaml
   limits_config:
     retention_period: 30d  # Reduce from 90d
   ```

4. **Increase compaction frequency** (edit `loki-config.yml`):
   ```yaml
   compactor:
     compaction_interval: 5m  # Reduce from 10m
   ```

### Query Performance Issues

1. **Add label filters** to narrow query scope:
   ```logql
   # BAD - scans all logs
   {} |= "error"
   
   # GOOD - uses label index
   {service="ai-orchestrator"} |= "error"
   ```

2. **Limit time range**:
   ```logql
   {service="ai-orchestrator"} | json | level="ERROR" [5m]
   ```

3. **Use stream selectors**:
   ```logql
   {service="ai-orchestrator", level="ERROR"}
   ```

4. **Avoid unbounded queries**:
   ```logql
   # BAD - potentially huge result set
   {service="ai-orchestrator"}
   
   # GOOD - filtered and limited
   {service="ai-orchestrator"} | json | level="ERROR" [1h] | limit 100
   ```

### High Memory Usage

1. **Reduce `max_query_lookback`** in `loki-config.yml`:
   ```yaml
   limits_config:
     max_query_lookback: 168h  # Reduce from 720h
   ```

2. **Limit concurrent queries**:
   ```yaml
   limits_config:
     max_concurrent_tail_requests: 10
   ```

3. **Increase ingestion rate limits** if experiencing backpressure:
   ```yaml
   limits_config:
     ingestion_rate_mb: 20      # Increase from 10
     ingestion_burst_size_mb: 40 # Increase from 20
   ```

### Missing Context in Logs

1. **Verify MDC is populated** in `CorrelationIdFilter`:
   ```java
   CorrelationIdHolder.setCorrelationId(correlationId);
   CorrelationIdHolder.setUserId(userId);
   CorrelationIdHolder.setRunId(runId);
   ```

2. **Check logback configuration** includes MDC keys:
   ```xml
   <includeMdcKeyName>correlationId</includeMdcKeyName>
   <includeMdcKeyName>runId</includeMdcKeyName>
   <includeMdcKeyName>userId</includeMdcKeyName>
   ```

3. **Verify Spring profile** is set to `prod`:
   ```bash
   docker exec ai-orchestrator env | grep SPRING_PROFILES_ACTIVE
   ```

## Security Considerations

1. **Access Control**: Loki has no built-in authentication. Use reverse proxy with authentication.
2. **Data Retention**: Logs may contain PII. Implement data retention policies.
3. **Network Isolation**: Run Loki on internal Docker network.
4. **Log Sanitization**: Never log secrets, tokens, or passwords.
5. **Encryption**: Enable TLS for Promtail → Loki communication in production.

## Performance Tuning

### Promtail

```yaml
# Increase batch size for higher throughput
client:
  batchwait: 1s
  batchsize: 1048576  # 1MB batches

# Tune file position tracking
positions:
  sync_period: 10s
```

### Loki

```yaml
# Increase chunk retention for better compression
chunk_store_config:
  chunk_cache_config:
    embedded_cache:
      enabled: true
      max_size_mb: 500

# Tune compaction
compactor:
  compaction_interval: 10m
  retention_delete_delay: 2h
  retention_delete_worker_count: 150
```

## Monitoring Loki Itself

Monitor Loki health via Prometheus:

```promql
# Loki ingestion rate
rate(loki_ingester_chunks_created_total[5m])

# Query latency P95
histogram_quantile(0.95, rate(loki_request_duration_seconds_bucket[5m]))

# Failed queries
rate(loki_request_duration_seconds_count{status_code=~"5.."}[5m])

# Storage usage
loki_boltdb_shipper_upload_bytes_total
```

## Further Reading

- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Jaeger Documentation](https://www.jaegertracing.io/docs/)
- [Loki Documentation](https://grafana.com/docs/loki/latest/)
- [LogQL Query Language](https://grafana.com/docs/loki/latest/logql/)
- [Promtail Configuration](https://grafana.com/docs/loki/latest/clients/promtail/configuration/)
- [Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [PROMETHEUS_METRICS.md](./PROMETHEUS_METRICS.md)
- [WEBSOCKET_MONITORING.md](./WEBSOCKET_MONITORING.md)
