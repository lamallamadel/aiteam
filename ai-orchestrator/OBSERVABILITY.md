# AI Orchestrator - Observability & Error Handling

This document describes the comprehensive observability and error handling features implemented in the AI Orchestrator.

## Features Overview

### 1. Circuit Breaker for GitHub API
- **Library**: Resilience4j
- **Configuration**: See `application.yml` under `resilience4j.circuitbreaker.instances.githubApi`
- **Behavior**:
  - Monitors GitHub API calls for failures
  - Opens circuit after 50% failure rate (5 minimum calls in sliding window of 10)
  - Automatically transitions to half-open state after 30 seconds
  - Prevents cascading failures when GitHub API is unavailable

### 2. Structured Logging with Correlation IDs
- **MDC Context**: Every log entry includes:
  - `correlationId`: Unique ID for tracking requests across the system
  - `runId`: UUID of the workflow run
  - `agentName`: Current agent executing (PM, QUALIFIER, ARCHITECT, etc.)

- **Format**: 
  ```
  YYYY-MM-DD HH:mm:ss.SSS [thread] LEVEL logger [correlationId=xxx] [runId=yyy] [agent=zzz] - message
  ```

- **HTTP Header Support**: 
  - Accepts `X-Correlation-ID` header on incoming requests
  - Generates new correlation ID if not provided
  - Returns correlation ID in response headers

### 3. Metrics (Prometheus-compatible)
All metrics are exposed at `/actuator/prometheus` and include:

#### GitHub API Metrics
- `orchestrator.github.api.calls.total` - Total API calls
- `orchestrator.github.api.errors.total` - Total API errors
- `orchestrator.github.api.ratelimit.total` - Rate limit hits
- `orchestrator.github.api.duration` - API call duration (timer)

#### LLM API Metrics
- `orchestrator.llm.calls.total` - Total LLM calls
- `orchestrator.llm.errors.total` - Total LLM errors
- `orchestrator.llm.duration` - LLM call duration (timer)
- `orchestrator.llm.tokens.used` - Total tokens consumed

#### Agent Step Metrics
- `orchestrator.agent.step.executions.total` - Total step executions
- `orchestrator.agent.step.errors.total` - Step execution errors
- `orchestrator.agent.step.duration` - Step execution duration (timer)

#### Workflow Metrics
- `orchestrator.workflow.executions.total` - Total workflow runs
- `orchestrator.workflow.success.total` - Successful completions
- `orchestrator.workflow.failure.total` - Failed workflows
- `orchestrator.workflow.escalation.total` - Escalated workflows
- `orchestrator.workflow.duration` - Workflow duration (timer)

#### Fix Loop Metrics
- `orchestrator.ci.fix.attempts.total` - CI fix iterations
- `orchestrator.e2e.fix.attempts.total` - E2E fix iterations

### 4. Custom Exceptions with Recovery Strategies

#### Exception Hierarchy
```
OrchestratorException (abstract)
├── GitHubApiException
├── LlmServiceException
├── AgentStepException
└── WorkflowException
```

#### Recovery Strategies
Each exception includes a recovery strategy:
- `RETRY_WITH_BACKOFF` - Retry with exponential backoff
- `RETRY_IMMEDIATE` - Retry immediately
- `ESCALATE_TO_HUMAN` - Requires human intervention
- `FALLBACK_TO_DEFAULT` - Use fallback behavior
- `SKIP_AND_CONTINUE` - Skip and continue workflow
- `FAIL_FAST` - Fail immediately without retry

#### LLM Error Types
- `RATE_LIMIT` (retryable) → `RETRY_WITH_BACKOFF`
- `TIMEOUT` (retryable) → `RETRY_WITH_BACKOFF`
- `INVALID_RESPONSE` (retryable) → `RETRY_IMMEDIATE`
- `CONTEXT_LENGTH_EXCEEDED` (non-retryable) → `FALLBACK_TO_DEFAULT`
- `NETWORK_ERROR` (retryable) → `RETRY_WITH_BACKOFF`
- `AUTHENTICATION_ERROR` (non-retryable) → `FAIL_FAST`

## Usage Examples

### Accessing Metrics
```bash
# View all metrics
curl http://localhost:8080/actuator/metrics

# View specific metric
curl http://localhost:8080/actuator/metrics/orchestrator.workflow.executions.total

# Prometheus format (for scraping)
curl http://localhost:8080/actuator/prometheus
```

### Searching Logs by Correlation ID
```bash
# Filter logs by correlation ID
grep "correlationId=abc-123-def" logs/ai-orchestrator.log

# Filter by run ID
grep "runId=550e8400-e29b-41d4-a716-446655440000" logs/ai-orchestrator.log

# Filter by agent
grep "agent=DEVELOPER" logs/ai-orchestrator.log
```

### Circuit Breaker Health Check
```bash
# Check circuit breaker status
curl http://localhost:8080/actuator/health

# Response includes:
{
  "status": "UP",
  "components": {
    "circuitBreakers": {
      "status": "UP",
      "details": {
        "githubApi": {
          "status": "UP",
          "state": "CLOSED",
          "failureRate": "0.0%",
          "slowCallRate": "0.0%"
        }
      }
    }
  }
}
```

### Sending Requests with Correlation ID
```bash
# Include correlation ID in request
curl -H "X-Correlation-ID: my-custom-id-123" \
     -H "Authorization: Bearer token" \
     -X POST http://localhost:8080/api/v1/runs \
     -d '{"repo": "owner/repo", "issueNumber": 42, "mode": "full"}'

# Response will include same correlation ID in headers
```

## Configuration

### Actuator Endpoints
Configure in `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### Circuit Breaker Settings
```yaml
resilience4j:
  circuitbreaker:
    instances:
      githubApi:
        slidingWindowSize: 10          # Number of calls to track
        minimumNumberOfCalls: 5        # Min calls before evaluation
        failureRateThreshold: 50       # % failures to open circuit
        waitDurationInOpenState: 30s   # Time to wait before half-open
```

### Logging Configuration
See `logback-spring.xml` for log pattern and appender configuration.

## Monitoring Recommendations

### Alerting Thresholds
- Circuit breaker opens: Immediate alert
- LLM token usage > 1M tokens/hour: Warning
- Workflow failure rate > 20%: Warning
- GitHub API rate limit hits: Info
- Workflow duration > 30 minutes: Warning
- CI/E2E fix attempts > 2 per run: Info

### Dashboards
Consider creating dashboards tracking:
1. Workflow success/failure rates over time
2. Average step durations per agent
3. LLM token consumption trends
4. GitHub API error rates and types
5. Circuit breaker state transitions
6. Fix loop iteration counts

## Troubleshooting

### High Failure Rate
1. Check correlation ID of failures
2. Examine logs for that correlation ID
3. Check which agent/step is failing
4. Review exception type and recovery strategy
5. Check external service health (GitHub, LLM)

### Circuit Breaker Open
1. Check GitHub API status
2. Review recent error logs
3. Wait for automatic half-open transition
4. Monitor recovery

### Missing Metrics
1. Verify actuator endpoints are exposed
2. Check Prometheus scrape configuration
3. Ensure MeterRegistry bean is initialized
4. Review application logs for metric registration errors

## Future Enhancements
- Distributed tracing with OpenTelemetry
- Custom Grafana dashboards
- Alert manager integration
- Metric aggregation for multi-instance deployments
- Structured JSON logging for log aggregation systems
