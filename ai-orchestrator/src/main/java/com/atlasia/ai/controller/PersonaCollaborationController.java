package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.ParallelCollaborationRequest;
import com.atlasia.ai.api.dto.ParallelPersonaResult;
import com.atlasia.ai.service.AsyncPersonaService;
import com.atlasia.ai.service.ParallelPersonaOrchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * REST surface for parallel persona collaboration patterns.
 *
 * POST /api/collaborate/all       — fan-out to all personas, wait for every reply
 * POST /api/collaborate/race      — first persona to reply wins
 * POST /api/collaborate/collect   — best-effort: collect whatever arrives before timeout
 * POST /api/collaborate/pipeline  — two-stage: Stage 1 personas run first,
 *                                   Stage 2 uses Stage 1 output as context
 */
@RestController
@RequestMapping("/api/collaborate")
public class PersonaCollaborationController {

    private final ParallelPersonaOrchestrator orchestrator;
    private final AsyncPersonaService asyncPersonaService;

    @Value("${atlasia.orchestrator.chat.parallel-fan-out-timeout-seconds:90}")
    private long fanOutTimeoutSeconds;

    @Value("${atlasia.orchestrator.chat.persona-call-timeout-seconds:60}")
    private long personaCallTimeoutSeconds;

    public PersonaCollaborationController(
            ParallelPersonaOrchestrator orchestrator,
            AsyncPersonaService asyncPersonaService) {
        this.orchestrator = orchestrator;
        this.asyncPersonaService = asyncPersonaService;
    }

    /**
     * Calls all personas and waits for every one to complete.
     * Use when you need all perspectives before proceeding.
     */
    @PostMapping("/all")
    public ResponseEntity<ParallelPersonaResult> callAll(
            @RequestBody ParallelCollaborationRequest req) {
        if (!isValid(req)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(
                orchestrator.callAllParallel(req.userId(), req.personaIds(),
                        req.prompt(), fanOutTimeoutSeconds));
    }

    /**
     * Races all personas — returns the first reply and ignores the rest.
     * Use when any persona can answer and speed is the priority.
     */
    @PostMapping("/race")
    public ResponseEntity<Map<String, Object>> race(
            @RequestBody ParallelCollaborationRequest req) {
        if (!isValid(req)) return ResponseEntity.badRequest().build();
        Optional<String> winner = orchestrator.racePersonas(
                req.userId(), req.personaIds(), req.prompt(), personaCallTimeoutSeconds);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", winner.isPresent());
        body.put("reply", winner.orElse(null));
        return ResponseEntity.ok(body);
    }

    /**
     * Calls all personas and collects every reply that arrives before the deadline.
     * Use when partial results are acceptable (e.g., UI renders progressively).
     */
    @PostMapping("/collect")
    public ResponseEntity<ParallelPersonaResult> collect(
            @RequestBody ParallelCollaborationRequest req) {
        if (!isValid(req)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(
                orchestrator.callBestEffort(req.userId(), req.personaIds(),
                        req.prompt(), fanOutTimeoutSeconds));
    }

    /**
     * Two-stage pipeline: Stage 1 runs the first half of personaIds in parallel,
     * then Stage 2 runs the second half using Stage 1's combined output as context.
     *
     * personaIds must have an even number of entries: first half = Stage 1, second half = Stage 2.
     * If odd, the last persona is always in Stage 2.
     *
     * Example: personaIds=[architect, backend-developer, frontend-designer, qa-engineer]
     * Stage 1: architect + backend-developer
     * Stage 2: frontend-designer + qa-engineer (with Stage 1 output prepended to prompt)
     */
    @PostMapping("/pipeline")
    public ResponseEntity<Map<String, Object>> pipeline(
            @RequestBody ParallelCollaborationRequest req) {
        if (!isValid(req) || req.personaIds().size() < 2) return ResponseEntity.badRequest().build();

        int mid = req.personaIds().size() / 2;
        List<String> stage1Ids = req.personaIds().subList(0, mid);
        List<String> stage2Ids = req.personaIds().subList(mid, req.personaIds().size());

        // Stage 1
        ParallelPersonaResult stage1 = orchestrator.callBestEffort(
                req.userId(), stage1Ids, req.prompt(), fanOutTimeoutSeconds);

        // Build stage 2 prompt with stage 1 context
        var stage2Prompt = buildStage2Prompt(req.prompt(), stage1);

        // Stage 2 (only if stage 1 had at least one success)
        ParallelPersonaResult stage2 = stage1.hasAnySuccess()
                ? orchestrator.callBestEffort(req.userId(), stage2Ids, stage2Prompt, fanOutTimeoutSeconds)
                : new ParallelPersonaResult(Map.of(), Map.of("stage2", "skipped — stage 1 had no successes"), 0L);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stage1", stage1);
        body.put("stage2", stage2);
        body.put("totalElapsedMs", stage1.elapsedMs() + stage2.elapsedMs());
        return ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------------

    private String buildStage2Prompt(String originalPrompt, ParallelPersonaResult stage1) {
        if (stage1.successes().isEmpty()) return originalPrompt;
        var sb = new StringBuilder(originalPrompt);
        sb.append("\n\n## Context from Stage 1\n");
        stage1.successes().forEach((personaId, reply) ->
            sb.append("\n### ").append(personaId).append("\n").append(reply).append("\n")
        );
        return sb.toString();
    }

    private boolean isValid(ParallelCollaborationRequest req) {
        return req.userId() != null && !req.userId().isBlank()
                && req.personaIds() != null && !req.personaIds().isEmpty()
                && req.prompt() != null && !req.prompt().isBlank();
    }
}
