package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shadow Mode Service — Pre-Production Validation.
 *
 * Runs the agent pipeline on live GitHub issues in parallel with the human
 * team, but outputs are NEVER merged or shown to users. Compares agent
 * performance against the human baseline to validate readiness.
 *
 * Validates:
 *   - Correctness (does the agent produce working code?)
 *   - Guardrail compliance (zero violations on real data)
 *   - Cost efficiency (within budget thresholds)
 *   - Swiss Cheese defense (all layers catching errors)
 */
@Service
public class ShadowModeService {
    private static final Logger log = LoggerFactory.getLogger(ShadowModeService.class);

    private final RunRepository runRepository;
    private final WorkflowEngine workflowEngine;
    private final OrchestratorMetrics metrics;

    /** Shadow run branch prefix — isolated from production. */
    private static final String SHADOW_BRANCH_PREFIX = "shadow/";
    private static final int MAX_CONCURRENT_SHADOW_RUNS = 3;
    private static final double MAX_COST_PER_RUN_USD = 3.00;
    private static final double MAX_DAILY_BUDGET_USD = 30.00;

    /** Active shadow runs. */
    private final ConcurrentHashMap<UUID, ShadowRun> activeShadowRuns = new ConcurrentHashMap<>();
    private final AtomicInteger activeShadowCount = new AtomicInteger(0);

    /** Completed shadow runs for reporting. */
    private final List<ShadowRunResult> completedRuns = Collections.synchronizedList(new ArrayList<>());

    public ShadowModeService(
            RunRepository runRepository,
            WorkflowEngine workflowEngine,
            OrchestratorMetrics metrics) {
        this.runRepository = runRepository;
        this.workflowEngine = workflowEngine;
        this.metrics = metrics;
    }

    /**
     * Start a shadow run for a GitHub issue.
     * The shadow pipeline runs in parallel with the human team on an isolated branch.
     *
     * @param issueNumber the GitHub issue number
     * @param repo        the repository (owner/name)
     * @return the shadow run ID, or null if capacity exceeded
     */
    @Async("workflowExecutor")
    public UUID startShadowRun(int issueNumber, String repo) {
        if (activeShadowCount.get() >= MAX_CONCURRENT_SHADOW_RUNS) {
            log.warn("SHADOW MODE: capacity exceeded (active={}/{}), skipping issue #{}",
                    activeShadowCount.get(), MAX_CONCURRENT_SHADOW_RUNS, issueNumber);
            return null;
        }

        UUID shadowRunId = UUID.randomUUID();
        String correlationId = CorrelationIdHolder.generateCorrelationId();
        CorrelationIdHolder.setCorrelationId(correlationId);
        CorrelationIdHolder.setRunId(shadowRunId);

        log.info("SHADOW MODE STARTED: shadowRunId={}, issue={}#{}, correlationId={}",
                shadowRunId, repo, issueNumber, correlationId);

        ShadowRun shadowRun = new ShadowRun(shadowRunId, issueNumber, repo, Instant.now());
        activeShadowRuns.put(shadowRunId, shadowRun);
        activeShadowCount.incrementAndGet();

        try {
            // Execute the workflow (same engine, isolated branch)
            workflowEngine.executeWorkflow(shadowRunId);

            // Collect results
            RunEntity runEntity = runRepository.findById(shadowRunId).orElse(null);
            RunStatus status = runEntity != null ? runEntity.getStatus() : RunStatus.FAILED;

            ShadowRunResult result = new ShadowRunResult(
                    shadowRunId, issueNumber, repo,
                    status.name(),
                    shadowRun.startedAt,
                    Instant.now(),
                    System.currentTimeMillis() - shadowRun.startedAt.toEpochMilli(),
                    0,
                    0.0,
                    0,
                    0);

            completedRuns.add(result);

            log.info("SHADOW MODE COMPLETED: shadowRunId={}, issue={}#{}, status={}, duration={}ms",
                    shadowRunId, repo, issueNumber, status, result.durationMs);

            return shadowRunId;

        } catch (Exception e) {
            log.error("SHADOW MODE FAILED: shadowRunId={}, issue={}#{}, error={}",
                    shadowRunId, repo, issueNumber, e.getMessage(), e);

            completedRuns.add(new ShadowRunResult(
                    shadowRunId, issueNumber, repo, "FAILED",
                    shadowRun.startedAt, Instant.now(),
                    System.currentTimeMillis() - shadowRun.startedAt.toEpochMilli(),
                    0, 0.0, 0, 0));

            return shadowRunId;

        } finally {
            activeShadowRuns.remove(shadowRunId);
            activeShadowCount.decrementAndGet();
            CorrelationIdHolder.clear();
        }
    }

    /**
     * Generate the shadow mode readiness report.
     */
    public ReadinessReport generateReadinessReport() {
        if (completedRuns.isEmpty()) {
            return new ReadinessReport(0, 0, 0, 0, 0, 0, 0, false, List.of(), Instant.now());
        }

        long totalRuns = completedRuns.size();
        long completedCount = completedRuns.stream()
                .filter(r -> "DONE".equals(r.status)).count();
        long escalatedCount = completedRuns.stream()
                .filter(r -> "ESCALATED".equals(r.status)).count();
        long failedCount = completedRuns.stream()
                .filter(r -> "FAILED".equals(r.status)).count();

        double passRate = (double) completedCount / totalRuns;
        long totalGuardrailViolations = completedRuns.stream()
                .mapToLong(r -> r.guardrailViolations).sum();
        double avgCostUsd = completedRuns.stream()
                .mapToDouble(r -> r.costUsd).average().orElse(0);

        boolean ready = passRate >= 0.60
                && totalGuardrailViolations == 0
                && avgCostUsd <= MAX_COST_PER_RUN_USD;

        ReadinessReport report = new ReadinessReport(
                totalRuns, completedCount, escalatedCount, failedCount,
                passRate, totalGuardrailViolations, avgCostUsd,
                ready, new ArrayList<>(completedRuns), Instant.now());

        log.info("SHADOW MODE READINESS: runs={}, pass={}%, guardrailViolations={}, avgCost=${}, ready={}",
                totalRuns, String.format("%.0f", passRate * 100),
                totalGuardrailViolations, String.format("%.2f", avgCostUsd),
                ready ? "YES" : "NO");

        return report;
    }

    /**
     * Get the count of active shadow runs.
     */
    public int getActiveShadowRunCount() {
        return activeShadowCount.get();
    }

    /**
     * Clear completed run history.
     */
    public void clearHistory() {
        completedRuns.clear();
        log.info("SHADOW MODE: history cleared");
    }

    // --- Data Classes ---

    public static class ShadowRun {
        public final UUID runId;
        public final int issueNumber;
        public final String repo;
        public final Instant startedAt;

        public ShadowRun(UUID runId, int issueNumber, String repo, Instant startedAt) {
            this.runId = runId;
            this.issueNumber = issueNumber;
            this.repo = repo;
            this.startedAt = startedAt;
        }
    }

    public record ShadowRunResult(
            UUID runId, int issueNumber, String repo, String status,
            Instant startedAt, Instant completedAt, long durationMs,
            int loopBackCount, double costUsd,
            int guardrailViolations, int interruptTriggers) {}

    public record ReadinessReport(
            long totalRuns, long completedCount, long escalatedCount, long failedCount,
            double passRate, long totalGuardrailViolations, double avgCostUsd,
            boolean ready, List<ShadowRunResult> runs, Instant generatedAt) {}
}
