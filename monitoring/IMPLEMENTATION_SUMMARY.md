# Prometheus Metrics Implementation Summary

This document provides a summary of the Prometheus metrics implementation for the AI Orchestrator.

## Implementation Overview

The metrics implementation extends the existing `OrchestratorMetrics` service with new counters, histograms, gauges, and timers to provide comprehensive observability across agent execution, circuit breakers, WebSocket connections, JWT token management, Vault secret rotation, and cost attribution.

---

## Changes Made

### 1. Backend Services

#### `OrchestratorMetrics.java`
**Location:** `ai-orchestrator/src/main/java/com/atlasia/ai/service/observability/OrchestratorMetrics.java`

**New Metrics Added:**
- `orchestrator_agent_execution_latency` - Timer with P50/P95/P99 percentiles
  - Tags: `agent_type`, `repository`, `user`
  - Tracks agent execution duration with percentile breakdowns
  
- `orchestrator_graft_circuit_breaker_state` - Gauge (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
  - Tags: `agent`
  - Tracks circuit breaker state per agent
  
- `orchestrator_websocket_active_connections` - Gauge
  - Tracks number of active WebSocket connections
  
- `orchestrator_websocket_message_queue_depth` - Gauge
  - Tracks message queue depth (sent - received)
  
- `orchestrator_websocket_dropped_messages` - Gauge
  - Tracks total dropped messages across all connections
  
- `orchestrator_jwt_token_refresh_total` - Counter
  - Tracks JWT token refresh operations
  
- `orchestrator_jwt_token_refresh_failure_total` - Counter
  - Tracks JWT token refresh failures
  
- `orchestrator_vault_secret_rotation_total` - Counter
  - Tracks Vault secret rotation events
  
- `orchestrator_vault_secret_rotation_failure_total` - Counter
  - Tracks Vault secret rotation failures
  
- `orchestrator_cost_attribution` - Counter
  - Tags: `repository`, `user`
  - Tracks cost attribution per repository and user

**New Methods:**
- `getOrCreateAgentExecutionTimer(agentType, repository, userId)` - Creates/retrieves timer with tags
- `recordAgentExecution(agentType, repository, userId, durationMs)` - Records agent execution
- `updateCircuitBreakerState(agentName, state)` - Updates circuit breaker state gauge
- `recordJwtTokenRefresh()` - Records JWT refresh
- `recordJwtTokenRefreshFailure()` - Records JWT refresh failure
- `recordVaultSecretRotation(secretType)` - Records Vault rotation
- `recordVaultSecretRotationFailure(secretType)` - Records Vault rotation failure
- `recordCostAttribution(repository, userId, cost)` - Records cost
- `registerWebSocketConnectionPoolGauges(monitor)` - Registers WebSocket gauges
- `getMeterRegistry()` - Exposes meter registry for custom metrics

---

#### `GraftExecutionService.java`
**Location:** `ai-orchestrator/src/main/java/com/atlasia/ai/service/GraftExecutionService.java`

**Changes:**
- Added calls to `metrics.updateCircuitBreakerState()` when circuit breaker state changes
- Updated `recordSuccess()` to emit CLOSED state
- Updated `recordFailure()` to emit OPEN state
- Updated `isCircuitOpen()` to emit HALF_OPEN state

---

#### `WebSocketConnectionMonitor.java`
**Location:** `ai-orchestrator/src/main/java/com/atlasia/ai/service/WebSocketConnectionMonitor.java`

**Changes:**
- Added call to `metrics.registerWebSocketConnectionPoolGauges(this)` in constructor
- Registers gauges for active connections, queue depth, and dropped messages

---

#### `JwtService.java`
**Location:** `ai-orchestrator/src/main/java/com/atlasia/ai/service/JwtService.java`

**Changes:**
- Injected `OrchestratorMetrics` dependency
- Added try-catch in `generateRefreshToken()` to record success/failure metrics

---

#### `SecretRotationScheduler.java`
**Location:** `ai-orchestrator/src/main/java/com/atlasia/ai/service/SecretRotationScheduler.java`

**Changes:**
- Injected `OrchestratorMetrics` dependency
- Added metrics recording in `rotateJwtSigningKey()` and `rotateOAuth2Secrets()`

---

#### `MetricsController.java` (NEW)
**Location:** `ai-orchestrator/src/main/java/com/atlasia/ai/controller/MetricsController.java`

**Endpoints:**
- `GET /api/metrics/summary` - Metrics summary
- `GET /api/metrics/circuit-breakers` - Circuit breaker states
- `GET /api/metrics/circuit-breakers/{agentName}/reset` - Reset circuit breaker
- `GET /api/metrics/websocket/connections` - Active WebSocket connections
- `GET /api/metrics/available` - List available metrics by category

---

### 2. Grafana Dashboards

**Location:** `monitoring/grafana/dashboards/`

#### New Dashboards:
1. **metrics-overview.json** - High-level summary dashboard
2. **agent-performance.json** - Agent execution latency percentiles
3. **circuit-breakers.json** - Circuit breaker monitoring
4. **websocket-health.json** - WebSocket connection health
5. **security-metrics.json** - JWT and Vault tracking
6. **cost-attribution.json** - Cost by repository and user

#### Dashboard README
**Location:** `monitoring/grafana/dashboards/README.md`
- Comprehensive documentation for all dashboards
- Installation instructions
- Metric descriptions
- PromQL query examples
- Alerting rule examples

---

### 3. Prometheus Configuration

#### `prometheus.yml`
**Location:** `monitoring/prometheus.yml`

**Updates:**
- Added external labels for cluster and environment
- Added metric relabeling to filter orchestrator metrics only
- Configured alerting with alertmanager
- Added rule file reference

#### `alerts.yml` (NEW)
**Location:** `monitoring/alerts.yml`

**Alert Groups:**
- `orchestrator_performance` - Agent latency alerts
- `orchestrator_resilience` - Circuit breaker and graft alerts
- `orchestrator_websocket` - WebSocket health alerts
- `orchestrator_security` - JWT and Vault alerts
- `orchestrator_cost` - Cost spike alerts
- `orchestrator_health` - Overall health alerts

**Alert Examples:**
- HighAgentLatencyP95 (P95 > 10s)
- CircuitBreakerOpen (OPEN for 2m)
- MessageDeliveryRateLow (< 95%)
- JWTRefreshFailureRateHigh (> 0.1/sec)
- CostSpikeDetected (> $10/hour)

---

### 4. Documentation

#### `PROMETHEUS_METRICS.md` (NEW)
**Location:** `docs/PROMETHEUS_METRICS.md`

Comprehensive documentation covering:
- Metrics endpoint configuration
- Detailed metric descriptions
- PromQL query examples
- Management API documentation
- Grafana dashboard overview
- Alerting examples
- Cardinality considerations
- CI/CD integration

#### `METRICS_QUICK_REFERENCE.md` (NEW)
**Location:** `monitoring/METRICS_QUICK_REFERENCE.md`

Quick reference guide with:
- Metric categories table
- Common PromQL queries
- Dashboard quick links
- Alert thresholds
- Management API examples
- Recording rules
- Troubleshooting tips

---

## Metric Categories

### Agent Execution (6 metrics)
- Execution latency with percentiles
- Execution counts
- Error tracking

### Circuit Breakers (6 metrics)
- State gauges
- Open/close events
- Success/failure tracking
- Timeout counts

### WebSocket Connections (12 metrics)
- Active connections
- Message throughput
- Queue depth
- Delivery rates
- Connection quality
- Latency tracking

### JWT & Security (4 metrics)
- Token refresh tracking
- Vault rotation tracking
- Success/failure rates

### Cost Attribution (1 metric)
- Per-repository cost
- Per-user cost
- Custom tag support

---

## Integration Points

### 1. Application Startup
- `OrchestratorMetrics` bean initialization
- WebSocket connection pool gauge registration
- Circuit breaker state initialization

### 2. Runtime Tracking
- Agent execution timing (per invocation)
- Circuit breaker state changes (on threshold)
- WebSocket connection events (connect/disconnect)
- JWT token refresh (on refresh request)
- Vault rotation (scheduled monthly/quarterly)
- Cost attribution (per operation)

### 3. Prometheus Scraping
- Endpoint: `/actuator/prometheus`
- Scrape interval: 10-15 seconds
- Timeout: 5 seconds
- Metric filtering via relabeling

### 4. Grafana Visualization
- 6 pre-built dashboards
- Auto-refresh intervals (30s - 1m)
- Drill-down links between dashboards
- Alert annotations

---

## Performance Considerations

### Cardinality
**High Cardinality Tags:**
- `repository` - Limit to < 500 unique values
- `user` - Limit to < 1000 unique values

**Mitigation:**
- Use metric relabeling to drop low-volume labels
- Implement recording rules for expensive queries
- Monitor cardinality with Prometheus queries

### Query Performance
**Optimizations:**
- Pre-calculate percentiles using recording rules
- Use rate() over increase() for counters
- Limit time ranges in expensive queries
- Enable query result caching in Prometheus

### Storage
**Considerations:**
- Default retention: 30 days
- Disk space: ~2-5 GB per month (estimated)
- Compression: Enabled by default in Prometheus
- Remote write: Optional (for long-term storage)

---

## Testing

### Manual Testing
```bash
# 1. Start application
cd ai-orchestrator && mvn spring-boot:run

# 2. Access metrics endpoint
curl http://localhost:8080/actuator/prometheus | grep orchestrator

# 3. Verify specific metrics
curl http://localhost:8080/actuator/prometheus | grep agent_execution_latency
curl http://localhost:8080/actuator/prometheus | grep circuit_breaker_state

# 4. Test Management API
curl http://localhost:8080/api/metrics/summary
curl http://localhost:8080/api/metrics/circuit-breakers

# 5. Import Grafana dashboards
# Navigate to http://localhost:3000
# Import JSON files from monitoring/grafana/dashboards/
```

### Integration Testing
```bash
# 1. Start Prometheus + Grafana
docker-compose -f infra/docker-compose.monitoring.yml up -d

# 2. Verify Prometheus targets
# Open http://localhost:9090/targets

# 3. Test PromQL queries
# Open http://localhost:9090/graph
# Run sample queries from METRICS_QUICK_REFERENCE.md

# 4. Verify Grafana dashboards
# Open http://localhost:3000
# Navigate to Dashboards → AI Orchestrator
```

---

## Deployment Checklist

- [ ] Update `application.yml` to enable Prometheus export
- [ ] Deploy Prometheus with updated `prometheus.yml`
- [ ] Deploy Grafana with dashboard provisioning
- [ ] Configure alertmanager for notifications
- [ ] Set up alert notification channels (Slack, PagerDuty, email)
- [ ] Test all alert rules with synthetic failures
- [ ] Monitor cardinality for `repository` and `user` tags
- [ ] Set up recording rules for expensive queries
- [ ] Configure Prometheus remote write (optional)
- [ ] Document runbooks for critical alerts
- [ ] Train team on dashboard usage
- [ ] Set up on-call rotation with alert escalation

---

## Future Enhancements

### Potential Additions
1. **Distributed Tracing** - Integrate OpenTelemetry/Jaeger for request tracing
2. **Log Aggregation** - Correlate metrics with logs via Loki/ELK
3. **SLO/SLA Tracking** - Define and track service level objectives
4. **Anomaly Detection** - ML-based anomaly detection on latency metrics
5. **Cost Forecasting** - Predict future costs based on usage trends
6. **Dashboard Variables** - Add filters for repository, user, agent type
7. **Custom Exporters** - Export metrics to DataDog, New Relic, etc.
8. **Metric Federation** - Aggregate metrics from multiple orchestrator instances

### Optimization Opportunities
1. **Recording Rules** - Pre-calculate expensive queries
2. **Downsampling** - Reduce resolution for old data
3. **Federation** - Centralize metrics from multiple clusters
4. **Push Gateway** - For short-lived jobs
5. **Cardinality Limits** - Enforce limits in application code

---

## References

- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)
- [Micrometer Documentation](https://micrometer.io/docs/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [PromQL Cheat Sheet](https://promlabs.com/promql-cheat-sheet/)

---

## Support

For questions or issues:
1. Check [PROMETHEUS_METRICS.md](../docs/PROMETHEUS_METRICS.md) for detailed documentation
2. Review [METRICS_QUICK_REFERENCE.md](./METRICS_QUICK_REFERENCE.md) for common queries
3. Inspect Grafana dashboards for visualization examples
4. Test queries in Prometheus UI (http://localhost:9090/graph)
5. Review alert rules in `alerts.yml`

---

**Implementation Date:** 2024-01-15  
**Version:** 1.0  
**Maintainer:** AI Orchestrator Team
