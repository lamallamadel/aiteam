# Prometheus Metrics Export

This document describes the Prometheus metrics exported by the AI Orchestrator, including the newly implemented metrics for agent execution latency, circuit breaker states, WebSocket connection health, JWT token refresh tracking, Vault secret rotation events, and cost attribution.

## Table of Contents

- [Overview](#overview)
- [Metrics Endpoint](#metrics-endpoint)
- [Agent Execution Metrics](#agent-execution-metrics)
- [Circuit Breaker Metrics](#circuit-breaker-metrics)
- [WebSocket Connection Metrics](#websocket-connection-metrics)
- [JWT Token Metrics](#jwt-token-metrics)
- [Vault Secret Rotation Metrics](#vault-secret-rotation-metrics)
- [Cost Attribution Metrics](#cost-attribution-metrics)
- [Management API](#management-api)
- [Grafana Dashboards](#grafana-dashboards)
- [Alerting Examples](#alerting-examples)

---

## Overview

The AI Orchestrator exports comprehensive metrics via the Spring Boot Actuator Prometheus endpoint. All metrics use the prefix `orchestrator.` and follow Prometheus naming conventions.

**Key Features:**
- **Percentile Metrics:** P50, P95, P99 for agent execution latency
- **Circuit Breaker Gauges:** Real-time circuit breaker state (CLOSED/OPEN/HALF_OPEN)
- **WebSocket Health:** Connection pool depth, dropped messages, delivery rate
- **Security Tracking:** JWT refresh rate and Vault rotation events
- **Cost Attribution:** Per-repository and per-user cost tracking with custom tags

---

## Metrics Endpoint

**URL:** `/actuator/prometheus`  
**Method:** `GET`  
**Authentication:** None (configure Spring Security if needed)

**Example:**
```bash
curl http://localhost:8080/actuator/prometheus
```

**Configuration:**
```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ai-orchestrator
```

---

## Agent Execution Metrics

### `orchestrator.agent.execution.latency`

**Type:** Timer (Histogram)  
**Description:** Agent execution latency with percentile distribution  
**Tags:**
- `agent_type` - Agent type (PM, QUALIFIER, ARCHITECT, DEVELOPER, TESTER, WRITER)
- `repository` - Repository identifier
- `user` - User identifier

**Percentiles:** P50 (0.5), P95 (0.95), P99 (0.99)

**Usage:**
```java
metrics.recordAgentExecution("ARCHITECT", "myorg/myrepo", "user123", durationMs);
```

**PromQL Queries:**
```promql
# P95 latency by agent type
histogram_quantile(0.95, sum(rate(orchestrator_agent_execution_latency_bucket[5m])) by (agent_type, le))

# P99 latency for specific repository
histogram_quantile(0.99, sum(rate(orchestrator_agent_execution_latency_bucket{repository="myorg/myrepo"}[5m])) by (le))

# Mean latency across all agents
avg(rate(orchestrator_agent_execution_latency_sum[5m]) / rate(orchestrator_agent_execution_latency_count[5m]))
```

---

## Circuit Breaker Metrics

### `orchestrator.graft.circuit.breaker.state`

**Type:** Gauge  
**Description:** Circuit breaker state for each agent  
**Tags:**
- `agent` - Agent name (e.g., pm-v1, architect-v1)

**Values:**
- `0` = CLOSED (normal operation)
- `1` = OPEN (failing, rejecting requests)
- `2` = HALF_OPEN (testing recovery)

**Related Metrics:**
- `orchestrator.graft.circuit.open.total` - Counter of circuit breaker open events
- `orchestrator.graft.success.total` - Counter of successful graft executions
- `orchestrator.graft.failure.total` - Counter of failed graft executions
- `orchestrator.graft.timeout.total` - Counter of graft timeouts

**Usage:**
```java
// Circuit breaker state changes are automatically tracked
metrics.updateCircuitBreakerState("architect-v1", "OPEN");
```

**PromQL Queries:**
```promql
# Count of open circuit breakers
count(orchestrator_graft_circuit_breaker_state == 1)

# Circuit breaker open events per minute
rate(orchestrator_graft_circuit_open_total[1m]) * 60

# Graft success rate
(sum(orchestrator_graft_success_total) / sum(orchestrator_graft_executions_total)) * 100
```

**Management API:**
```bash
# Get circuit breaker states
curl http://localhost:8080/api/metrics/circuit-breakers

# Reset circuit breaker for specific agent
curl -X GET http://localhost:8080/api/metrics/circuit-breakers/architect-v1/reset
```

---

## WebSocket Connection Metrics

### `orchestrator.websocket.active.connections`

**Type:** Gauge  
**Description:** Number of active WebSocket connections

### `orchestrator.websocket.message.queue.depth`

**Type:** Gauge  
**Description:** Message queue depth (messages sent - messages received)

### `orchestrator.websocket.dropped.messages`

**Type:** Gauge  
**Description:** Total number of dropped messages across all connections

### `orchestrator.websocket.connection.quality`

**Type:** Distribution Summary  
**Description:** Connection quality score (0-100) calculated from latency, reconnections, and delivery rate

### `orchestrator.websocket.message.delivery.rate`

**Type:** Distribution Summary  
**Description:** Message delivery success rate (0-1)

**Related Counters:**
- `orchestrator.websocket.connections.total` - Total connections established
- `orchestrator.websocket.disconnections.total` - Total disconnections
- `orchestrator.websocket.reconnections.total` - Total reconnection attempts
- `orchestrator.websocket.messages.in.total` - Total incoming messages
- `orchestrator.websocket.messages.out.total` - Total outgoing messages
- `orchestrator.websocket.message.failures.total` - Total message failures
- `orchestrator.websocket.fallback.http.total` - Total HTTP polling fallbacks

**PromQL Queries:**
```promql
# Active connections
orchestrator_websocket_active_connections

# Message throughput (per second)
rate(orchestrator_websocket_messages_in_total[1m])
rate(orchestrator_websocket_messages_out_total[1m])

# Average connection quality
avg(orchestrator_websocket_connection_quality)

# Message delivery success rate
avg(orchestrator_websocket_message_delivery_rate) * 100

# WebSocket message latency P95
histogram_quantile(0.95, rate(orchestrator_websocket_message_latency_bucket[5m]))
```

**Management API:**
```bash
# Get active WebSocket connections
curl http://localhost:8080/api/metrics/websocket/connections
```

---

## JWT Token Metrics

### `orchestrator.jwt.token.refresh.total`

**Type:** Counter  
**Description:** Total number of JWT token refresh operations

### `orchestrator.jwt.token.refresh.failure.total`

**Type:** Counter  
**Description:** Total number of JWT token refresh failures

**Usage:**
```java
// Automatically tracked in JwtService
metrics.recordJwtTokenRefresh();        // On success
metrics.recordJwtTokenRefreshFailure(); // On failure
```

**PromQL Queries:**
```promql
# JWT refresh rate per minute
rate(orchestrator_jwt_token_refresh_total[1m]) * 60

# JWT refresh failure rate
rate(orchestrator_jwt_token_refresh_failure_total[5m]) * 60

# JWT refresh success rate
(sum(orchestrator_jwt_token_refresh_total) - sum(orchestrator_jwt_token_refresh_failure_total)) / 
sum(orchestrator_jwt_token_refresh_total) * 100
```

---

## Vault Secret Rotation Metrics

### `orchestrator.vault.secret.rotation.total`

**Type:** Counter  
**Description:** Total number of Vault secret rotation operations

### `orchestrator.vault.secret.rotation.failure.total`

**Type:** Counter  
**Description:** Total number of Vault secret rotation failures

**Usage:**
```java
// Automatically tracked in SecretRotationScheduler
metrics.recordVaultSecretRotation("jwt-secret");        // On success
metrics.recordVaultSecretRotationFailure("jwt-secret"); // On failure
```

**Rotation Schedule:**
- **JWT Signing Key:** Monthly (1st of month, 2 AM)
- **OAuth2 Secrets:** Quarterly (1st of quarter, 2 AM)

**PromQL Queries:**
```promql
# Total rotations
sum(orchestrator_vault_secret_rotation_total)

# Rotation failures
sum(orchestrator_vault_secret_rotation_failure_total)

# Rotation success rate
(sum(orchestrator_vault_secret_rotation_total) - sum(orchestrator_vault_secret_rotation_failure_total)) / 
sum(orchestrator_vault_secret_rotation_total) * 100

# Rotations in last 24 hours
increase(orchestrator_vault_secret_rotation_total[24h])
```

---

## Cost Attribution Metrics

### `orchestrator.cost.attribution`

**Type:** Counter  
**Description:** Cost attribution with repository and user tags  
**Tags:**
- `repository` - Repository identifier
- `user` - User identifier

**Usage:**
```java
// Record cost for repository and user
metrics.recordCostAttribution("myorg/myrepo", "user123", 0.15);
```

**PromQL Queries:**
```promql
# Total cost (all time)
sum(orchestrator_cost_attribution)

# Cost by repository
sum(orchestrator_cost_attribution) by (repository)

# Cost by user
sum(orchestrator_cost_attribution) by (user)

# Top 10 repositories by cost
topk(10, sum(orchestrator_cost_attribution) by (repository))

# Cost in last 24 hours
sum(increase(orchestrator_cost_attribution[24h]))

# Cost rate per hour
sum(increase(orchestrator_cost_attribution[1h]))

# Cost per user in last 7 days
sum(increase(orchestrator_cost_attribution{user="user123"}[7d]))
```

**Important Notes:**
- Cost values should be in USD
- Use small increments per operation (e.g., $0.001 per LLM token)
- Tag cardinality: Ensure `repository` and `user` counts remain manageable (< 1000 unique values)

---

## Management API

The orchestrator provides REST endpoints for metrics introspection and management.

### Get Metrics Summary

```bash
GET /api/metrics/summary
```

**Response:**
```json
{
  "timestamp": 1699999999999,
  "totalMetrics": 87,
  "metricsByType": {
    "COUNTER": 42,
    "GAUGE": 15,
    "TIMER": 20,
    "DISTRIBUTION_SUMMARY": 10
  },
  "activeWebSocketConnections": 12
}
```

### List Available Metrics

```bash
GET /api/metrics/available
```

**Response:**
```json
{
  "categories": {
    "agent": ["orchestrator.agent.execution.latency", ...],
    "graft": ["orchestrator.graft.circuit.breaker.state", ...],
    "websocket": ["orchestrator.websocket.active.connections", ...],
    "jwt": ["orchestrator.jwt.token.refresh.total", ...],
    "vault": ["orchestrator.vault.secret.rotation.total", ...],
    "cost": ["orchestrator.cost.attribution"]
  },
  "totalMetrics": 87
}
```

### Circuit Breaker Management

```bash
# Get circuit breaker states
GET /api/metrics/circuit-breakers

# Reset circuit breaker
GET /api/metrics/circuit-breakers/{agentName}/reset
```

### WebSocket Connection Details

```bash
GET /api/metrics/websocket/connections
```

**Response:**
```json
{
  "activeConnections": [
    {
      "sessionId": "abc123",
      "userId": "user123",
      "runId": "550e8400-e29b-41d4-a716-446655440000",
      "connectedAt": "2024-01-15T10:30:00Z",
      "messagesSent": 150,
      "messagesReceived": 148,
      "messageFailures": 2,
      "reconnectionCount": 1,
      "averageLatency": 45.2,
      "maxLatency": 120,
      "deliveryRate": 0.9867
    }
  ],
  "totalActive": 1,
  "timestamp": 1699999999999
}
```

---

## Grafana Dashboards

Pre-built Grafana dashboards are available in `monitoring/grafana/dashboards/`:

1. **Metrics Overview** (`metrics-overview.json`) - High-level summary
2. **Agent Performance** (`agent-performance.json`) - Agent latency percentiles
3. **Circuit Breakers** (`circuit-breakers.json`) - Circuit breaker states and graft health
4. **WebSocket Health** (`websocket-health.json`) - Connection pool and message delivery
5. **Security Metrics** (`security-metrics.json`) - JWT and Vault tracking
6. **Cost Attribution** (`cost-attribution.json`) - Cost by repository and user

**Import Instructions:**
1. Navigate to Grafana → Dashboards → Import
2. Upload JSON file from `monitoring/grafana/dashboards/`
3. Select Prometheus data source
4. Click Import

See [monitoring/grafana/dashboards/README.md](../monitoring/grafana/dashboards/README.md) for detailed dashboard documentation.

---

## Alerting Examples

### Prometheus Alerting Rules

```yaml
groups:
  - name: orchestrator_alerts
    interval: 30s
    rules:
      - alert: HighAgentLatencyP95
        expr: histogram_quantile(0.95, rate(orchestrator_agent_execution_latency_bucket[5m])) > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High agent execution latency (P95 > 10s)"
          description: "Agent {{ $labels.agent_type }} P95 latency: {{ $value | humanizeDuration }}"

      - alert: CircuitBreakerOpen
        expr: orchestrator_graft_circuit_breaker_state == 1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker OPEN for {{ $labels.agent }}"
          description: "Circuit breaker has been OPEN for 2+ minutes"

      - alert: WebSocketConnectionsHigh
        expr: orchestrator_websocket_active_connections > 100
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High WebSocket connection count"
          description: "Active connections: {{ $value }} (threshold: 100)"

      - alert: MessageDeliveryRateLow
        expr: avg(orchestrator_websocket_message_delivery_rate) < 0.95
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Low WebSocket message delivery rate"
          description: "Delivery rate: {{ $value | humanizePercentage }} (threshold: 95%)"

      - alert: JWTRefreshFailureRateHigh
        expr: rate(orchestrator_jwt_token_refresh_failure_total[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High JWT token refresh failure rate"
          description: "Failure rate: {{ $value }}/sec (threshold: 0.1/sec)"

      - alert: VaultRotationFailure
        expr: increase(orchestrator_vault_secret_rotation_failure_total[1h]) > 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Vault secret rotation failure detected"
          description: "Rotation failed in the last hour"

      - alert: CostSpikeDetected
        expr: rate(orchestrator_cost_attribution[1h]) > 10
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: "Cost spike detected"
          description: "Cost rate: ${{ $value }}/hour (threshold: $10/hour)"
```

### Grafana Alerts

Configure alerts directly in Grafana dashboards using the built-in alerting engine:

1. Open dashboard panel
2. Click "Alert" tab
3. Create alert rule with conditions
4. Configure notification channels (Slack, PagerDuty, email)

---

## Cardinality Considerations

**High Cardinality Risks:**
- `repository` tag in cost attribution
- `user` tag in cost attribution and agent execution metrics

**Mitigation Strategies:**
1. **Limit unique values:** Aggregate low-volume repositories/users
2. **Drop labels in Prometheus:**
   ```yaml
   metric_relabel_configs:
     - source_labels: [repository]
       regex: '.*-test.*'
       action: drop
   ```
3. **Use recording rules:**
   ```yaml
   - record: orchestrator:cost:repository:1h
     expr: sum(increase(orchestrator_cost_attribution[1h])) by (repository)
   ```

**Monitor Cardinality:**
```promql
# Count unique repositories in cost metrics
count(count(orchestrator_cost_attribution) by (repository))

# Count unique users
count(count(orchestrator_cost_attribution) by (user))
```

**Recommended Limits:**
- Repositories: < 500 unique values
- Users: < 1000 unique values

---

## Integration with CI/CD

### Prometheus Configuration

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'ai-orchestrator'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['orchestrator:8080']
    scrape_interval: 15s
    scrape_timeout: 10s
```

### Docker Compose

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=30d'

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - ./monitoring/grafana/dashboards:/etc/grafana/dashboards
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
      - grafana-data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH=/etc/grafana/dashboards/metrics-overview.json
```

---

## Related Documentation

- [AGENTS.md](../AGENTS.md) - Build, test, and deployment commands
- [COLLABORATION.md](./COLLABORATION.md) - WebSocket real-time collaboration
- [VAULT_SETUP.md](./VAULT_SETUP.md) - Vault secrets management
- [Grafana Dashboard README](../monitoring/grafana/dashboards/README.md) - Dashboard documentation
- [Spring Boot Actuator Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Prometheus](https://micrometer.io/docs/registry/prometheus)
