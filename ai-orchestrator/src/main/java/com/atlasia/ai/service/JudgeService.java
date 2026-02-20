package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Judge Service — LLM-as-a-Judge Quality Arbiter.
 *
 * An independent quality evaluation agent with VETO POWER over the pipeline.
 * Evaluates artifacts against structured rubrics and produces binding verdicts.
 *
 * Implements:
 *   - Single-agent evaluation (standard Judge pattern)
 *   - Majority Voting (3 independent evaluations for high-stakes decisions)
 *   - Confidence-Weighted Synthesis (for conflict arbitration)
 *   - Pass@1 tracking (percentage of tasks passing on first attempt)
 */
@Service
public class JudgeService {
    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private final LlmService llmService;
    private final BlackboardService blackboardService;
    private final JsonSchemaValidator schemaValidator;
    private final OrchestratorMetrics metrics;
    private final ObjectMapper objectMapper;

    /** Rubric criteria weights for code quality evaluation. */
    private static final Map<String, Double> CODE_QUALITY_WEIGHTS = Map.of(
            "correctness", 0.30,
            "security", 0.25,
            "maintainability", 0.20,
            "performance", 0.15,
            "test_coverage", 0.10
    );

    /** Score mapping: level name → numeric score. */
    private static final Map<String, Double> LEVEL_SCORES = Map.of(
            "excellent", 1.0,
            "good", 0.7,
            "acceptable", 0.4,
            "failing", 0.0
    );

    /** Default thresholds. */
    private static final double PASSING_THRESHOLD = 0.65;
    private static final double VETO_THRESHOLD = 0.40;
    private static final int DEFAULT_VOTER_COUNT = 3;
    private static final int VOTING_QUORUM = 2;

    public JudgeService(
            LlmService llmService,
            BlackboardService blackboardService,
            JsonSchemaValidator schemaValidator,
            OrchestratorMetrics metrics,
            ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.blackboardService = blackboardService;
        this.schemaValidator = schemaValidator;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluate an artifact at a specific checkpoint using the appropriate rubric.
     *
     * @param runEntity   the workflow run
     * @param checkpoint  the evaluation checkpoint (e.g., "post_developer", "pre_merge")
     * @param artifactKey the blackboard entry key of the artifact to evaluate
     * @return the verdict as a structured JSON string
     */
    public JudgeVerdict evaluate(RunEntity runEntity, String checkpoint, String artifactKey) {
        UUID runId = runEntity.getId();
        String correlationId = CorrelationIdHolder.getCorrelationId();
        long startTime = System.currentTimeMillis();

        log.info("Judge evaluation started: checkpoint={}, artifact={}, runId={}, correlationId={}",
                checkpoint, artifactKey, runId, correlationId);

        try {
            // Read the artifact from the blackboard
            String artifact = blackboardService.read(runEntity, artifactKey, "JUDGE");
            if (artifact == null) {
                log.warn("Judge: artifact not found on blackboard: key={}, runId={}", artifactKey, runId);
                return JudgeVerdict.error(runId, checkpoint, artifactKey, "Artifact not found");
            }

            // Build the evaluation prompt
            String evaluationPrompt = buildEvaluationPrompt(checkpoint, artifact);

            // Call LLM for evaluation
            String llmResponse = llmService.generateCompletion(null, evaluationPrompt);

            // Parse the LLM response into criterion scores
            Map<String, CriterionScore> criterionScores = parseCriterionScores(llmResponse);

            // Compute weighted overall score
            double overallScore = computeWeightedScore(criterionScores);

            // Determine verdict
            String verdict;
            if (overallScore < VETO_THRESHOLD) {
                verdict = "veto";
            } else if (overallScore < PASSING_THRESHOLD) {
                verdict = "conditional_pass";
            } else {
                verdict = "pass";
            }

            // Estimate confidence based on criterion score variance
            double confidence = computeConfidence(criterionScores);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Judge evaluation completed: checkpoint={}, verdict={}, score={}, confidence={}, duration={}ms, runId={}",
                    checkpoint, verdict, String.format("%.2f", overallScore),
                    String.format("%.2f", confidence), duration, runId);

            JudgeVerdict result = new JudgeVerdict(
                    runId, checkpoint, artifactKey, "code_quality",
                    overallScore, verdict, confidence, criterionScores,
                    List.of(), determineRecommendation(verdict, overallScore),
                    null, Instant.now());

            // Persist verdict to blackboard
            String verdictJson = serializeVerdict(result);
            blackboardService.write(runEntity, "judge_verdict", "JUDGE", verdictJson);

            return result;

        } catch (Exception e) {
            log.error("Judge evaluation failed: checkpoint={}, runId={}, correlationId={}",
                    checkpoint, runId, correlationId, e);
            return JudgeVerdict.error(runId, checkpoint, artifactKey, e.getMessage());
        }
    }

    /**
     * Execute majority voting: run N independent evaluations and aggregate results.
     * Used for high-stakes decisions (pre_merge, security_verdict).
     *
     * @param runEntity   the workflow run
     * @param checkpoint  the evaluation checkpoint
     * @param artifactKey the artifact to evaluate
     * @return the aggregated verdict with voting metadata
     */
    public JudgeVerdict evaluateWithMajorityVoting(RunEntity runEntity, String checkpoint, String artifactKey) {
        UUID runId = runEntity.getId();
        log.info("Majority voting started: checkpoint={}, voters={}, runId={}",
                checkpoint, DEFAULT_VOTER_COUNT, runId);

        List<JudgeVerdict> voterVerdicts = new ArrayList<>();
        double[] temperatures = {0.2, 0.5, 0.8};
        String[] emphases = {
                "Focus on correctness and security",
                "Focus on maintainability and test coverage",
                "Focus on performance and scalability"
        };

        // Run voters (sequentially for now; parallel execution can be added)
        for (int i = 0; i < DEFAULT_VOTER_COUNT; i++) {
            try {
                String artifact = blackboardService.read(runEntity, artifactKey, "JUDGE");
                if (artifact == null) {
                    log.warn("Majority voting: artifact not found, voter {} skipped", i);
                    continue;
                }

                String prompt = buildVotingPrompt(checkpoint, artifact, emphases[i]);
                String response = llmService.generateCompletion(null, prompt);
                Map<String, CriterionScore> scores = parseCriterionScores(response);
                double overallScore = computeWeightedScore(scores);
                double confidence = computeConfidence(scores);

                String verdict;
                if (overallScore < VETO_THRESHOLD) verdict = "veto";
                else if (overallScore < PASSING_THRESHOLD) verdict = "conditional_pass";
                else verdict = "pass";

                voterVerdicts.add(new JudgeVerdict(
                        runId, checkpoint, artifactKey, "code_quality",
                        overallScore, verdict, confidence, scores,
                        List.of(), determineRecommendation(verdict, overallScore),
                        null, Instant.now()));

            } catch (Exception e) {
                log.warn("Voter {} failed: checkpoint={}, runId={}", i, checkpoint, runId, e);
            }
        }

        // Check quorum
        if (voterVerdicts.size() < VOTING_QUORUM) {
            log.warn("Majority voting quorum not met: needed={}, got={}, runId={}",
                    VOTING_QUORUM, voterVerdicts.size(), runId);
            return JudgeVerdict.error(runId, checkpoint, artifactKey,
                    "Voting quorum not met: " + voterVerdicts.size() + "/" + VOTING_QUORUM);
        }

        // Aggregate: confidence-weighted majority
        return aggregateVotes(runId, checkpoint, artifactKey, voterVerdicts);
    }

    /**
     * Arbitrate a conflict between review personas using confidence-weighted synthesis.
     */
    public JudgeVerdict arbitrateConflict(RunEntity runEntity, String position1, String position2,
                                            String evidence1, String evidence2) {
        UUID runId = runEntity.getId();
        log.info("Conflict arbitration started: runId={}", runId);

        try {
            String prompt = buildArbitrationPrompt(position1, position2, evidence1, evidence2);
            String response = llmService.generateCompletion(null, prompt);

            Map<String, CriterionScore> scores = parseCriterionScores(response);
            double overallScore = computeWeightedScore(scores);
            double confidence = computeConfidence(scores);

            String verdict = overallScore >= PASSING_THRESHOLD ? "pass" : "conditional_pass";

            JudgeVerdict result = new JudgeVerdict(
                    runId, "conflict_arbitration", "conflict_resolution", "code_quality",
                    overallScore, verdict, confidence, scores,
                    List.of(), "Conflict resolved via confidence-weighted synthesis",
                    null, Instant.now());

            String verdictJson = serializeVerdict(result);
            blackboardService.write(runEntity, "judge_verdict", "JUDGE", verdictJson);

            metrics.recordConflictResolution("judge_arbitration");
            return result;

        } catch (Exception e) {
            log.error("Conflict arbitration failed: runId={}", runId, e);
            return JudgeVerdict.error(runId, "conflict_arbitration", "conflict_resolution", e.getMessage());
        }
    }

    // --- Aggregation ---

    private JudgeVerdict aggregateVotes(UUID runId, String checkpoint, String artifactKey,
                                          List<JudgeVerdict> votes) {
        // Confidence-weighted vote aggregation
        Map<String, Double> verdictWeights = new HashMap<>();
        verdictWeights.put("pass", 0.0);
        verdictWeights.put("conditional_pass", 0.0);
        verdictWeights.put("veto", 0.0);

        for (JudgeVerdict vote : votes) {
            verdictWeights.merge(vote.verdict, vote.confidence, Double::sum);
        }

        // Select verdict with highest confidence weight
        String aggregatedVerdict = verdictWeights.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("conditional_pass");

        // Tie-breaking: prefer more conservative verdict
        double maxWeight = verdictWeights.values().stream().mapToDouble(d -> d).max().orElse(0);
        List<String> tiedVerdicts = verdictWeights.entrySet().stream()
                .filter(e -> Math.abs(e.getValue() - maxWeight) < 0.01)
                .map(Map.Entry::getKey)
                .toList();

        if (tiedVerdicts.size() > 1) {
            if (tiedVerdicts.contains("veto")) aggregatedVerdict = "veto";
            else if (tiedVerdicts.contains("conditional_pass")) aggregatedVerdict = "conditional_pass";
        }

        // Compute aggregated score and confidence
        String finalVerdict = aggregatedVerdict;
        double avgScore = votes.stream().mapToDouble(v -> v.overallScore).average().orElse(0);
        double avgConfidence = votes.stream().mapToDouble(v -> v.confidence).average().orElse(0);
        double agreementRate = (double) votes.stream()
                .filter(v -> v.verdict.equals(finalVerdict)).count() / votes.size();

        VotingMetadata votingMetadata = new VotingMetadata(
                votes.size(), votes.size() >= VOTING_QUORUM,
                votes, aggregatedVerdict, avgConfidence, agreementRate);

        log.info("Majority voting result: verdict={}, score={}, confidence={}, agreement={}%, runId={}",
                aggregatedVerdict, String.format("%.2f", avgScore),
                String.format("%.2f", avgConfidence),
                String.format("%.0f", agreementRate * 100), runId);

        return new JudgeVerdict(
                runId, checkpoint, artifactKey, "code_quality",
                avgScore, aggregatedVerdict, avgConfidence,
                votes.get(0).criterionScores,
                List.of(), determineRecommendation(aggregatedVerdict, avgScore),
                votingMetadata, Instant.now());
    }

    // --- Prompt Builders ---

    private String buildEvaluationPrompt(String checkpoint, String artifact) {
        return String.format("""
                You are the Judge — an independent quality arbiter. Evaluate the following artifact
                at checkpoint '%s' using the Code Quality Rubric.

                For each criterion, assign a level (excellent/good/acceptable/failing) with specific evidence.

                Criteria and weights:
                - correctness (0.30): Are requirements correctly implemented?
                - security (0.25): Are there vulnerabilities? OWASP compliance?
                - maintainability (0.20): Clean architecture? Tests? Documentation?
                - performance (0.15): Efficient algorithms? No N+1 queries?
                - test_coverage (0.10): Adequate test coverage?

                Respond with a JSON object containing:
                {"criteria": {"correctness": {"level": "...", "evidence": "..."}, ...}}

                ARTIFACT TO EVALUATE:
                %s
                """, checkpoint, truncate(artifact, 8000));
    }

    private String buildVotingPrompt(String checkpoint, String artifact, String emphasis) {
        return buildEvaluationPrompt(checkpoint, artifact) + "\n\nEMPHASIS: " + emphasis;
    }

    private String buildArbitrationPrompt(String pos1, String pos2, String ev1, String ev2) {
        return String.format("""
                You are the Judge arbitrating a conflict between two review positions.

                POSITION 1: %s
                EVIDENCE 1: %s

                POSITION 2: %s
                EVIDENCE 2: %s

                Evaluate both positions using the Code Quality Rubric criteria.
                Produce a verdict that resolves the conflict with evidence-based reasoning.

                Respond with: {"criteria": {"correctness": {"level": "...", "evidence": "..."}, ...}}
                """, pos1, ev1, pos2, ev2);
    }

    // --- Parsing & Scoring ---

    private Map<String, CriterionScore> parseCriterionScores(String llmResponse) {
        Map<String, CriterionScore> scores = new LinkedHashMap<>();

        try {
            JsonNode root = objectMapper.readTree(llmResponse);
            JsonNode criteria = root.has("criteria") ? root.get("criteria") : root;

            for (Map.Entry<String, Double> entry : CODE_QUALITY_WEIGHTS.entrySet()) {
                String criterion = entry.getKey();
                double weight = entry.getValue();

                if (criteria.has(criterion)) {
                    JsonNode node = criteria.get(criterion);
                    String level = node.path("level").asText("acceptable");
                    String evidence = node.path("evidence").asText("");
                    double score = LEVEL_SCORES.getOrDefault(level, 0.4);
                    scores.put(criterion, new CriterionScore(criterion, weight, score, level, evidence));
                } else {
                    scores.put(criterion, new CriterionScore(criterion, weight, 0.4, "acceptable", "Not evaluated"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Judge LLM response, using default scores", e);
            for (Map.Entry<String, Double> entry : CODE_QUALITY_WEIGHTS.entrySet()) {
                scores.put(entry.getKey(), new CriterionScore(
                        entry.getKey(), entry.getValue(), 0.4, "acceptable", "Parse error — defaulted"));
            }
        }

        return scores;
    }

    private double computeWeightedScore(Map<String, CriterionScore> criterionScores) {
        double totalWeight = 0;
        double weightedSum = 0;
        for (CriterionScore cs : criterionScores.values()) {
            weightedSum += cs.score * cs.weight;
            totalWeight += cs.weight;
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0;
    }

    private double computeConfidence(Map<String, CriterionScore> criterionScores) {
        // Confidence is higher when criterion scores are consistent (low variance)
        double[] scores = criterionScores.values().stream().mapToDouble(cs -> cs.score).toArray();
        if (scores.length == 0) return 0.5;

        double mean = Arrays.stream(scores).average().orElse(0.5);
        double variance = Arrays.stream(scores).map(s -> (s - mean) * (s - mean)).average().orElse(0);

        // Low variance → high confidence; max variance (0.25 for 0-1 range) → low confidence
        return Math.max(0.3, 1.0 - (variance * 4));
    }

    private String determineRecommendation(String verdict, double score) {
        return switch (verdict) {
            case "pass" -> "Artifact meets quality bar. Proceed to next step.";
            case "conditional_pass" -> String.format(
                    "Artifact is below quality bar (score=%.2f). Recommended improvements should be addressed before merge.", score);
            case "veto" -> String.format(
                    "VETO: Artifact fails quality bar (score=%.2f). Must be reworked before proceeding.", score);
            default -> "Evaluate further.";
        };
    }

    private String serializeVerdict(JudgeVerdict verdict) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(verdict.toMap());
        } catch (Exception e) {
            log.error("Failed to serialize judge verdict", e);
            return "{\"error\": \"serialization_failed\"}";
        }
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // --- Data Classes ---

    public record CriterionScore(String criterion, double weight, double score, String level, String evidence) {}

    public record VotingMetadata(
            int voterCount, boolean quorumMet, List<JudgeVerdict> individualVerdicts,
            String aggregatedVerdict, double aggregatedConfidence, double agreementRate) {}

    public record JudgeVerdict(
            UUID runId, String checkpoint, String artifactKey, String rubricName,
            double overallScore, String verdict, double confidence,
            Map<String, CriterionScore> criterionScores, List<Map<String, String>> findings,
            String recommendation, VotingMetadata votingMetadata, Instant timestamp) {

        public static JudgeVerdict error(UUID runId, String checkpoint, String artifactKey, String message) {
            return new JudgeVerdict(runId, checkpoint, artifactKey, "error",
                    0.0, "veto", 0.0, Map.of(), List.of(), "Error: " + message, null, Instant.now());
        }

        public boolean isVeto() { return "veto".equals(verdict); }
        public boolean isPass() { return "pass".equals(verdict); }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("run_id", runId.toString());
            map.put("checkpoint", checkpoint);
            map.put("artifact_key", artifactKey);
            map.put("rubric_name", rubricName);
            map.put("overall_score", overallScore);
            map.put("verdict", verdict);
            map.put("confidence", confidence);

            List<Map<String, Object>> criteria = new ArrayList<>();
            for (CriterionScore cs : criterionScores.values()) {
                criteria.add(Map.of(
                        "criterion", cs.criterion, "weight", cs.weight,
                        "score", cs.score, "level", cs.level, "evidence", cs.evidence));
            }
            map.put("per_criterion", criteria);
            map.put("findings", findings);
            map.put("recommendation", recommendation);
            if (votingMetadata != null) {
                map.put("voting_metadata", Map.of(
                        "voter_count", votingMetadata.voterCount,
                        "quorum_met", votingMetadata.quorumMet,
                        "aggregated_verdict", votingMetadata.aggregatedVerdict,
                        "aggregated_confidence", votingMetadata.aggregatedConfidence,
                        "agreement_rate", votingMetadata.agreementRate));
            }
            map.put("timestamp", timestamp.toString());
            return map;
        }
    }
}
