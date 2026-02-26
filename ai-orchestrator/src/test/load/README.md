# K6 Load Tests

This directory contains k6 load test scenarios for the Atlasia AI Orchestrator platform.

## Test Scenarios

### 1. `api-load-test.js`
Tests `/api/runs` endpoints with realistic user traffic patterns.
- **Target**: 100+ concurrent users
- **Duration**: ~20 minutes
- **Focus**: CRUD operations, workflow management

### 2. `a2a-load-test.js`
Tests `/api/a2a/tasks` agent-to-agent protocol.
- **Target**: 100+ concurrent agents
- **Duration**: ~18 minutes
- **Focus**: Task submission, execution, queue management

### 3. `websocket-load-test.js`
Tests `/ws/runs/{runId}/collaboration` real-time collaboration.
- **Target**: 50+ concurrent connections
- **Duration**: ~18 minutes
- **Focus**: Message latency, connection stability

## Running Tests

### Quick Start

```bash
# From repository root
cd infra/ci-cd/scripts
./load-test.sh all local
```

### Individual Tests

```bash
# API only
k6 run -e BASE_URL=http://localhost:8088 ai-orchestrator/src/test/load/api-load-test.js

# A2A only
k6 run -e BASE_URL=http://localhost:8088 ai-orchestrator/src/test/load/a2a-load-test.js

# WebSocket only
k6 run -e BASE_URL=http://localhost:8088 -e WS_URL=ws://localhost:8088 ai-orchestrator/src/test/load/websocket-load-test.js
```

### With Docker

```bash
docker run --rm --network host \
  -v $(pwd)/ai-orchestrator/src/test/load:/scripts \
  -e BASE_URL=http://localhost:8088 \
  -e WS_URL=ws://localhost:8088 \
  grafana/k6:0.49.0 \
  run /scripts/api-load-test.js
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `BASE_URL` | API base URL | `http://localhost:8088` |
| `WS_URL` | WebSocket base URL | `ws://localhost:8088` |
| `AUTH_TOKEN` | Bearer token for authentication | `test-token-123` |
| `A2A_TOKEN` | A2A protocol authentication | `a2a-secret-token` |

## Performance Baselines

See [PERFORMANCE_BENCHMARKS.md](../../../docs/PERFORMANCE_BENCHMARKS.md) for detailed baselines.

**Summary**:
- API p95 latency: < 500ms
- AI p95 latency: < 2s
- WebSocket connection: < 1s
- WebSocket message: < 100ms
- Error rate: < 1%

## Results

Results are saved to `ai-orchestrator/target/load-test-results/`:
- `*_summary.json`: Test summary with aggregated metrics
- `*.json`: Detailed time-series data
- `load-test-report.html`: HTML report

## Customization

### Adjust Load Profile

Edit the `options.stages` array in each test:

```javascript
export const options = {
  stages: [
    { duration: '1m', target: 20 },   // Customize duration and target
    { duration: '3m', target: 50 },
    // Add more stages...
  ],
};
```

### Adjust Thresholds

Edit the `options.thresholds` object:

```javascript
export const options = {
  thresholds: {
    'http_req_duration': ['p(95)<500'],  // Adjust threshold
    'errors': ['rate<0.01'],              // Adjust error rate
  },
};
```

## CI/CD Integration

Tests run automatically in GitLab CI as manual jobs:

```bash
# Trigger via GitLab UI
# Pipeline → load-test stage → load-test job → ▶ Run

# Or via API
curl -X POST \
  -F token=<JOB_TOKEN> \
  -F ref=main \
  https://gitlab.com/api/v4/projects/<PROJECT_ID>/trigger/pipeline
```

## Troubleshooting

### Connection Refused

```
ERRO[0001] GoError: Get "http://localhost:8088/actuator/health": dial tcp: connect: connection refused
```

**Solution**: Ensure services are running:
```bash
docker-compose up -d
curl http://localhost:8088/actuator/health
```

### High Error Rate

**Check**:
1. Database connection pool exhaustion
2. Rate limiting rules
3. Memory/CPU constraints
4. Network latency

**Solution**: Scale resources or reduce load profile.

### WebSocket Connection Failures

**Check**:
1. Nginx WebSocket proxy configuration
2. STOMP broker settings
3. Connection timeout settings

**Solution**: Review `nginx.conf` and Spring WebSocket config.

## Additional Resources

- [k6 Documentation](https://k6.io/docs/)
- [k6 Examples](https://k6.io/docs/examples/)
- [Performance Benchmarks](../../../docs/PERFORMANCE_BENCHMARKS.md)
- [CI/CD Setup](../../../infra/ci-cd/README.md)
