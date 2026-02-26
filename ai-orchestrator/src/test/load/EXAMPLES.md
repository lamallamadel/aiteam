# K6 Load Testing Examples

This document provides practical examples for running load tests in different scenarios.

## Prerequisites

```bash
# Install k6 (choose one method)
# macOS
brew install k6

# Linux
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# Docker (no installation required)
docker pull grafana/k6:0.49.0
```

## Example 1: Quick Smoke Test

**Purpose**: Verify basic functionality before full load tests.

```bash
# Start services
docker-compose up -d

# Wait for health check
sleep 10
curl http://localhost:8088/actuator/health

# Run smoke test (1 user, 1 minute)
cd ai-orchestrator/src/test/load
k6 run smoke-test.js

# Expected output:
#   ✓ health check is 200
#   ✓ list runs is 200
#   checks.........................: 100.00% ✓ 60  ✗ 0
#   http_req_duration..............: avg=45ms  p(95)=120ms
```

## Example 2: Local API Load Test

**Purpose**: Test API endpoints on local development environment.

```bash
# Run with default settings (100+ users, 20 minutes)
k6 run -e BASE_URL=http://localhost:8088 api-load-test.js

# Run with reduced load (faster)
k6 run --vus 20 --duration 2m -e BASE_URL=http://localhost:8088 api-load-test.js

# Save results to file
k6 run --out json=results.json api-load-test.js

# Expected output:
#   ✓ create run status is 201
#   ✓ list runs status is 200
#   http_req_duration..............: avg=235ms  p(95)=420ms
#   iterations.....................: 8542   71/s
```

## Example 3: A2A Protocol Stress Test

**Purpose**: Test agent-to-agent communication under high load.

```bash
# Run with authentication
export AUTH_TOKEN="your-token-here"
export A2A_TOKEN="your-a2a-token"

k6 run \
  -e BASE_URL=http://localhost:8088 \
  -e AUTH_TOKEN=$AUTH_TOKEN \
  -e A2A_TOKEN=$A2A_TOKEN \
  a2a-load-test.js

# Run with custom load profile (spike test)
k6 run --stage 30s:10 --stage 10s:100 --stage 30s:10 \
  -e BASE_URL=http://localhost:8088 \
  a2a-load-test.js

# Expected output:
#   ✓ task submitted successfully
#   ✓ task has id
#   task_submission_latency........: avg=198ms  p(95)=385ms
#   tasks_submitted................: 12453
#   task_completion_rate...........: 96.5%
```

## Example 4: WebSocket Load Test

**Purpose**: Test real-time collaboration with multiple concurrent connections.

```bash
# Run with WebSocket URL
k6 run \
  -e BASE_URL=http://localhost:8088 \
  -e WS_URL=ws://localhost:8088 \
  websocket-load-test.js

# Test with 50 concurrent connections
k6 run --vus 50 --duration 5m \
  -e WS_URL=ws://localhost:8088 \
  websocket-load-test.js

# Expected output:
#   ✓ WebSocket connected successfully
#   ws_connection_time.............: avg=412ms  p(95)=780ms
#   ws_message_latency.............: avg=43ms   p(95)=87ms
#   active_ws_connections..........: min=1 max=75
#   ws_errors......................: 0.2%
```

## Example 5: Staging Environment Test

**Purpose**: Test against staging environment before production release.

```bash
# Set environment variables
export STAGING_URL="https://staging.atlasia.com"
export STAGING_WS_URL="wss://staging.atlasia.com"
export AUTH_TOKEN="staging-token"

# Run all tests via wrapper script
cd infra/ci-cd/scripts
./load-test.sh all staging

# Or run individually
k6 run -e BASE_URL=$STAGING_URL api-load-test.js
k6 run -e BASE_URL=$STAGING_URL a2a-load-test.js
k6 run -e BASE_URL=$STAGING_URL -e WS_URL=$STAGING_WS_URL websocket-load-test.js
```

## Example 6: Production Monitoring

**Purpose**: Continuous load testing for production monitoring.

```bash
# Run with production URLs (read-only operations)
export PRODUCTION_URL="https://api.atlasia.com"
export AUTH_TOKEN="prod-readonly-token"

# Run in background with reduced load
k6 run --vus 10 --duration 60m \
  -e BASE_URL=$PRODUCTION_URL \
  api-load-test.js > load-test.log 2>&1 &

# Monitor progress
tail -f load-test.log

# Or schedule with cron (every hour)
# 0 * * * * cd /path/to/repo && k6 run --vus 5 --duration 5m api-load-test.js
```

## Example 7: CI/CD Integration

**Purpose**: Run as part of GitLab CI/CD pipeline.

```yaml
# In .gitlab-ci.yml (already configured)
load-test:
  stage: load-test
  when: manual
  script:
    - bash infra/ci-cd/scripts/load-test.sh all staging
```

**Trigger manually**:
```bash
# Via GitLab UI
# Pipeline → load-test → load-test → ▶ Run

# Via GitLab CLI
glab ci run

# Via API
curl -X POST \
  -H "PRIVATE-TOKEN: $GITLAB_TOKEN" \
  "https://gitlab.com/api/v4/projects/$PROJECT_ID/jobs/$JOB_ID/play"
```

## Example 8: Docker-based Testing

**Purpose**: Run tests without installing k6 locally.

```bash
# Run API test
docker run --rm --network host \
  -v $(pwd):/scripts \
  -e BASE_URL=http://localhost:8088 \
  grafana/k6:0.49.0 \
  run /scripts/api-load-test.js

# Run all tests with wrapper script
docker run --rm --network host \
  -v $(pwd):/workspace \
  -w /workspace \
  grafana/k6:0.49.0 \
  sh -c "cd infra/ci-cd/scripts && ./load-test.sh all local"

# Run with custom parameters
docker run --rm --network host \
  -v $(pwd)/ai-orchestrator/src/test/load:/scripts \
  -v $(pwd)/ai-orchestrator/target/load-test-results:/results \
  -e BASE_URL=http://localhost:8088 \
  grafana/k6:0.49.0 \
  run --out json=/results/api-test.json /scripts/api-load-test.js
```

## Example 9: Performance Regression Detection

**Purpose**: Detect performance degradation compared to baseline.

```bash
# Run test and analyze results
cd infra/ci-cd/scripts
./load-test.sh all local

# Analyze results (automatic)
./analyze-load-results.sh ../../../ai-orchestrator/target/load-test-results

# Expected output if regression:
#   ❌ REGRESSION: p95 latency (650ms) exceeds baseline (500ms)
#   Exit code: 1

# Expected output if passing:
#   ✅ p95 latency within baseline
#   ✅ Error rate within baseline
#   Exit code: 0
```

## Example 10: Custom Test Scenarios

**Purpose**: Create custom test for specific use case.

```javascript
// custom-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 10 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<300'], // Custom threshold
  },
};

export default function() {
  const res = http.get(`${__ENV.BASE_URL}/api/custom-endpoint`);
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);
}
```

```bash
# Run custom test
k6 run -e BASE_URL=http://localhost:8088 custom-test.js
```

## Example 11: Analyzing Results

**Purpose**: Extract and analyze metrics from test results.

```bash
# View summary
cat ai-orchestrator/target/load-test-results/*_summary.json | jq

# Extract p95 latency
jq '.metrics.http_req_duration.values.p95' results/api-load-test_*_summary.json

# Extract error rate
jq '.metrics.http_req_failed.values.rate' results/*_summary.json

# Compare two test runs
diff \
  <(jq '.metrics.http_req_duration.values' run1_summary.json) \
  <(jq '.metrics.http_req_duration.values' run2_summary.json)

# Generate CSV report
jq -r '.metrics | to_entries[] | [.key, .value.values.avg, .value.values.p95] | @csv' \
  results/*_summary.json > metrics.csv
```

## Example 12: Load Test with Grafana k6 Cloud

**Purpose**: Stream results to k6 Cloud for advanced analytics.

```bash
# Sign up for free account at https://app.k6.io/

# Get API token from account settings
export K6_CLOUD_TOKEN="your-cloud-token"

# Run test with cloud output
k6 run --out cloud api-load-test.js

# View results in browser
# https://app.k6.io/runs/<run-id>

# Run and stream to cloud + local JSON
k6 run --out cloud --out json=local-results.json api-load-test.js
```

## Tips & Best Practices

### 1. Start Small
```bash
# Always start with smoke test
k6 run smoke-test.js

# Then gradually increase load
k6 run --vus 10 --duration 2m api-load-test.js
k6 run --vus 50 --duration 5m api-load-test.js
k6 run --vus 100 --duration 10m api-load-test.js
```

### 2. Monitor System Resources
```bash
# While test is running
# Terminal 1: Run test
k6 run api-load-test.js

# Terminal 2: Monitor resources
docker stats

# Terminal 3: Monitor logs
docker logs -f ai-orchestrator
```

### 3. Clean Up Between Tests
```bash
# Reset database
docker-compose down -v
docker-compose up -d

# Wait for services to be ready
sleep 30
curl http://localhost:8088/actuator/health
```

### 4. Save Baselines
```bash
# Save first run as baseline
mkdir -p baselines
cp ai-orchestrator/target/load-test-results/*_summary.json baselines/

# Compare against baseline
jq -r '.metrics.http_req_duration.values.p95' baselines/api-load-test_*_summary.json
jq -r '.metrics.http_req_duration.values.p95' ai-orchestrator/target/load-test-results/api-load-test_*_summary.json
```

## Troubleshooting

### Connection Refused
```bash
# Check if services are running
docker-compose ps

# Check health
curl http://localhost:8088/actuator/health

# Check logs
docker logs ai-orchestrator
```

### High Error Rate
```bash
# Reduce concurrent users
k6 run --vus 10 api-load-test.js

# Increase think time (edit test file)
sleep(5); // Increase from 1-3 to 5 seconds

# Check rate limits
curl -I http://localhost:8088/api/runs
# Look for: X-RateLimit-Limit, X-RateLimit-Remaining
```

### Out of Memory
```bash
# Increase Docker memory
# Docker Desktop → Preferences → Resources → Memory: 8GB

# Or increase Java heap
# docker-compose.yml:
#   JAVA_OPTS: "-Xmx4g -Xms2g"
```

## References

- [K6 Documentation](https://k6.io/docs/)
- [K6 Examples](https://k6.io/docs/examples/)
- [Performance Benchmarks](../../../docs/PERFORMANCE_BENCHMARKS.md)
- [Load Testing Quick Reference](../../../infra/ci-cd/scripts/LOAD_TESTING_QUICKREF.md)
