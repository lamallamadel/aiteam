# Metrics Quick Reference

Quick reference for AI Orchestrator Prometheus metrics and common queries.

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/prometheus` | Prometheus metrics export |
| `/actuator/metrics` | JSON metrics endpoint |
| `/api/metrics/summary` | Metrics summary (custom) |
| `/api/metrics/circuit-breakers` | Circuit breaker states |
| `/api/metrics/websocket/connections` | Active WebSocket connections |

---

## Metric Categories

### Agent Execution

| Metric | Type | Description |
|--------|------|-------------|
| `orchestrator_agent_execution_latency` | Timer | Agent execution duration (P50/P95/P99) |
| `orchestrator_agent_step_executions_total` | Counter | Total agent step executions |
| `orchestrator_agent_step_errors_total` | Counter | Total agent step errors |

**Common Queries:**
```promql
# P95 latency by agent
histogram_quantile(0.95, sum(rate(orchestrator_agent_execution_latency_bucket[5m])) by (agent_type, le))

# Execution rate per second
rate(orchestrator_agent_execution_latency_count[1m])

# Top 5 slowest agents (P99)
topk(5, histogram_quantile(0.99, sum(rate(orchestrator_agent_execution_latency_bucket[5m])) by (agent_type, le)))
```

---

### Circuit Breakers

| Metric | Type | Description |
|--------|------|-------------|
| `orchestrator_graft_circuit_breaker_state` | Gauge | Circuit state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `orchestrator_graft_circuit_open_total` | Counter | Circuit open events |
| `orchestrator_graft_executions_total` | Counter | Total graft executions |
| `orchestrator_graft_success_total` | Counter | Successful graft executions |
| `orchestrator_graft_failure_total` | Counter | Failed graft executions |
| `orchestrator_graft_timeout_total` | Counter | Graft timeouts |

**Common Queries:**
```promql
# Count of open circuit breakers
count(orchestrator_graft_circuit_breaker_state == 1)

# Graft success rate
(sum(orchestrator_graft_success_total) / sum(orchestrator_graft_executions_total)) * 100

# Circuit breaker by agent
orchestrator_graft_circuit_breaker_state{agent="architect-v1"}
```

---

### WebSocket Connections

| Metric | Type | Description |
|--------|------|-------------|
| `orchestrator_websocket_active_connections` | Gauge | Active connections |
| `orchestrator_websocket_message_queue_depth` | Gauge | Message queue depth |
| `orchestrator_websocket_dropped_messages` | Gauge | Dropped messages |
| `orchestrator_websocket_connection_quality` | Summary | Connection quality (0-100) |
| `orchestrator_websocket_message_delivery_rate` | Summary | Delivery rate (0-1) |
| `orchestrator_websocket_message_latency` | Timer | Message round-trip latency |

**Common Queries:**
```promql
# Active connections
orchestrator_websocket_active_connections

# Messages per second
rate(orchestrator_websocket_messages_in_total[1m])
rate(orchestrator_websocket_messages_out_total[1m])

# Average delivery rate
avg(orchestrator_websocket_message_delivery_rate) * 100

# Message latency P95
histogram_quantile(0.95, rate(orchestrator_websocket_message_latency_bucket[5m]))
```

---

### JWT & Security

| Metric | Type | Description |
|--------|------|-------------|
| `orchestrator_jwt_token_refresh_total` | Counter | Token refresh operations |
| `orchestrator_jwt_token_refresh_failure_total` | Counter | Token refresh failures |
| `orchestrator_vault_secret_rotation_total` | Counter | Vault rotations |
| `orchestrator_vault_secret_rotation_failure_total` | Counter | Vault rotation failures |

**Common Queries:**
```promql
# JWT refresh rate (per minute)
rate(orchestrator_jwt_token_refresh_total[1m]) * 60

# JWT success rate
(sum(orchestrator_jwt_token_refresh_total) - sum(orchestrator_jwt_token_refresh_failure_total)) / sum(orchestrator_jwt_token_refresh_total) * 100

# Last Vault rotation time
time() - timestamp(orchestrator_vault_secret_rotation_total)
```

---

### Cost Attribution

| Metric | Type | Description |
|--------|------|-------------|
| `orchestrator_cost_attribution` | Counter | Cost by repository and user |

**Common Queries:**
```promql
# Total cost
sum(orchestrator_cost_attribution)

# Cost by repository
sum(orchestrator_cost_attribution) by (repository)

# Top 10 repositories by cost
topk(10, sum(orchestrator_cost_attribution) by (repository))

# Cost in last 24 hours
sum(increase(orchestrator_cost_attribution[24h]))

# Cost rate per hour
sum(rate(orchestrator_cost_attribution[1h]))
```

---

## Dashboard Quick Links

| Dashboard | UID | Purpose |
|-----------|-----|---------|
| Metrics Overview | `metrics-overview` | High-level summary |
| Agent Performance | `agent-performance` | Agent latency percentiles |
| Circuit Breakers | `circuit-breakers` | Circuit breaker monitoring |
| WebSocket Health | `websocket-health` | Connection pool health |
| Security Metrics | `security-metrics` | JWT & Vault tracking |
| Cost Attribution | `cost-attribution` | Cost by repo/user |

**Access:** `http://grafana:3000/d/{uid}`

---

## Alert Quick Reference

| Alert | Threshold | Severity |
|-------|-----------|----------|
| HighAgentLatencyP95 | P95 > 10s | Warning |
| CircuitBreakerOpen | State == OPEN for 2m | Critical |
| MessageDeliveryRateLow | Rate < 95% for 10m | Warning |
| JWTRefreshFailureRateHigh | > 0.1/sec for 5m | Critical |
| CostSpikeDetected | > $10/hour for 30m | Warning |

---

## Management API Examples

```bash
# Get circuit breaker states
curl http://localhost:8080/api/metrics/circuit-breakers

# Reset circuit breaker
curl http://localhost:8080/api/metrics/circuit-breakers/architect-v1/reset

# Get WebSocket connections
curl http://localhost:8080/api/metrics/websocket/connections

# List available metrics
curl http://localhost:8080/api/metrics/available
```

---

## Recording Rules (Optional)

For improved query performance on frequently accessed metrics:

```yaml
groups:
  - name: orchestrator_recording_rules
    interval: 60s
    rules:
      - record: orchestrator:agent_latency_p95:5m
        expr: histogram_quantile(0.95, sum(rate(orchestrator_agent_execution_latency_bucket[5m])) by (agent_type, le))

      - record: orchestrator:graft_success_rate:5m
        expr: sum(rate(orchestrator_graft_success_total[5m])) / sum(rate(orchestrator_graft_executions_total[5m]))

      - record: orchestrator:websocket_messages_rate:1m
        expr: rate(orchestrator_websocket_messages_in_total[1m]) + rate(orchestrator_websocket_messages_out_total[1m])

      - record: orchestrator:cost_per_repository:1h
        expr: sum(increase(orchestrator_cost_attribution[1h])) by (repository)

      - record: orchestrator:cost_per_user:1h
        expr: sum(increase(orchestrator_cost_attribution[1h])) by (user)
```

---

## Troubleshooting

### No data in Prometheus
1. Check scrape status: `http://prometheus:9090/targets`
2. Verify actuator endpoint: `curl http://localhost:8080/actuator/prometheus`
3. Check metric names: `curl http://localhost:8080/actuator/prometheus | grep orchestrator`

### High cardinality warnings
```promql
# Check cardinality
count(count by (repository) (orchestrator_cost_attribution))
count(count by (user) (orchestrator_cost_attribution))
```

### Query timeout
- Reduce time range
- Increase `--query.timeout` in Prometheus
- Use recording rules for expensive queries

---

## Related Documentation

- [PROMETHEUS_METRICS.md](../docs/PROMETHEUS_METRICS.md) - Detailed metrics documentation
- [Grafana Dashboard README](./grafana/dashboards/README.md) - Dashboard guide
- [alerts.yml](./alerts.yml) - Alerting rules
