package com.atlasia.ai.service;

import com.atlasia.ai.service.observability.OrchestratorMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Behavior Diff Service — Version-to-Version Comparison.
 *
 * Compares eval suite traces between Version N and Version N+1 to detect
 * regressions. Answers: "Did the update improve relevance but hurt compliance?"
 *
 * Gates deployment if any comparison metric crosses its threshold.
 */
@Service
public class BehaviorDiffService {
    private static final Logger log = LoggerFactory.getLogger(BehaviorDiffService.class);

    private final OrchestratorMetrics metrics;
    private final ObjectMapper objectMapper;

    /** Thresholds for behavior diff comparisons. */
    private static final double PASS_AT_1_DECLINE_THRESHOLD = 0.05;
    private static final double GROUNDEDNESS_DECLINE_THRESHOLD = 0.05;
    private static final double CORRECTNESS_DECLINE_THRESHOLD = 0.05;
    private static final double PROTOCOL_DECLINE_THRESHOLD = 0.03;
    private static final double COST_INCREASE_THRESHOLD = 1.20;
    private static final double DURATION_INCREASE_THRESHOLD = 1.30;
    private static final double ESCALATION_INCREASE_THRESHOLD = 0.10;

    public BehaviorDiffService(OrchestratorMetrics metrics, ObjectMapper objectMapper) {
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /**
     * Compare two eval suite results (baseline vs candidate) and produce a diff report.
     *
     * @param baseline   eval results from the current version
     * @param candidate  eval results from the candidate version
     * @return the diff report with per-metric comparisons
     */
    public DiffReport compare(EvalSuiteService.SuiteResult baseline, EvalSuiteService.SuiteResult candidate) {
        log.info("BEHAVIOR DIFF: baseline={} scenarios, candidate={} scenarios",
                baseline.totalScenarios(), candidate.totalScenarios());

        List<MetricComparison> comparisons = new ArrayList<>();

        // Pass@1 rate comparison
        comparisons.add(compareMetric("pass_at_1_rate",
                baseline.passAt1Rate(), candidate.passAt1Rate(),
                ComparisonType.MUST_NOT_DECLINE, PASS_AT_1_DECLINE_THRESHOLD));

        // Pass rate comparison
        comparisons.add(compareMetric("pass_rate",
                baseline.passRate(), candidate.passRate(),
                ComparisonType.MUST_NOT_DECLINE, PASS_AT_1_DECLINE_THRESHOLD));

        // Per-scenario comparisons
        Map<String, EvalSuiteService.ScenarioResult> baselineByScenario = new HashMap<>();
        for (EvalSuiteService.ScenarioResult r : baseline.results()) {
            baselineByScenario.put(r.scenarioId(), r);
        }

        List<ScenarioDiff> scenarioDiffs = new ArrayList<>();
        int regressions = 0;
        int improvements = 0;

        for (EvalSuiteService.ScenarioResult candidateResult : candidate.results()) {
            EvalSuiteService.ScenarioResult baselineResult = baselineByScenario.get(candidateResult.scenarioId());
            if (baselineResult != null) {
                double scoreDelta = candidateResult.overallScore() - baselineResult.overallScore();
                boolean regressed = candidateResult.pass() == false && baselineResult.pass() == true;
                boolean improved = candidateResult.pass() == true && baselineResult.pass() == false;

                if (regressed) regressions++;
                if (improved) improvements++;

                scenarioDiffs.add(new ScenarioDiff(
                        candidateResult.scenarioId(),
                        baselineResult.overallScore(), candidateResult.overallScore(),
                        scoreDelta, baselineResult.pass(), candidateResult.pass(),
                        regressed, improved));
            }
        }

        // Aggregate metrics
        double avgBaselineGroundedness = baseline.results().stream()
                .mapToDouble(EvalSuiteService.ScenarioResult::groundednessScore).average().orElse(0);
        double avgCandidateGroundedness = candidate.results().stream()
                .mapToDouble(EvalSuiteService.ScenarioResult::groundednessScore).average().orElse(0);
        comparisons.add(compareMetric("avg_groundedness",
                avgBaselineGroundedness, avgCandidateGroundedness,
                ComparisonType.MUST_NOT_DECLINE, GROUNDEDNESS_DECLINE_THRESHOLD));

        double avgBaselineCorrectness = baseline.results().stream()
                .mapToDouble(EvalSuiteService.ScenarioResult::correctnessScore).average().orElse(0);
        double avgCandidateCorrectness = candidate.results().stream()
                .mapToDouble(EvalSuiteService.ScenarioResult::correctnessScore).average().orElse(0);
        comparisons.add(compareMetric("avg_correctness",
                avgBaselineCorrectness, avgCandidateCorrectness,
                ComparisonType.MUST_NOT_DECLINE, CORRECTNESS_DECLINE_THRESHOLD));

        double avgBaselineProtocol = baseline.results().stream()
                .mapToDouble(EvalSuiteService.ScenarioResult::protocolAdherenceScore).average().orElse(0);
        double avgCandidateProtocol = candidate.results().stream()
                .mapToDouble(EvalSuiteService.ScenarioResult::protocolAdherenceScore).average().orElse(0);
        comparisons.add(compareMetric("avg_protocol_adherence",
                avgBaselineProtocol, avgCandidateProtocol,
                ComparisonType.MUST_NOT_DECLINE, PROTOCOL_DECLINE_THRESHOLD));

        // Overall gate decision
        boolean allPass = comparisons.stream().allMatch(c -> c.pass);

        DiffReport report = new DiffReport(
                baseline.suiteRunId(), candidate.suiteRunId(),
                comparisons, scenarioDiffs,
                regressions, improvements,
                allPass, Instant.now());

        log.info("BEHAVIOR DIFF RESULT: gate={}, regressions={}, improvements={}, comparisons={}",
                allPass ? "PASS" : "FAIL", regressions, improvements,
                comparisons.stream().filter(c -> !c.pass).count() + " failures");

        return report;
    }

    private MetricComparison compareMetric(String name, double baseline, double candidate,
                                            ComparisonType type, double threshold) {
        boolean pass;
        double delta = candidate - baseline;

        pass = switch (type) {
            case MUST_NOT_DECLINE -> candidate >= baseline - threshold;
            case MUST_NOT_INCREASE -> candidate <= baseline * threshold;
        };

        return new MetricComparison(name, baseline, candidate, delta, threshold, type, pass);
    }

    // --- Data Classes ---

    public enum ComparisonType { MUST_NOT_DECLINE, MUST_NOT_INCREASE }

    public record MetricComparison(
            String name, double baseline, double candidate, double delta,
            double threshold, ComparisonType type, boolean pass) {}

    public record ScenarioDiff(
            String scenarioId, double baselineScore, double candidateScore,
            double scoreDelta, boolean baselinePass, boolean candidatePass,
            boolean regressed, boolean improved) {}

    public record DiffReport(
            UUID baselineSuiteId, UUID candidateSuiteId,
            List<MetricComparison> comparisons, List<ScenarioDiff> scenarioDiffs,
            int regressions, int improvements,
            boolean deploymentGatePass, Instant timestamp) {}
}
