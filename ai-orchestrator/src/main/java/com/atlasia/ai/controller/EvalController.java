package com.atlasia.ai.controller;

import com.atlasia.ai.service.BehaviorDiffService;
import com.atlasia.ai.service.EvalSuiteService;
import com.atlasia.ai.service.ShadowModeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/eval")
@Validated
public class EvalController {
    private static final Logger log = LoggerFactory.getLogger(EvalController.class);

    private final EvalSuiteService evalSuiteService;
    private final BehaviorDiffService behaviorDiffService;
    private final ShadowModeService shadowModeService;
    private final ObjectMapper objectMapper;

    /** Cache of completed suite results for diff comparisons. */
    private final ConcurrentHashMap<UUID, EvalSuiteService.SuiteResult> suiteResults = new ConcurrentHashMap<>();

    public EvalController(EvalSuiteService evalSuiteService,
                          BehaviorDiffService behaviorDiffService,
                          ShadowModeService shadowModeService,
                          ObjectMapper objectMapper) {
        this.evalSuiteService = evalSuiteService;
        this.behaviorDiffService = behaviorDiffService;
        this.shadowModeService = shadowModeService;
        this.objectMapper = objectMapper;
    }

    /**
     * Trigger a full eval suite run with all scenarios loaded from the JSON dataset.
     */
    @PostMapping("/run")
    public ResponseEntity<EvalSuiteService.SuiteResult> runEvalSuite() {
        try {
            List<EvalSuiteService.EvalScenario> scenarios = loadScenarios();
            log.info("Starting eval suite run with {} scenarios", scenarios.size());

            EvalSuiteService.SuiteResult result = evalSuiteService.executeSuite(scenarios);
            suiteResults.put(result.suiteRunId(), result);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to run eval suite: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get results of a completed eval suite run.
     */
    @GetMapping("/results/{suiteRunId}")
    public ResponseEntity<EvalSuiteService.SuiteResult> getResults(@PathVariable UUID suiteRunId) {
        EvalSuiteService.SuiteResult result = suiteResults.get(suiteRunId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Get progress of an active eval suite run.
     */
    @GetMapping("/progress/{suiteRunId}")
    public ResponseEntity<EvalSuiteService.SuiteRun> getProgress(@PathVariable UUID suiteRunId) {
        EvalSuiteService.SuiteRun progress = evalSuiteService.getProgress(suiteRunId);
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(progress);
    }

    /**
     * Compare two suite runs to detect behavioral regressions.
     */
    @PostMapping("/diff")
    public ResponseEntity<BehaviorDiffService.DiffReport> compareSuiteRuns(
            @RequestBody DiffRequest request) {
        EvalSuiteService.SuiteResult baseline = suiteResults.get(request.baselineSuiteRunId);
        EvalSuiteService.SuiteResult candidate = suiteResults.get(request.candidateSuiteRunId);

        if (baseline == null || candidate == null) {
            return ResponseEntity.badRequest().build();
        }

        BehaviorDiffService.DiffReport report = behaviorDiffService.compare(baseline, candidate);
        return ResponseEntity.ok(report);
    }

    /**
     * Start a shadow mode run for a GitHub issue.
     */
    @PostMapping("/shadow")
    public ResponseEntity<Map<String, Object>> startShadowRun(@RequestBody ShadowRunRequest request) {
        UUID shadowRunId = shadowModeService.startShadowRun(request.issueNumber, request.repo);

        if (shadowRunId == null) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "Shadow mode capacity exceeded",
                    "activeRuns", shadowModeService.getActiveShadowRunCount()));
        }

        return ResponseEntity.accepted().body(Map.of(
                "shadowRunId", shadowRunId,
                "status", "started"));
    }

    /**
     * Get shadow mode readiness report.
     */
    @GetMapping("/shadow/readiness")
    public ResponseEntity<ShadowModeService.ReadinessReport> getReadinessReport() {
        return ResponseEntity.ok(shadowModeService.generateReadinessReport());
    }

    private List<EvalSuiteService.EvalScenario> loadScenarios() throws Exception {
        ClassPathResource resource = new ClassPathResource("ai/eval/scenarios.json");
        try (InputStream is = resource.getInputStream()) {
            List<Map<String, Object>> rawScenarios = objectMapper.readValue(is,
                    new TypeReference<List<Map<String, Object>>>() {});

            List<EvalSuiteService.EvalScenario> scenarios = new ArrayList<>();
            for (Map<String, Object> raw : rawScenarios) {
                @SuppressWarnings("unchecked")
                List<String> gradingMetricsList = (List<String>) raw.get("gradingMetrics");
                Set<String> gradingMetrics = gradingMetricsList != null
                        ? new LinkedHashSet<>(gradingMetricsList) : Set.of();

                scenarios.add(new EvalSuiteService.EvalScenario(
                        (String) raw.get("id"),
                        (String) raw.get("category"),
                        (String) raw.get("difficulty"),
                        (String) raw.get("title"),
                        (String) raw.get("description"),
                        (String) raw.get("expectedOutcome"),
                        raw.get("expectedMaxLoops") != null ? ((Number) raw.get("expectedMaxLoops")).intValue() : null,
                        gradingMetrics));
            }
            return scenarios;
        }
    }

    public record DiffRequest(UUID baselineSuiteRunId, UUID candidateSuiteRunId) {}
    public record ShadowRunRequest(int issueNumber, String repo) {}
}
