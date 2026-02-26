# Load Testing Quick Reference

## Quick Start

```bash
# Run all tests locally
cd infra/ci-cd/scripts
./load-test.sh all local

# Run specific test
./load-test.sh api local        # API endpoints only
./load-test.sh a2a local        # A2A protocol only
./load-test.sh websocket local  # WebSocket only
```

## Scripts

### `load-test.sh`
Main wrapper script for running k6 load tests.

**Usage**:
```bash
./load-test.sh [scenario] [environment]
```

**Scenarios**:
- `api` - API endpoints (`/api/runs`)
- `a2a` - A2A protocol (`/api/a2a/tasks`)
- `websocket` - WebSocket collaboration
- `all` - Run all scenarios

**Environments**:
- `local` - http://localhost:8088
- `staging` - Uses `$STAGING_URL` variable
- `production` - Uses `$PRODUCTION_URL` variable

### `analyze-load-results.sh`
Analyzes k6 results and checks for performance regressions.

**Usage**:
```bash
./analyze-load-results.sh [results_dir]
```

**Exit Codes**:
- `0` - All baselines met
- `1` - Performance regression detected

## Environment Variables

### Required for Staging/Production

```bash
# Staging
export STAGING_URL="https://staging.example.com"
export STAGING_WS_URL="wss://staging.example.com"

# Production
export PRODUCTION_URL="https://api.example.com"
export PRODUCTION_WS_URL="wss://api.example.com"

# Authentication
export AUTH_TOKEN="your-auth-token"
export A2A_TOKEN="your-a2a-token"
```

### Optional

```bash
# Custom k6 Docker image
export K6_DOCKER_IMAGE="grafana/k6:latest"

# Custom results directory
export RESULTS_DIR="./custom-results"
```

## CI/CD Integration

### GitLab CI Variables

Set in **Settings → CI/CD → Variables**:

| Variable | Description | Example |
|----------|-------------|---------|
| `LOAD_TEST_URL` | Staging API URL | `https://staging.example.com` |
| `LOAD_TEST_WS_URL` | Staging WebSocket URL | `wss://staging.example.com` |
| `PRODUCTION_URL` | Production API URL | `https://api.example.com` |
| `PRODUCTION_WS_URL` | Production WebSocket URL | `wss://api.example.com` |
| `AUTH_TOKEN` | Test user token | `Bearer xxx` |
| `A2A_TOKEN` | A2A protocol token | `a2a-xxx` |

### Manual Job Triggers

```bash
# Via GitLab UI
Pipeline → load-test → load-test → ▶ Run

# Via GitLab CLI
glab ci trigger main --variable="RUN_LOAD_TESTS=true"

# Via API
curl -X POST \
  -F token=$CI_JOB_TOKEN \
  -F ref=main \
  https://gitlab.com/api/v4/projects/$PROJECT_ID/trigger/pipeline
```

## Results

### Location

```
ai-orchestrator/target/load-test-results/
├── api-load-test_20250226_143022.json
├── api-load-test_20250226_143022_summary.json
├── a2a-load-test_20250226_143525.json
├── a2a-load-test_20250226_143525_summary.json
├── websocket-load-test_20250226_144030.json
├── websocket-load-test_20250226_144030_summary.json
└── load-test-report.html
```

### View Results

```bash
# HTML report
open ai-orchestrator/target/load-test-results/load-test-report.html

# Summary JSON
jq . ai-orchestrator/target/load-test-results/*_summary.json

# Extract p95 latency
jq '.metrics.http_req_duration.values.p95' results/*_summary.json

# Extract error rate
jq '.metrics.http_req_failed.values.rate' results/*_summary.json
```

## Performance Baselines

| Metric | Baseline | Test |
|--------|----------|------|
| API p95 latency | < 500ms | `api-load-test.js` |
| AI p95 latency | < 2s | `api-load-test.js` |
| A2A task submission p95 | < 500ms | `a2a-load-test.js` |
| A2A task execution p95 | < 2s | `a2a-load-test.js` |
| WebSocket connection p95 | < 1s | `websocket-load-test.js` |
| WebSocket message p95 | < 100ms | `websocket-load-test.js` |
| Error rate | < 1% | All tests |
| Concurrent connections | > 50 | `websocket-load-test.js` |

## Troubleshooting

### Services Not Running

**Error**:
```
❌ API health check failed: Connection refused
```

**Solution**:
```bash
docker-compose up -d
curl http://localhost:8088/actuator/health
```

### Permission Denied

**Error**:
```
bash: ./load-test.sh: Permission denied
```

**Solution**:
```bash
chmod +x infra/ci-cd/scripts/load-test.sh
chmod +x infra/ci-cd/scripts/analyze-load-results.sh
```

### k6 Not Found

**Error**:
```
❌ Neither k6 nor Docker is available
```

**Solution**:
```bash
# Install k6
brew install k6  # macOS

# Or use Docker
docker pull grafana/k6:0.49.0
```

### High Error Rates

**Symptoms**:
- Error rate > 1%
- Many 500/503 responses

**Checks**:
1. Database connection pool size
2. Rate limiting configuration
3. Memory/CPU utilization
4. Network latency

**Solutions**:
```bash
# Check container resources
docker stats

# Check application logs
docker logs ai-orchestrator

# Reduce load profile
# Edit test file: reduce target users/duration
```

### WebSocket Connection Failures

**Symptoms**:
- Connection timeouts
- High reconnection rate

**Checks**:
1. Nginx WebSocket proxy config
2. STOMP broker settings
3. Firewall rules

**Solutions**:
```bash
# Verify WebSocket endpoint
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  http://localhost:8088/ws/runs/1/collaboration

# Check Nginx config
grep -A10 "websocket" nginx.conf
```

## Advanced Usage

### Custom Load Profile

Edit test file and modify `options.stages`:

```javascript
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // Warm up
    { duration: '2m', target: 50 },   // Ramp up
    { duration: '5m', target: 100 },  // Sustain
    { duration: '30s', target: 0 },   // Cool down
  ],
};
```

### Custom Thresholds

```javascript
export const options = {
  thresholds: {
    'http_req_duration': ['p(95)<300'],  // Stricter: 300ms
    'http_req_duration{type:ai}': ['p(95)<1500'],  // 1.5s
    'errors': ['rate<0.005'],  // 0.5%
  },
};
```

### Stream to k6 Cloud

```bash
K6_CLOUD_TOKEN=xxx k6 run --out cloud api-load-test.js
```

### Run with Custom Script

```bash
k6 run \
  --vus 100 \
  --duration 10m \
  --out json=results.json \
  -e BASE_URL=http://localhost:8088 \
  ai-orchestrator/src/test/load/api-load-test.js
```

## References

- [Load Test Scenarios](../../../ai-orchestrator/src/test/load/README.md)
- [Performance Benchmarks](../../../docs/PERFORMANCE_BENCHMARKS.md)
- [k6 Documentation](https://k6.io/docs/)
- [CI/CD Setup](../README.md)
