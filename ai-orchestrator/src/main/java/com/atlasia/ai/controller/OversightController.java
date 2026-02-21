package com.atlasia.ai.controller;

import com.atlasia.ai.service.DynamicInterruptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oversight Controller — Configuration and interrupt decision management.
 *
 * Provides REST endpoints for:
 *   - Reading/updating oversight configuration (interrupt rules, autonomy level)
 *   - Approving/denying pending interrupt decisions
 */
@RestController
@RequestMapping("/api/oversight")
public class OversightController {
    private static final Logger log = LoggerFactory.getLogger(OversightController.class);

    private final DynamicInterruptService interruptService;

    /** In-memory config store (persisted per-session; externalize to DB for production). */
    private volatile OversightConfig currentConfig = OversightConfig.defaults();

    /** Pending interrupt decisions awaiting human approval. */
    private final ConcurrentHashMap<UUID, PendingInterrupt> pendingInterrupts = new ConcurrentHashMap<>();

    public OversightController(DynamicInterruptService interruptService) {
        this.interruptService = interruptService;
    }

    /**
     * Get the current oversight configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<OversightConfig> getConfig() {
        return ResponseEntity.ok(currentConfig);
    }

    /**
     * Update the oversight configuration.
     */
    @PostMapping("/config")
    public ResponseEntity<OversightConfig> updateConfig(@RequestBody OversightConfig config) {
        log.info("Oversight config updated: autonomyLevel={}, interruptRulesCount={}",
                config.autonomyLevel, config.interruptRules != null ? config.interruptRules.size() : 0);
        this.currentConfig = config;
        return ResponseEntity.ok(currentConfig);
    }

    /**
     * Get all pending interrupt decisions.
     */
    @GetMapping("/interrupts/pending")
    public ResponseEntity<Collection<PendingInterrupt>> getPendingInterrupts() {
        return ResponseEntity.ok(pendingInterrupts.values());
    }

    /**
     * Approve or deny a pending interrupt decision.
     */
    @PostMapping("/runs/{runId}/interrupt-decision")
    public ResponseEntity<Map<String, Object>> resolveInterrupt(
            @PathVariable UUID runId,
            @RequestBody InterruptDecisionRequest request) {

        PendingInterrupt pending = pendingInterrupts.remove(runId);
        if (pending == null) {
            return ResponseEntity.notFound().build();
        }

        if ("approve".equalsIgnoreCase(request.decision)) {
            interruptService.recordApproval(pending.ruleName);
            log.info("Interrupt APPROVED: runId={}, rule={}, by={}",
                    runId, pending.ruleName, request.decidedBy);
        } else {
            interruptService.recordDenial(pending.ruleName);
            log.info("Interrupt DENIED: runId={}, rule={}, by={}",
                    runId, pending.ruleName, request.decidedBy);
        }

        return ResponseEntity.ok(Map.of(
                "runId", runId,
                "decision", request.decision,
                "rule", pending.ruleName,
                "resolvedAt", java.time.Instant.now().toString()));
    }

    /**
     * Register a pending interrupt (called internally by the interrupt service pipeline).
     */
    public void registerPendingInterrupt(UUID runId, String agentName, String ruleName, String tier, String message) {
        pendingInterrupts.put(runId, new PendingInterrupt(
                runId, agentName, ruleName, tier, message, java.time.Instant.now()));
    }

    // --- Data Classes ---

    public static class OversightConfig {
        public String autonomyLevel = "supervised";
        public List<InterruptRule> interruptRules = new ArrayList<>();
        public boolean autoApproveMedianTier = true;
        public int maxConcurrentRuns = 5;

        public static OversightConfig defaults() {
            OversightConfig config = new OversightConfig();
            config.autonomyLevel = "supervised";
            config.interruptRules = List.of(
                    new InterruptRule("destructive_git_operations", "critical", true),
                    new InterruptRule("secret_exposure", "critical", true),
                    new InterruptRule("file_writes_outside_scope", "critical", true),
                    new InterruptRule("database_schema_mutations", "critical", true),
                    new InterruptRule("large_code_changes", "high", true),
                    new InterruptRule("dependency_introduction", "high", true),
                    new InterruptRule("pr_creation", "medium", false));
            return config;
        }
    }

    public record InterruptRule(String ruleName, String tier, boolean enabled) {}
    public record InterruptDecisionRequest(String decision, String decidedBy, String reason) {}
    public record PendingInterrupt(UUID runId, String agentName, String ruleName,
                                   String tier, String message, java.time.Instant createdAt) {}
}
