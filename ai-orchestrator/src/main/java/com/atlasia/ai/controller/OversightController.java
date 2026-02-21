package com.atlasia.ai.controller;

import com.atlasia.ai.model.GlobalSettingsEntity;
import com.atlasia.ai.persistence.GlobalSettingsRepository;
import com.atlasia.ai.service.DynamicInterruptService;
import com.atlasia.ai.service.InterruptDecisionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Oversight Controller — Configuration and interrupt decision management.
 *
 * Provides REST endpoints for:
 *   - Reading/updating oversight configuration (persisted to ai_global_settings)
 *   - Approving/denying pending interrupt decisions (unblocks WorkflowEngine futures)
 */
@RestController
@RequestMapping("/api/oversight")
public class OversightController {
    private static final Logger log = LoggerFactory.getLogger(OversightController.class);
    private static final String CONFIG_KEY = "oversight_config";

    private final DynamicInterruptService  interruptService;
    private final InterruptDecisionStore   interruptDecisionStore;
    private final GlobalSettingsRepository settingsRepository;
    private final ObjectMapper             objectMapper;

    /** In-memory cache of the current config (loaded from DB at startup). */
    private volatile OversightConfig currentConfig = OversightConfig.defaults();

    public OversightController(DynamicInterruptService interruptService,
                               InterruptDecisionStore interruptDecisionStore,
                               GlobalSettingsRepository settingsRepository,
                               ObjectMapper objectMapper) {
        this.interruptService       = interruptService;
        this.interruptDecisionStore = interruptDecisionStore;
        this.settingsRepository     = settingsRepository;
        this.objectMapper           = objectMapper;
    }

    @PostConstruct
    public void loadConfig() {
        try {
            settingsRepository.findById(CONFIG_KEY).ifPresent(row -> {
                try {
                    currentConfig = objectMapper.readValue(row.getValueJson(), OversightConfig.class);
                    log.info("Oversight config loaded from DB: autonomyLevel={}", currentConfig.autonomyLevel);
                } catch (Exception e) {
                    log.warn("Failed to deserialise oversight config from DB; using defaults", e);
                }
            });
        } catch (Exception e) {
            // Table may not exist in test environments without Flyway migrations
            log.warn("Could not load oversight config from DB; using defaults: {}", e.getMessage());
        }
    }

    // ── Config endpoints ─────────────────────────────────────────────────────

    @GetMapping("/config")
    public ResponseEntity<OversightConfig> getConfig() {
        return ResponseEntity.ok(currentConfig);
    }

    @PostMapping("/config")
    public ResponseEntity<OversightConfig> updateConfig(@RequestBody OversightConfig config) {
        log.info("Oversight config updated: autonomyLevel={}, rules={}",
                config.autonomyLevel,
                config.interruptRules != null ? config.interruptRules.size() : 0);
        this.currentConfig = config;
        persistConfig(config);
        return ResponseEntity.ok(currentConfig);
    }

    // ── Interrupt endpoints ──────────────────────────────────────────────────

    /** All pending interrupt decisions awaiting human approval. */
    @GetMapping("/interrupts/pending")
    public ResponseEntity<Collection<InterruptDecisionStore.PendingApproval>> getPendingInterrupts() {
        return ResponseEntity.ok(interruptDecisionStore.getPending());
    }

    /**
     * Approve or deny a pending interrupt decision.
     * Completes the CompletableFuture parked in WorkflowEngine, unblocking the pipeline thread.
     */
    @PostMapping("/runs/{runId}/interrupt-decision")
    public ResponseEntity<Map<String, Object>> resolveInterrupt(
            @PathVariable UUID runId,
            @RequestBody InterruptDecisionRequest request) {

        boolean resolved = interruptDecisionStore.complete(runId, request.decision);
        if (!resolved) {
            return ResponseEntity.notFound().build();
        }

        if ("approve".equalsIgnoreCase(request.decision)) {
            interruptService.recordApproval(request.decision);
            log.info("Interrupt APPROVED: runId={}, by={}", runId, request.decidedBy);
        } else {
            interruptService.recordDenial(request.decision);
            log.info("Interrupt DENIED: runId={}, by={}", runId, request.decidedBy);
        }

        return ResponseEntity.ok(Map.of(
                "runId",      runId,
                "decision",   request.decision,
                "resolvedAt", java.time.Instant.now().toString()));
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void persistConfig(OversightConfig config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            GlobalSettingsEntity row = settingsRepository.findById(CONFIG_KEY)
                    .orElse(new GlobalSettingsEntity(CONFIG_KEY, "{}"));
            row.setValueJson(json);
            settingsRepository.save(row);
        } catch (Exception e) {
            log.error("Failed to persist oversight config", e);
        }
    }

    // ── Data classes ─────────────────────────────────────────────────────────

    public static class OversightConfig {
        public String autonomyLevel = "supervised";
        public List<InterruptRule> interruptRules = new ArrayList<>();
        public boolean autoApproveMedianTier = true;
        public int maxConcurrentRuns = 5;

        public static OversightConfig defaults() {
            OversightConfig cfg = new OversightConfig();
            cfg.autonomyLevel = "supervised";
            cfg.interruptRules = List.of(
                    new InterruptRule("destructive_git_operations", "critical", true),
                    new InterruptRule("secret_exposure",            "critical", true),
                    new InterruptRule("file_writes_outside_scope",  "critical", true),
                    new InterruptRule("database_schema_mutations",  "critical", true),
                    new InterruptRule("large_code_changes",         "high",     true),
                    new InterruptRule("dependency_introduction",    "high",     true),
                    new InterruptRule("pr_creation",                "medium",   false));
            return cfg;
        }
    }

    public record InterruptRule(String ruleName, String tier, boolean enabled) {}
    public record InterruptDecisionRequest(String decision, String decidedBy, String reason) {}
}
