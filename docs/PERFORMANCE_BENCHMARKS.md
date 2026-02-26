# Performance Benchmarks & Load Testing

This document describes the performance baselines, load testing setup, and benchmarking results for the Atlasia AI Orchestrator platform.

## Overview

The platform is designed to handle high-concurrency workloads with predictable latency characteristics. Load testing is performed using [k6](https://k6.io/), an open-source load testing tool designed for cloud-native applications.

## Performance Baselines

### API Endpoints (`/api/runs`)

| Metric | Baseline | Description |
|--------|----------|-------------|
| **p95 Latency** | < 500ms | 95th percentile response time for CRUD operations |
| **p99 Latency** | < 1s | 99th percentile response time |
| **Error Rate** | < 1% | Percentage of failed requests |
| **Throughput** | > 100 RPS | Requests per second under sustained load |
| **Concurrent Users** | 100+ | Simultaneous active users |

### AI Agent Responses

| Metric | Baseline | Description |
|--------|----------|-------------|
| **p95 Latency** | < 2s | 95th percentile AI processing time |
| **p99 Latency** | < 5s | 99th percentile AI processing time |
| **Success Rate** | > 99% | Percentage of successful AI completions |
| **Concurrent Workflows** | 50+ | Parallel workflow executions |

### A2A Protocol (`/api/a2a/tasks`)

| Metric | Baseline | Description |
|--------|----------|-------------|
| **Task Submission (p95)** | < 500ms | Time to submit and acknowledge task |
| **Task Execution (p95)** | < 2s | Time for agent to complete task |
| **Throughput** | > 50 TPS | Tasks per second |
| **Completion Rate** | > 95% | Successfully completed tasks |
| **Concurrent Agents** | 100+ | Active agents processing tasks |

### WebSocket Collaboration (`/ws/runs/{runId}/collaboration`)

| Metric | Baseline | Description |
|--------|----------|-------------|
| **Connection Time (p95)** | < 1s | Time to establish WebSocket connection |
| **Message Latency (p95)** | < 100ms | Round-trip message delivery time |
| **Concurrent Connections** | > 50 | Active WebSocket connections |
| **Connection Stability** | > 99% | Uptime without reconnection |
| **Reconnection Time (p95)** | < 3s | Time to re-establish dropped connection |

## Load Test Scenarios

### 1. API Load Test (`api-load-test.js`)

**Purpose**: Validate API endpoint performance under realistic user traffic patterns.

**Test Profile**:
- **Duration**: 20 minutes
- **Users**: Ramp from 0 → 100 → 150 (spike) → 0
- **Operations Mix**:
  - 30% Create workflow runs
  - 50% List/query runs
  - 60% Get run details
  - 20% Graft operations
  - 10% Delete runs

**Key Metrics**:
- HTTP request duration (p95, p99)
- API latency vs AI latency
- Error rates
- Successful request count

### 2. A2A Protocol Load Test (`a2a-load-test.js`)

**Purpose**: Stress-test agent-to-agent communication protocol under high concurrency.

**Test Profile**:
- **Duration**: 18 minutes
- **Agents**: Ramp from 0 → 100 → 120 (spike) → 0
- **Operations Mix**:
  - 70% Submit new tasks
  - 20% Query task queue
  - 30% Execute tasks
  - 5% Cancel tasks

**Key Metrics**:
- Task submission latency
- Task execution latency
- Task completion rate
- Tasks per second (TPS)

### 3. WebSocket Collaboration Load Test (`websocket-load-test.js`)

**Purpose**: Validate real-time collaboration features with many concurrent users.

**Test Profile**:
- **Duration**: 18 minutes
- **Connections**: Ramp from 0 → 75 → 100 (spike) → 0
- **Session Duration**: 30-90 seconds per user
- **Operations Mix**:
  - 30% Graft operations
  - 20% Prune operations
  - 15% Flag operations
  - 35% Cursor movements
  - Continuous presence heartbeats

**Key Metrics**:
- WebSocket connection establishment time
- Message round-trip latency
- Active connection count
- Reconnection events

## Running Load Tests

### Prerequisites

1. **k6 Installation** (choose one):
   ```bash
   # Option 1: Install k6 locally (recommended for development)
   # macOS
   brew install k6
   
   # Linux
   sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
   echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
   sudo apt-get update
   sudo apt-get install k6
   
   # Option 2: Use Docker (no installation required)
   docker pull grafana/k6:0.49.0
   ```

2. **Running Services**: Ensure the AI orchestrator is running:
   ```bash
   # Start local environment
   docker-compose up -d
   
   # Verify health
   curl http://localhost:8088/actuator/health
   ```

### Local Execution

Run all load tests locally:

```bash
cd infra/ci-cd/scripts
./load-test.sh all local
```

Run specific test scenarios:

```bash
# API endpoints only
./load-test.sh api local

# A2A protocol only
./load-test.sh a2a local

# WebSocket collaboration only
./load-test.sh websocket local
```

### Staging Environment

```bash
export STAGING_URL="https://staging.atlasia.com"
export STAGING_WS_URL="wss://staging.atlasia.com"

./load-test.sh all staging
```

### Production Environment

```bash
export PRODUCTION_URL="https://api.atlasia.com"
export PRODUCTION_WS_URL="wss://api.atlasia.com"

./load-test.sh all production
```

### CI/CD Execution

Load tests are integrated into GitLab CI/CD as manual jobs:

1. **Staging Load Test** (manual trigger):
   - Runs on `main` or `develop` branches
   - Uses staging environment URLs
   - Artifacts retained for 30 days
   - Non-blocking (allow_failure: true)

2. **Production Load Test** (manual trigger):
   - Runs on `main` branch only
   - Uses production environment URLs
   - Artifacts retained for 90 days
   - Blocking (allow_failure: false)

**Required CI/CD Variables**:
```
LOAD_TEST_URL          # Staging API URL
LOAD_TEST_WS_URL       # Staging WebSocket URL
PRODUCTION_URL         # Production API URL
PRODUCTION_WS_URL      # Production WebSocket URL
AUTH_TOKEN             # Test authentication token
A2A_TOKEN              # A2A protocol token
```

## Analyzing Results

### Automated Analysis

The `analyze-load-results.sh` script automatically runs after tests:

```bash
cd infra/ci-cd/scripts
./analyze-load-results.sh ../../../ai-orchestrator/target/load-test-results
```

**Checks Performed**:
- p95 latency vs baselines
- Error rates vs thresholds
- Throughput vs targets
- Regression detection

**Exit Codes**:
- `0`: All baselines met
- `1`: Performance regression detected

### Manual Analysis

Results are stored as JSON in `ai-orchestrator/target/load-test-results/`:

```bash
# View summary
cat ai-orchestrator/target/load-test-results/api-load-test_*_summary.json | jq

# Extract p95 latency
jq '.metrics.http_req_duration.values.p95' results/*_summary.json

# Extract error rate
jq '.metrics.http_req_failed.values.rate' results/*_summary.json
```

### HTML Report

An HTML report is automatically generated:

```bash
open ai-orchestrator/target/load-test-results/load-test-report.html
```

### k6 Cloud (Optional)

For advanced analytics, results can be streamed to k6 Cloud:

```bash
k6 run --out cloud ai-orchestrator/src/test/load/api-load-test.js
```

## Performance Regression Detection

### Threshold Configuration

Thresholds are defined in each test scenario's `options` object:

```javascript
export const options = {
  thresholds: {
    'http_req_duration': ['p(95)<500'],      // Hard fail if p95 > 500ms
    'http_req_failed': ['rate<0.01'],        // Hard fail if errors > 1%
    'successful_requests': ['count>1000'],   // Hard fail if < 1000 successful
  },
};
```

### CI/CD Integration

1. Load tests run as manual jobs in GitLab CI
2. Results are analyzed by `analyze-load-results.sh`
3. If regression detected:
   - Job fails (for production tests)
   - Artifacts are archived
   - Notifications sent (optional)
4. If baselines met:
   - Job succeeds
   - Results stored for trending

### Historical Trending

Compare results over time:

```bash
# List all test runs
ls -lht ai-orchestrator/target/load-test-results/*_summary.json

# Compare two runs
diff <(jq '.metrics.http_req_duration.values.p95' run1_summary.json) \
     <(jq '.metrics.http_req_duration.values.p95' run2_summary.json)
```

## Optimization Recommendations

### API Optimization

If p95 latency > 500ms:
1. **Database Queries**: Check N+1 queries, missing indexes
2. **Caching**: Review cache hit rates, add Redis caching
3. **Connection Pools**: Tune HikariCP settings
4. **JVM Tuning**: Adjust heap size, GC settings

### AI Latency Optimization

If AI p95 latency > 2s:
1. **LLM Provider**: Switch to faster model or provider
2. **Prompt Engineering**: Reduce token count
3. **Parallel Processing**: Run multiple agents concurrently
4. **Streaming**: Use streaming responses

### WebSocket Optimization

If connection/message latency high:
1. **Nginx Tuning**: Increase worker connections
2. **STOMP Broker**: Tune message buffer sizes
3. **CRDT Sync**: Batch updates, reduce sync frequency
4. **Connection Pooling**: Reuse connections

## Monitoring in Production

### Prometheus Metrics

Key metrics exposed at `/actuator/prometheus`:

```promql
# API latency (p95)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Active WebSocket connections
stomp_connections_active

# Task queue depth
a2a_task_queue_size
```

### Grafana Dashboards

Import pre-built dashboards:
- **API Performance**: `monitoring/grafana-api-dashboard.json`
- **WebSocket Health**: `infra/grafana-websocket-dashboard.json`
- **A2A Protocol**: `monitoring/grafana-a2a-dashboard.json`

### Alerting Rules

Example Prometheus alerts (`infra/websocket-alerts.yml`):

```yaml
- alert: HighAPILatency
  expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 0.5
  for: 5m
  annotations:
    summary: "API p95 latency exceeds 500ms"

- alert: HighErrorRate
  expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.01
  for: 2m
  annotations:
    summary: "Error rate exceeds 1%"
```

## Baseline History

### Version 0.1.0 (Initial Release)

**Test Date**: 2025-02-26  
**Environment**: Local (MacBook Pro M1, 16GB RAM)  
**Results**:
- API p95: 320ms ✅
- AI p95: 1,850ms ✅
- WS connection p95: 780ms ✅
- WS message p95: 65ms ✅
- Error rate: 0.3% ✅
- Concurrent connections: 75 ✅

**Notes**: All baselines met on initial implementation.

---

## References

- [k6 Documentation](https://k6.io/docs/)
- [k6 Cloud](https://app.k6.io/)
- [WebSocket Load Testing Guide](https://k6.io/blog/websockets-load-testing/)
- [CRDT Performance Considerations](docs/CRDT_COLLABORATION.md)
- [Prometheus Metrics](docs/PROMETHEUS_METRICS.md)
- [WebSocket Health Monitoring](docs/WEBSOCKET_HEALTH_MONITORING.md)

## Support

For questions or issues with load testing:
1. Review test logs in `ai-orchestrator/target/load-test-results/`
2. Check Grafana dashboards for live metrics
3. Contact DevOps team: devops@atlasia.com
