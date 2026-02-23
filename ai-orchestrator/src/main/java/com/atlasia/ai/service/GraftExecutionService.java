package com.atlasia.ai.service;

import com.atlasia.ai.model.GraftExecutionEntity;
import com.atlasia.ai.model.GraftExecutionEntity.GraftExecutionStatus;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.GraftExecutionRepository;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.event.WorkflowEvent;
import com.atlasia.ai.service.event.WorkflowEventBus;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * A2A Graft Execution Engine — Runtime injection of grafted agents at pipeline checkpoints.
 *
 * <p>Implements the complete graft lifecycle:
 * <ol>
 *   <li><b>Pause</b>: After designated checkpoint (e.g., ARCHITECT), workflow pauses graft execution.</li>
 *   <li><b>Discover</b>: Dynamically discovers grafted agent via A2ADiscoveryService using agent name.</li>
 *   <li><b>Invoke</b>: Invokes grafted agent with environment snapshot + context from previous steps.</li>
 *   <li><b>Capture</b>: Captures graft output as artifact and persists to graft_executions table.</li>
 *   <li><b>Resume</b>: Main pipeline continues, with executed grafts tracked in executed_grafts field.</li>
 * </ol>
 *
 * <p><b>Circuit Breaker</b>: Each agent has independent circuit breaker state (not shared with main agent pools).
 * Circuit opens after 5 consecutive failures within 5-minute window. State: CLOSED → OPEN → HALF_OPEN → CLOSED.
 *
 * <p><b>Retry Logic</b>: Up to 3 retries with exponential backoff (1s, 2s, 3s). Separate from main agent retry.
 *
 * <p><b>Timeout Control</b>: Default 5 minutes per graft, configurable via pending_grafts JSON (timeoutMs field).
 *
 * <p><b>Resumability</b>: RunEntity tracks executed_grafts vs pending_grafts for pause/resume scenarios.
 *
 * <p><b>SSE Events</b>: Emits GRAFT_START, GRAFT_COMPLETE, GRAFT_FAILED events in real-time for UI progress.
 */
@Service
public class GraftExecutionService {
    private static final Logger log = LoggerFactory.getLogger(GraftExecutionService.class);

    private static final int MAX_RETRIES = 3;
    private static final long DEFAULT_TIMEOUT_MS = 300_000L;
    private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5;
    private static final Duration CIRCUIT_BREAKER_RESET_WINDOW = Duration.ofMinutes(5);

    private final GraftExecutionRepository graftExecutionRepository;
    private final RunRepository runRepository;
    private final AgentStepFactory agentStepFactory;
    private final A2ADiscoveryService a2aDiscoveryService;
    private final WorkflowEventBus eventBus;
    private final OrchestratorMetrics metrics;
    private final ObjectMapper objectMapper;

    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();

    public GraftExecutionService(
            GraftExecutionRepository graftExecutionRepository,
            RunRepository runRepository,
            AgentStepFactory agentStepFactory,
            A2ADiscoveryService a2aDiscoveryService,
            WorkflowEventBus eventBus,
            OrchestratorMetrics metrics,
            ObjectMapper objectMapper) {
        this.graftExecutionRepository = graftExecutionRepository;
        this.runRepository = runRepository;
        this.agentStepFactory = agentStepFactory;
        this.a2aDiscoveryService = a2aDiscoveryService;
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /**
     * Execute all pending grafts scheduled to run after the given checkpoint.
     *
     * <p>Called by WorkflowEngine after each pipeline step completes. Filters pending_grafts
     * JSON array for grafts with matching "after" field, dynamically discovers the agent,
     * invokes it with the current RunContext (which includes environment snapshot + all
     * prior step outputs), captures the output as an artifact, and updates executed_grafts.
     *
     * <p>If circuit breaker is OPEN for an agent, the graft is marked CIRCUIT_OPEN and skipped.
     * Failed grafts remain in pending_grafts for retry on next checkpoint (or manual retry).
     *
     * @param runId      the workflow run ID
     * @param checkpoint the completed step name (e.g., "ARCHITECT", "DEVELOPER")
     * @param context    the current RunContext with all prior step outputs
     * @param runEntity  the RunEntity to persist graft metadata
     */
    @Transactional
    public void executeGraftsAfterCheckpoint(UUID runId, String checkpoint, RunContext context, RunEntity runEntity) {
        String pendingGraftsJson = runEntity.getPendingGrafts();
        if (pendingGraftsJson == null || pendingGraftsJson.isBlank() || pendingGraftsJson.equals("[]")) {
            return;
        }

        try {
            JsonNode graftsArray = objectMapper.readTree(pendingGraftsJson);
            List<JsonNode> remaining = new ArrayList<>();
            List<Map<String, Object>> executed = new ArrayList<>();

            String existingExecuted = runEntity.getExecutedGrafts();
            if (existingExecuted != null && !existingExecuted.isBlank() && !existingExecuted.equals("[]")) {
                JsonNode existingArr = objectMapper.readTree(existingExecuted);
                for (JsonNode node : existingArr) {
                    executed.add(objectMapper.convertValue(node, Map.class));
                }
            }

            for (JsonNode graftNode : graftsArray) {
                String after = graftNode.path("after").asText("");
                String agentName = graftNode.path("agentName").asText("");
                String graftId = graftNode.path("graftId").asText(UUID.randomUUID().toString());
                long timeoutMs = graftNode.path("timeoutMs").asLong(DEFAULT_TIMEOUT_MS);

                if (!checkpoint.equalsIgnoreCase(after)) {
                    remaining.add(graftNode);
                    continue;
                }

                log.info("GRAFT: executing graft_id={}, agent={}, checkpoint={}: runId={}",
                        graftId, agentName, checkpoint, runId);

                GraftExecutionEntity execution = new GraftExecutionEntity(
                        runId, graftId, agentName, checkpoint, timeoutMs);

                if (isCircuitOpen(agentName)) {
                    log.warn("GRAFT: circuit breaker OPEN for agent={}, skipping graft_id={}: runId={}",
                            agentName, graftId, runId);
                    execution.setStatus(GraftExecutionStatus.CIRCUIT_OPEN);
                    execution.setErrorMessage("Circuit breaker open due to recent failures");
                    execution.setCompletedAt(Instant.now());
                    graftExecutionRepository.save(execution);
                    metrics.recordGraftCircuitOpen(agentName);

                    emitAndTrace(runId, new WorkflowEvent.GraftFailed(
                            runId, Instant.now(), graftId, agentName, "CIRCUIT_OPEN",
                            "Circuit breaker open"));

                    executed.add(Map.of(
                            "graftId", graftId,
                            "agentName", agentName,
                            "checkpoint", checkpoint,
                            "status", "CIRCUIT_OPEN",
                            "completedAt", Instant.now().toString()
                    ));
                    continue;
                }

                GraftExecutionResult result = executeGraftWithRetry(execution, context, runEntity);

                if (result.success()) {
                    executed.add(Map.of(
                            "graftId", graftId,
                            "agentName", agentName,
                            "checkpoint", checkpoint,
                            "status", "COMPLETED",
                            "artifactId", result.artifactId() != null ? result.artifactId().toString() : "",
                            "completedAt", Instant.now().toString()
                    ));
                } else {
                    remaining.add(graftNode);
                    executed.add(Map.of(
                            "graftId", graftId,
                            "agentName", agentName,
                            "checkpoint", checkpoint,
                            "status", result.status(),
                            "errorMessage", result.errorMessage() != null ? result.errorMessage() : "",
                            "completedAt", Instant.now().toString()
                    ));
                }
            }

            runEntity.setPendingGrafts(remaining.isEmpty() ? "[]" : objectMapper.writeValueAsString(remaining));
            runEntity.setExecutedGrafts(objectMapper.writeValueAsString(executed));
            runRepository.save(runEntity);

        } catch (Exception e) {
            log.error("GRAFT: failed to process pending grafts: runId={}", runId, e);
        }
    }

    private GraftExecutionResult executeGraftWithRetry(GraftExecutionEntity execution, RunContext context, RunEntity runEntity) {
        UUID runId = execution.getRunId();
        String graftId = execution.getGraftId();
        String agentName = execution.getAgentName();
        long timeoutMs = execution.getTimeoutMs();

        Timer.Sample graftTimer = metrics.startGraftTimer();
        metrics.recordGraftExecution(agentName);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                execution.setStatus(GraftExecutionStatus.RUNNING);
                execution.setStartedAt(Instant.now());
                graftExecutionRepository.save(execution);

                emitAndTrace(runId, new WorkflowEvent.GraftStart(
                        runId, Instant.now(), graftId, agentName, execution.getCheckpointAfter()));

                log.info("GRAFT: attempt {}/{} for graft_id={}, agent={}: runId={}",
                        attempt + 1, MAX_RETRIES + 1, graftId, agentName, runId);

                GraftExecutionResult result = executeGraftWithTimeout(execution, context, runEntity, timeoutMs);

                if (result.success()) {
                    execution.setStatus(GraftExecutionStatus.COMPLETED);
                    execution.setCompletedAt(Instant.now());
                    execution.setOutputArtifactId(result.artifactId());
                    graftExecutionRepository.save(execution);

                    long duration = Duration.between(execution.getStartedAt(), execution.getCompletedAt()).toMillis();
                    graftTimer.stop(metrics.getGraftDuration());
                    metrics.recordGraftSuccess(agentName, duration);
                    recordSuccess(agentName);

                    emitAndTrace(runId, new WorkflowEvent.GraftComplete(
                            runId, Instant.now(), graftId, agentName, duration, result.artifactId()));

                    log.info("GRAFT: completed graft_id={}, agent={}, duration={}ms: runId={}",
                            graftId, agentName, duration, runId);

                    return result;
                } else if (result.status().equals("TIMEOUT")) {
                    execution.setStatus(GraftExecutionStatus.TIMEOUT);
                    execution.setErrorMessage(result.errorMessage());
                    execution.setCompletedAt(Instant.now());
                    execution.incrementRetryCount();
                    graftExecutionRepository.save(execution);

                    graftTimer.stop(metrics.getGraftDuration());
                    metrics.recordGraftTimeout(agentName);
                    recordFailure(agentName);

                    emitAndTrace(runId, new WorkflowEvent.GraftFailed(
                            runId, Instant.now(), graftId, agentName, "TIMEOUT", result.errorMessage()));

                    log.warn("GRAFT: timeout for graft_id={}, agent={}, attempt={}: runId={}",
                            graftId, agentName, attempt + 1, runId);

                    if (attempt < MAX_RETRIES) {
                        continue;
                    }
                    return result;
                } else {
                    execution.incrementRetryCount();
                    graftExecutionRepository.save(execution);

                    if (attempt < MAX_RETRIES) {
                        log.warn("GRAFT: failed attempt {}/{} for graft_id={}, agent={}, retrying: runId={}",
                                attempt + 1, MAX_RETRIES + 1, graftId, agentName, runId);
                        Thread.sleep(1000 * (attempt + 1));
                        continue;
                    }

                    execution.setStatus(GraftExecutionStatus.FAILED);
                    execution.setErrorMessage(result.errorMessage());
                    execution.setCompletedAt(Instant.now());
                    graftExecutionRepository.save(execution);

                    graftTimer.stop(metrics.getGraftDuration());
                    metrics.recordGraftFailure(agentName);
                    recordFailure(agentName);

                    emitAndTrace(runId, new WorkflowEvent.GraftFailed(
                            runId, Instant.now(), graftId, agentName, "EXECUTION_FAILED", result.errorMessage()));

                    log.error("GRAFT: failed after {} attempts for graft_id={}, agent={}: runId={}",
                            MAX_RETRIES + 1, graftId, agentName, runId);

                    return result;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                execution.setStatus(GraftExecutionStatus.FAILED);
                execution.setErrorMessage("Interrupted");
                execution.setCompletedAt(Instant.now());
                graftExecutionRepository.save(execution);

                graftTimer.stop(metrics.getGraftDuration());
                metrics.recordGraftFailure(agentName);

                return new GraftExecutionResult(false, null, "FAILED", "Interrupted");
            } catch (Exception e) {
                log.error("GRAFT: unexpected error during retry attempt {}/{} for graft_id={}, agent={}: runId={}",
                        attempt + 1, MAX_RETRIES + 1, graftId, agentName, runId, e);

                if (attempt >= MAX_RETRIES) {
                    execution.setStatus(GraftExecutionStatus.FAILED);
                    execution.setErrorMessage(e.getMessage());
                    execution.setCompletedAt(Instant.now());
                    graftExecutionRepository.save(execution);

                    graftTimer.stop(metrics.getGraftDuration());
                    metrics.recordGraftFailure(agentName);
                    recordFailure(agentName);

                    emitAndTrace(runId, new WorkflowEvent.GraftFailed(
                            runId, Instant.now(), graftId, agentName, "EXCEPTION", e.getMessage()));

                    return new GraftExecutionResult(false, null, "FAILED", e.getMessage());
                }
            }
        }

        return new GraftExecutionResult(false, null, "FAILED", "Max retries exceeded");
    }

    private GraftExecutionResult executeGraftWithTimeout(
            GraftExecutionEntity execution, RunContext context, RunEntity runEntity, long timeoutMs) {
        
        String agentName = execution.getAgentName();
        AgentCard card = a2aDiscoveryService.getAgent(agentName);
        
        if (card == null) {
            log.warn("GRAFT: agent card not found for agentName={}, attempting role discovery: runId={}",
                    agentName, execution.getRunId());
            
            String role = mapAgentNameToRole(agentName);
            if (role == null) {
                return new GraftExecutionResult(false, null, "FAILED",
                        "Agent not found in A2A registry: " + agentName);
            }
            card = a2aDiscoveryService.discoverForRole(role, Set.of());
            if (card == null) {
                return new GraftExecutionResult(false, null, "FAILED",
                        "No agent discovered for role: " + role);
            }
        }

        final AgentCard finalCard = card;
        final String agentRole = finalCard.role();
        final Set<String> agentCapabilities = finalCard.capabilities();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            try {
                log.info("GRAFT: invoking agent role={} for graft_id={}: runId={}",
                        agentRole, execution.getGraftId(), execution.getRunId());
                
                return agentStepFactory.resolveForRole(agentRole, agentCapabilities).execute(context);
            } catch (Exception e) {
                log.error("GRAFT: execution error for graft_id={}, agent={}: runId={}",
                        execution.getGraftId(), agentName, execution.getRunId(), e);
                throw new RuntimeException("Graft execution failed: " + e.getMessage(), e);
            }
        });

        try {
            String artifact = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
            RunArtifactEntity artifactEntity = new RunArtifactEntity(
                    agentName,
                    "graft_output_" + execution.getGraftId(),
                    artifact,
                    Instant.now()
            );
            runEntity.addArtifact(artifactEntity);
            runRepository.save(runEntity);

            UUID artifactId = artifactEntity.getId();
            
            log.info("GRAFT: artifact persisted for graft_id={}, artifactId={}: runId={}",
                    execution.getGraftId(), artifactId, execution.getRunId());

            return new GraftExecutionResult(true, artifactId, "COMPLETED", null);

        } catch (TimeoutException e) {
            future.cancel(true);
            return new GraftExecutionResult(false, null, "TIMEOUT",
                    "Graft execution exceeded timeout of " + timeoutMs + "ms");
        } catch (ExecutionException e) {
            return new GraftExecutionResult(false, null, "FAILED",
                    "Execution error: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return new GraftExecutionResult(false, null, "FAILED", "Interrupted");
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean isCircuitOpen(String agentName) {
        CircuitBreakerState state = circuitBreakers.get(agentName);
        if (state == null) {
            return false;
        }

        if (state.state == CircuitState.OPEN) {
            if (Duration.between(state.lastFailureTime, Instant.now()).compareTo(CIRCUIT_BREAKER_RESET_WINDOW) > 0) {
                state.state = CircuitState.HALF_OPEN;
                state.failureCount = 0;
                log.info("GRAFT: circuit breaker HALF_OPEN for agent={}", agentName);
                return false;
            }
            return true;
        }

        return false;
    }

    private void recordSuccess(String agentName) {
        CircuitBreakerState state = circuitBreakers.computeIfAbsent(agentName, k -> new CircuitBreakerState());
        state.failureCount = 0;
        if (state.state == CircuitState.HALF_OPEN) {
            state.state = CircuitState.CLOSED;
            log.info("GRAFT: circuit breaker CLOSED for agent={}", agentName);
        }
    }

    private void recordFailure(String agentName) {
        CircuitBreakerState state = circuitBreakers.computeIfAbsent(agentName, k -> new CircuitBreakerState());
        state.failureCount++;
        state.lastFailureTime = Instant.now();

        if (state.failureCount >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
            state.state = CircuitState.OPEN;
            log.warn("GRAFT: circuit breaker OPEN for agent={}, failures={}", agentName, state.failureCount);
        }
    }

    private String mapAgentNameToRole(String agentName) {
        Map<String, String> agentToRole = Map.of(
                "pm-v1", "PM",
                "qualifier-v1", "QUALIFIER",
                "architect-v1", "ARCHITECT",
                "tester-v1", "TESTER",
                "writer-v1", "WRITER"
        );
        return agentToRole.get(agentName);
    }

    private void emitAndTrace(UUID runId, WorkflowEvent event) {
        eventBus.emit(runId, event);
    }

    private static class CircuitBreakerState {
        CircuitState state = CircuitState.CLOSED;
        int failureCount = 0;
        Instant lastFailureTime = Instant.now();
    }

    private enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    private record GraftExecutionResult(boolean success, UUID artifactId, String status, String errorMessage) {}
}
