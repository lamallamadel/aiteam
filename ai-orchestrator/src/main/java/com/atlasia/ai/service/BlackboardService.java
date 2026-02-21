package com.atlasia.ai.service;

import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.RunArtifactRepository;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.event.WorkflowEventBus;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blackboard Service — Shared Memory for the Multi-Agent Pipeline.
 *
 * Implements the Blackboard pattern: a versioned, access-controlled shared
 * memory store where agents post structured artifacts and read updates from
 * other agents asynchronously. The Orchestrator manages access control,
 * schema validation, and versioning.
 *
 * Also implements the Magentic Progress Ledger: a persistent plan of
 * goals/subgoals enabling agents to pause, hand off, and resume without
 * losing the "big picture."
 *
 * Agents NEVER communicate directly — all inter-agent state flows through
 * the Blackboard.
 */
@Service
public class BlackboardService {
    private static final Logger log = LoggerFactory.getLogger(BlackboardService.class);

    private final RunRepository runRepository;
    private final RunArtifactRepository artifactRepository;
    private final JsonSchemaValidator schemaValidator;
    private final OrchestratorMetrics metrics;
    private final WorkflowEventBus eventBus;

    /**
     * Access control matrix: entry_key → authorized producers.
     */
    private static final Map<String, String> ENTRY_PRODUCERS = Map.ofEntries(
            Map.entry("task_ledger", "orchestrator"),
            Map.entry("progress_ledger", "orchestrator"),
            Map.entry("ticket_plan", "PM"),
            Map.entry("work_plan", "QUALIFIER"),
            Map.entry("architecture_notes", "ARCHITECT"),
            Map.entry("implementation_report", "DEVELOPER"),
            Map.entry("pr_url", "DEVELOPER"),
            Map.entry("persona_review", "REVIEW"),
            Map.entry("conflict_resolution", "REVIEW"),
            Map.entry("test_report", "TESTER"),
            Map.entry("judge_verdict", "JUDGE"),
            Map.entry("quality_report", "orchestrator"),
            Map.entry("docs_patch", "WRITER")
    );

    /**
     * Access control matrix: entry_key → authorized consumers.
     */
    private static final Map<String, Set<String>> ENTRY_CONSUMERS = Map.ofEntries(
            Map.entry("task_ledger", Set.of("PM", "QUALIFIER", "ARCHITECT", "DEVELOPER", "REVIEW", "TESTER", "WRITER", "JUDGE")),
            Map.entry("progress_ledger", Set.of("PM", "QUALIFIER", "ARCHITECT", "DEVELOPER", "REVIEW", "TESTER", "WRITER", "JUDGE")),
            Map.entry("ticket_plan", Set.of("QUALIFIER")),
            Map.entry("work_plan", Set.of("ARCHITECT", "DEVELOPER")),
            Map.entry("architecture_notes", Set.of("DEVELOPER")),
            Map.entry("implementation_report", Set.of("REVIEW", "WRITER")),
            Map.entry("pr_url", Set.of("REVIEW", "TESTER", "WRITER")),
            Map.entry("persona_review", Set.of("TESTER", "DEVELOPER", "JUDGE")),
            Map.entry("conflict_resolution", Set.of("orchestrator", "JUDGE")),
            Map.entry("test_report", Set.of("WRITER", "DEVELOPER", "JUDGE")),
            Map.entry("judge_verdict", Set.of("orchestrator", "REVIEW")),
            Map.entry("escalation", Set.of("orchestrator")),
            Map.entry("quality_report", Set.of()),
            Map.entry("docs_patch", Set.of())
    );

    /**
     * Schema references for entries that require validation.
     */
    private static final Map<String, String> ENTRY_SCHEMAS = Map.of(
            "task_ledger", "task_ledger.schema.json",
            "progress_ledger", "progress_ledger.schema.json",
            "ticket_plan", "ticket_plan.schema.json",
            "work_plan", "work_plan.schema.json",
            "persona_review", "persona_review.schema.json",
            "test_report", "test_report.schema.json",
            "judge_verdict", "judge_verdict.schema.json",
            "quality_report", "quality_report.schema.json",
            "conflict_resolution", "conflict_resolution.schema.json"
    );

    /**
     * In-memory version tracker for fast version lookups.
     * Key: runId:entryKey → current version number.
     */
    private final ConcurrentHashMap<String, Integer> versionTracker = new ConcurrentHashMap<>();

    public BlackboardService(
            RunRepository runRepository,
            RunArtifactRepository artifactRepository,
            JsonSchemaValidator schemaValidator,
            OrchestratorMetrics metrics,
            WorkflowEventBus eventBus) {
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.schemaValidator = schemaValidator;
        this.metrics = metrics;
        this.eventBus = eventBus;
    }

    /**
     * Write an artifact to the blackboard with access control and schema validation.
     *
     * @param runEntity   the workflow run
     * @param entryKey    the blackboard entry key (e.g., "ticket_plan")
     * @param agentName   the agent producing the entry
     * @param payload     the artifact content
     * @throws BlackboardAccessException if the agent is not authorized to produce this entry
     */
    public void write(RunEntity runEntity, String entryKey, String agentName, String payload) {
        UUID runId = runEntity.getId();
        String correlationId = CorrelationIdHolder.getCorrelationId();

        // Access control: verify producer authorization
        String authorizedProducer = ENTRY_PRODUCERS.get(entryKey);
        if (authorizedProducer != null && !authorizedProducer.equals(agentName)
                && !"escalation".equals(entryKey)) {
            log.warn("BLACKBOARD ACCESS DENIED: agent={} attempted to write entry={}, authorized_producer={}, runId={}, correlationId={}",
                    agentName, entryKey, authorizedProducer, runId, correlationId);
            metrics.recordGuardrailViolation("blackboard_write_denied", agentName);
            throw new BlackboardAccessException(
                    "Agent '" + agentName + "' is not authorized to write '" + entryKey + "'. Authorized producer: " + authorizedProducer);
        }

        // Schema validation (if schema exists for this entry)
        String schemaFile = ENTRY_SCHEMAS.get(entryKey);
        if (schemaFile != null) {
            try {
                schemaValidator.validate(payload, schemaFile);
            } catch (Exception e) {
                log.error("BLACKBOARD SCHEMA VIOLATION: entry={}, schema={}, agent={}, runId={}, correlationId={}",
                        entryKey, schemaFile, agentName, runId, correlationId, e);
                metrics.recordGuardrailViolation("blackboard_schema_violation", agentName);
                throw new BlackboardAccessException(
                        "Schema validation failed for entry '" + entryKey + "': " + e.getMessage());
            }
        }

        // Version tracking
        String versionKey = runId + ":" + entryKey;
        int version = versionTracker.merge(versionKey, 1, Integer::sum);

        // Persist to database
        RunArtifactEntity artifact = new RunArtifactEntity(agentName, entryKey, payload, Instant.now());
        runEntity.addArtifact(artifact);
        runRepository.save(runEntity);

        metrics.recordBlackboardWrite(entryKey, agentName);

        log.info("BLACKBOARD WRITE: run_id={}, agent={}, entry={}, version={}, correlation_id={}",
                runId, agentName, entryKey, version, correlationId);
    }

    /**
     * Read the latest version of an artifact from the blackboard with access control.
     *
     * @param runEntity  the workflow run
     * @param entryKey   the blackboard entry key
     * @param agentName  the agent requesting the read
     * @return the artifact payload, or null if not found
     * @throws BlackboardAccessException if the agent is not authorized to read this entry
     */
    public String read(RunEntity runEntity, String entryKey, String agentName) {
        UUID runId = runEntity.getId();
        String correlationId = CorrelationIdHolder.getCorrelationId();

        // Access control: verify consumer authorization
        Set<String> authorizedConsumers = ENTRY_CONSUMERS.get(entryKey);
        if (authorizedConsumers != null && !authorizedConsumers.contains(agentName)
                && !"orchestrator".equals(agentName)) {
            log.warn("BLACKBOARD ACCESS DENIED: agent={} attempted to read entry={}, runId={}, correlationId={}",
                    agentName, entryKey, runId, correlationId);
            metrics.recordGuardrailViolation("blackboard_read_denied", agentName);
            throw new BlackboardAccessException(
                    "Agent '" + agentName + "' is not authorized to read '" + entryKey + "'.");
        }

        // Read latest version from artifacts
        String payload = runEntity.getArtifacts().stream()
                .filter(a -> entryKey.equals(a.getArtifactType()))
                .reduce((first, second) -> second)
                .map(RunArtifactEntity::getPayload)
                .orElse(null);

        metrics.recordBlackboardRead(entryKey, agentName);

        log.debug("BLACKBOARD READ: run_id={}, agent={}, entry={}, found={}, correlation_id={}",
                runId, agentName, entryKey, payload != null, correlationId);

        return payload;
    }

    /**
     * Read a specific version of an artifact from the blackboard.
     *
     * @param runEntity  the workflow run
     * @param entryKey   the blackboard entry key
     * @param agentName  the agent requesting the read
     * @param version    the version to retrieve (1-indexed)
     * @return the artifact payload, or null if not found
     */
    public String readVersion(RunEntity runEntity, String entryKey, String agentName, int version) {
        // Access control same as read()
        Set<String> authorizedConsumers = ENTRY_CONSUMERS.get(entryKey);
        if (authorizedConsumers != null && !authorizedConsumers.contains(agentName)
                && !"orchestrator".equals(agentName)) {
            throw new BlackboardAccessException(
                    "Agent '" + agentName + "' is not authorized to read '" + entryKey + "'.");
        }

        List<RunArtifactEntity> versions = runEntity.getArtifacts().stream()
                .filter(a -> entryKey.equals(a.getArtifactType()))
                .toList();

        if (version < 1 || version > versions.size()) {
            return null;
        }
        return versions.get(version - 1).getPayload();
    }

    /**
     * Get the current version number for an entry.
     */
    public int getCurrentVersion(UUID runId, String entryKey) {
        return versionTracker.getOrDefault(runId + ":" + entryKey, 0);
    }

    /**
     * List all entry keys that have been written for a run.
     */
    public Set<String> listEntries(RunEntity runEntity) {
        Set<String> keys = new LinkedHashSet<>();
        for (RunArtifactEntity artifact : runEntity.getArtifacts()) {
            keys.add(artifact.getArtifactType());
        }
        return keys;
    }

    /**
     * Clean up version tracker when a workflow completes.
     */
    public void cleanup(UUID runId) {
        versionTracker.keySet().removeIf(key -> key.startsWith(runId.toString()));
        log.debug("BLACKBOARD CLEANUP: run_id={}", runId);
    }

    /**
     * Exception thrown when blackboard access control is violated.
     */
    public static class BlackboardAccessException extends RuntimeException {
        public BlackboardAccessException(String message) {
            super(message);
        }
    }
}
