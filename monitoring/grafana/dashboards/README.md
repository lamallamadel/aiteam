# Grafana Dashboards for AI Orchestrator

This directory contains comprehensive Grafana dashboard templates for monitoring the AI Orchestrator platform.

## Dashboard Overview

### 1. Metrics Overview (`metrics-overview.json`)
**UID:** `metrics-overview`

High-level summary dashboard providing quick insights across all metric categories.

**Key Panels:**
- Total agent executions and latency P95
- Active circuit breakers (OPEN count)
- Active WebSocket connections
- JWT token refresh count
- Cost in last 24 hours
- Agent execution latency trends
- WebSocket message throughput
- Top 5 repositories by cost
- Circuit breaker state distribution

**Use Case:** Executive summary, NOC wall boards, at-a-glance system health

---

### 2. Agent Performance Metrics (`agent-performance.json`)
**UID:** `agent-performance`

Detailed agent execution latency metrics with percentile breakdowns (P50, P95, P99).

**Key Panels:**
- Agent execution latency percentiles by agent type
- Agent latency P95 by repository
- Agent execution latency heatmap
- Agent execution count per hour by agent type
- Agent execution count by user

**Metrics:**
- `orchestrator_agent_execution_latency` - Timer with percentiles (0.5, 0.95, 0.99)
- Tags: `agent_type`, `repository`, `user`

**Use Case:** Performance tuning, SLA monitoring, identifying slow agents

---

### 3. Circuit Breaker Monitoring (`circuit-breakers.json`)
**UID:** `circuit-breakers`

Real-time circuit breaker state monitoring for graft execution resilience.

**Key Panels:**
- Circuit breaker states (CLOSED=0, OPEN=1, HALF_OPEN=2)
- Circuit breaker state timeline
- Circuit breaker open events (5m rate)
- Graft execution success vs failures
- Overall graft success rate gauge
- Graft timeout totals

**Metrics:**
- `orchestrator_graft_circuit_breaker_state` - Gauge by agent
- `orchestrator_graft_circuit_open_total` - Counter
- `orchestrator_graft_success_total` - Counter
- `orchestrator_graft_failure_total` - Counter
- `orchestrator_graft_timeout_total` - Counter

**Use Case:** Fault detection, resilience monitoring, circuit breaker tuning

---

### 4. WebSocket Connection Health (`websocket-health.json`)
**UID:** `websocket-health`

Comprehensive WebSocket connection pool monitoring and message delivery tracking.

**Key Panels:**
- Active WebSocket connections (gauge)
- Message queue depth (gauge)
- Dropped messages (gauge)
- Connection quality score (0-100)
- WebSocket connection activity (connections, disconnections)
- Message throughput (in/out/failures)
- Message latency percentiles (P50, P95, P99)
- Message delivery success rate
- Resilience events (reconnections, HTTP fallbacks)
- Connection quality over time

**Metrics:**
- `orchestrator_websocket_active_connections` - Gauge
- `orchestrator_websocket_message_queue_depth` - Gauge
- `orchestrator_websocket_dropped_messages` - Gauge
- `orchestrator_websocket_connection_quality` - Distribution summary (0-100 score)
- `orchestrator_websocket_message_latency` - Timer
- `orchestrator_websocket_message_delivery_rate` - Distribution summary (0-1 rate)
- `orchestrator_websocket_reconnections_total` - Counter
- `orchestrator_websocket_fallback_http_total` - Counter

**Use Case:** Real-time collaboration health, connection quality monitoring, message delivery SLA

---

### 5. Security & Secrets Management (`security-metrics.json`)
**UID:** `security-metrics`

JWT token refresh tracking and Vault secret rotation monitoring.

**Key Panels:**
- JWT token refresh totals
- JWT refresh failure count
- Vault secret rotation totals
- Vault rotation failure count
- JWT token refresh rate (per minute)
- JWT refresh success rate gauge
- Vault secret rotation events (daily)
- Vault rotation success rate gauge

**Metrics:**
- `orchestrator_jwt_token_refresh_total` - Counter
- `orchestrator_jwt_token_refresh_failure_total` - Counter
- `orchestrator_vault_secret_rotation_total` - Counter
- `orchestrator_vault_secret_rotation_failure_total` - Counter

**Use Case:** Security auditing, token refresh health, secret rotation compliance

---

### 6. Cost Attribution Dashboard (`cost-attribution.json`)
**UID:** `cost-attribution`

Cost tracking and attribution by repository and user with custom tags.

**Key Panels:**
- Total cost (all time)
- Cost in last 24 hours
- Cost in last 7 days
- Cost attribution by repository (pie chart)
- Cost attribution by user (pie chart)
- Cost trend by repository (hourly)
- Cost trend by user (hourly)
- Top 10 repositories by cost (table)
- Top 10 users by cost (table)

**Metrics:**
- `orchestrator_cost_attribution` - Counter with tags: `repository`, `user`

**Use Case:** Cost optimization, budget tracking, chargeback reporting

---

## Installation

### Automatic Provisioning
Place dashboard JSON files in `monitoring/grafana/dashboards/` and configure Grafana provisioning:

```yaml
# provisioning/dashboards/dashboards.yml
apiVersion: 1
providers:
  - name: 'Orchestrator Dashboards'
    orgId: 1
    folder: 'AI Orchestrator'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/dashboards
```

### Manual Import
1. Navigate to Grafana UI → Dashboards → Import
2. Upload JSON file or paste JSON content
3. Select Prometheus data source
4. Click Import

---

## Prometheus Configuration

Ensure Prometheus is scraping the orchestrator metrics endpoint:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'ai-orchestrator'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['orchestrator:8080']
    scrape_interval: 15s
```

---

## Metric Naming Convention

All orchestrator metrics follow the pattern: `orchestrator.<category>.<metric_name>`

**Categories:**
- `agent` - Agent execution metrics
- `graft` - Graft execution and circuit breaker metrics
- `websocket` - WebSocket connection and messaging metrics
- `jwt` - JWT token management metrics
- `vault` - Vault secret rotation metrics
- `cost` - Cost attribution metrics

**Metric Types:**
- `_total` suffix - Counters (monotonic increasing)
- `_duration` / `_latency` - Timers (with histograms)
- No suffix - Gauges (point-in-time values)

---

## Alerting Rules (Optional)

Example Prometheus alerting rules for critical metrics:

```yaml
groups:
  - name: orchestrator_alerts
    interval: 30s
    rules:
      - alert: HighAgentLatency
        expr: histogram_quantile(0.95, rate(orchestrator_agent_execution_latency_bucket[5m])) > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High agent execution latency detected"
          description: "Agent P95 latency is {{ $value }}ms (threshold: 10000ms)"

      - alert: CircuitBreakerOpen
        expr: orchestrator_graft_circuit_breaker_state == 1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker OPEN for agent {{ $labels.agent }}"
          description: "Circuit breaker has been OPEN for 2+ minutes"

      - alert: WebSocketConnectionsHigh
        expr: orchestrator_websocket_active_connections > 100
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High WebSocket connection count"
          description: "Active connections: {{ $value }} (threshold: 100)"

      - alert: JWTRefreshFailures
        expr: rate(orchestrator_jwt_token_refresh_failure_total[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "JWT token refresh failures detected"
          description: "Refresh failure rate: {{ $value }}/sec"

      - alert: MessageDeliveryLow
        expr: avg(orchestrator_websocket_message_delivery_rate) < 0.95
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Low WebSocket message delivery rate"
          description: "Delivery rate: {{ $value | humanizePercentage }} (threshold: 95%)"
```

---

## Dashboard Variables (Future Enhancement)

Consider adding template variables for filtering:

- `$repository` - Filter by repository
- `$user` - Filter by user
- `$agent_type` - Filter by agent type
- `$time_range` - Custom time range selector

---

## Refresh Rates

Default refresh rates:
- **Metrics Overview:** 30s
- **Agent Performance:** 30s
- **Circuit Breakers:** 30s
- **WebSocket Health:** 30s
- **Security Metrics:** 1m
- **Cost Attribution:** 1m

Adjust based on data volume and Prometheus query load.

---

## Troubleshooting

### Dashboard shows "No Data"
1. Verify Prometheus is scraping the orchestrator: `http://prometheus:9090/targets`
2. Check actuator endpoint is enabled: `http://orchestrator:8080/actuator/prometheus`
3. Verify metric names in dashboard match exported metrics
4. Check Grafana data source configuration

### High Cardinality Warnings
If `repository` or `user` tags cause high cardinality:
1. Limit number of unique values
2. Use relabeling in Prometheus to drop high-cardinality labels
3. Consider aggregating in application before export

### Query Timeout
For large time ranges or high cardinality:
1. Reduce time range
2. Increase Prometheus query timeout: `--query.timeout=2m`
3. Add recording rules for expensive queries

---

## Related Documentation

- [AGENTS.md](../../../AGENTS.md) - Build, test, and dev server commands
- [COLLABORATION.md](../../../docs/COLLABORATION.md) - WebSocket collaboration features
- [VAULT_SETUP.md](../../../docs/VAULT_SETUP.md) - Vault secrets management
- [Prometheus Operator](https://prometheus-operator.dev/) - Kubernetes deployment
- [Grafana Provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/) - Automated dashboard deployment
