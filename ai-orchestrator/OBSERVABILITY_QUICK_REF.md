# Observability Quick Reference Card

## Using Correlation IDs in Your Code

```java
import com.atlasia.ai.service.observability.CorrelationIdHolder;

// Generate a new correlation ID
String correlationId = CorrelationIdHolder.generateCorrelationId();
CorrelationIdHolder.setCorrelationId(correlationId);

// Set run context
CorrelationIdHolder.setRunId(runEntity.getId());
CorrelationIdHolder.setAgentName("MY_AGENT");

// Get current correlation ID
String currentId = CorrelationIdHolder.getCorrelationId();

// Clean up (important in async contexts)
CorrelationIdHolder.clear();
```

## Recording Metrics

```java
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import io.micrometer.core.instrument.Timer;

@Autowired
private OrchestratorMetrics metrics;

// Time an operation
Timer.Sample sample = metrics.startGitHubApiTimer();
try {
    // ... do work ...
    long duration = sample.stop(metrics.startGitHubApiTimer());
    metrics.recordGitHubApiCall("/repos/owner/repo", duration);
} catch (Exception e) {
    metrics.recordGitHubApiError("/repos/owner/repo", "ERROR_TYPE");
}

// Record LLM call with tokens
metrics.recordLlmCall("gpt-4", durationMs, tokensUsed);

// Record agent step
metrics.recordAgentStepExecution("PM", "execute", durationMs);

// Record fix attempts
metrics.recordCiFixAttempt();
metrics.recordE2eFixAttempt();
```

## Throwing Custom Exceptions

```java
import com.atlasia.ai.service.exception.*;

// GitHub API error
throw new GitHubApiException("Rate limit exceeded", "/repos/owner/repo", 429);

// LLM error
throw new LlmServiceException("Context too long", "gpt-4", 
    LlmServiceException.LlmErrorType.CONTEXT_LENGTH_EXCEEDED);

// Agent step error
throw new AgentStepException("Failed to generate code", "DEVELOPER", "execute",
    OrchestratorException.RecoveryStrategy.ESCALATE_TO_HUMAN);

// Workflow error
throw new WorkflowException("Step timeout", runId, "PM",
    OrchestratorException.RecoveryStrategy.RETRY_WITH_BACKOFF);
```

## Logging Best Practices

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

private static final Logger log = LoggerFactory.getLogger(MyClass.class);

// Correlation ID is automatically included in logs
log.info("Starting operation for issue #{}", issueNumber);

// Add context to error logs
log.error("Failed to process: issueNumber={}, owner={}, correlationId={}", 
    issueNumber, owner, CorrelationIdHolder.getCorrelationId(), e);

// Use structured logging
log.debug("API call: method={}, endpoint={}, duration={}ms", 
    "GET", endpoint, duration);
```

## Checking Circuit Breaker

```java
// Circuit breaker is automatic on GitHub API calls
// To check status programmatically:

@Autowired
private CircuitBreakerRegistry circuitBreakerRegistry;

CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("githubApi");
CircuitBreaker.State state = cb.getState(); // CLOSED, OPEN, HALF_OPEN

// Manually trigger circuit breaker (for testing)
cb.transitionToOpenState();
```

## Common Monitoring Commands

```bash
# View all metrics
curl localhost:8080/actuator/metrics | jq .

# Specific metric
curl localhost:8080/actuator/metrics/orchestrator.workflow.executions.total

# Prometheus format
curl localhost:8080/actuator/prometheus | grep orchestrator

# Health check with circuit breaker
curl localhost:8080/actuator/health | jq .

# Search logs by correlation ID
grep "correlationId=abc-123" logs/ai-orchestrator.log

# Search logs by run ID  
grep "runId=550e8400" logs/ai-orchestrator.log

# Search logs by agent
grep "agent=DEVELOPER" logs/ai-orchestrator.log

# Count errors in last hour
grep "ERROR" logs/ai-orchestrator-$(date +%Y-%m-%d).log | wc -l

# Find all circuit breaker events
grep "Circuit breaker" logs/ai-orchestrator.log
```

## Metric Names Quick Reference

| Metric | Type | Description |
|--------|------|-------------|
| `orchestrator.github.api.calls.total` | Counter | Total GitHub API calls |
| `orchestrator.github.api.errors.total` | Counter | GitHub API errors |
| `orchestrator.github.api.ratelimit.total` | Counter | Rate limit hits |
| `orchestrator.github.api.duration` | Timer | API call duration |
| `orchestrator.llm.calls.total` | Counter | LLM API calls |
| `orchestrator.llm.errors.total` | Counter | LLM errors |
| `orchestrator.llm.duration` | Timer | LLM call duration |
| `orchestrator.llm.tokens.used` | Counter | Tokens consumed |
| `orchestrator.agent.step.executions.total` | Counter | Step executions |
| `orchestrator.agent.step.errors.total` | Counter | Step errors |
| `orchestrator.agent.step.duration` | Timer | Step duration |
| `orchestrator.workflow.executions.total` | Counter | Workflow runs |
| `orchestrator.workflow.success.total` | Counter | Successful workflows |
| `orchestrator.workflow.failure.total` | Counter | Failed workflows |
| `orchestrator.workflow.escalation.total` | Counter | Escalated workflows |
| `orchestrator.workflow.duration` | Timer | Workflow duration |
| `orchestrator.ci.fix.attempts.total` | Counter | CI fix iterations |
| `orchestrator.e2e.fix.attempts.total` | Counter | E2E fix iterations |

## Recovery Strategies

| Strategy | Use Case | Example |
|----------|----------|---------|
| `RETRY_WITH_BACKOFF` | Transient failures | Network errors, rate limits |
| `RETRY_IMMEDIATE` | Quick retry | Invalid response format |
| `ESCALATE_TO_HUMAN` | Cannot auto-resolve | Complex failures |
| `FALLBACK_TO_DEFAULT` | Use alternative | Context too long |
| `SKIP_AND_CONTINUE` | Non-critical step | Optional validation |
| `FAIL_FAST` | Unrecoverable | Auth errors |

## HTTP Headers

```bash
# Send request with correlation ID
curl -H "X-Correlation-ID: my-id-123" \
     -H "Authorization: Bearer token" \
     http://localhost:8080/api/v1/runs

# Response includes correlation ID
HTTP/1.1 201 Created
X-Correlation-ID: my-id-123
```

## Testing Circuit Breaker

```java
@Test
void testCircuitBreakerOpens() {
    // Mock GitHub API to fail
    when(gitHubApiClient.readIssue(any(), any(), anyInt()))
        .thenThrow(new WebClientResponseException(500, ...));
    
    // Trigger 5+ failures
    for (int i = 0; i < 6; i++) {
        try {
            gitHubApiClient.readIssue("owner", "repo", 1);
        } catch (Exception e) {
            // Expected
        }
    }
    
    // Verify circuit opened
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("githubApi");
    assertEquals(CircuitBreaker.State.OPEN, cb.getState());
}
```

## Environment Variables

```bash
# Optional: Adjust log levels
export LOGGING_LEVEL_COM_ATLASIA_AI=DEBUG
export LOGGING_LEVEL_IO_GITHUB_RESILIENCE4J=DEBUG

# Optional: Circuit breaker config
export RESILIENCE4J_CIRCUITBREAKER_INSTANCES_GITHUBAPI_FAILURERATETHRESHOLD=50
export RESILIENCE4J_CIRCUITBREAKER_INSTANCES_GITHUBAPI_WAITDURATIONINOPENSTATE=30s
```

## Troubleshooting Checklist

1. **No metrics appearing**
   - Check actuator endpoints are exposed in `application.yml`
   - Verify Spring Boot Actuator is on classpath
   - Check `/actuator/prometheus` endpoint accessible

2. **Correlation ID missing from logs**
   - Verify `logback-spring.xml` is loaded
   - Check MDC pattern includes `%X{correlationId}`
   - Ensure `CorrelationIdHolder.setCorrelationId()` called

3. **Circuit breaker not working**
   - Verify `@CircuitBreaker` annotation present
   - Check AOP is enabled (spring-boot-starter-aop)
   - Review circuit breaker configuration in `application.yml`

4. **Metrics not updating**
   - Verify `OrchestratorMetrics` bean is autowired
   - Check metrics methods are being called
   - Review application logs for metric registration errors
