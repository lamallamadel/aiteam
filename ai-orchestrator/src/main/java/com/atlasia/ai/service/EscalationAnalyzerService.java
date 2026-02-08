package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.EscalationInsightDto;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.RunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EscalationAnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(EscalationAnalyzerService.class);

    private final RunRepository runRepository;
    private final ObjectMapper objectMapper;

    @Value("${escalation.insights.output.dir:./insights}")
    private String insightsOutputDir;

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("([a-zA-Z0-9_\\-./]+\\.(java|ts|js|tsx|jsx|py|go|rb|cs|cpp|h|yml|yaml|json|xml|sql))");
    private static final int MIN_KEYWORD_FREQUENCY = 2;

    public EscalationAnalyzerService(RunRepository runRepository, ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    public EscalationInsightDto analyzeEscalations() {
        List<RunEntity> runs = runRepository.findEscalatedRunsWithArtifacts();
        log.info("Analyzing {} escalated runs", runs.size());

        Map<String, Integer> errorPatterns = new HashMap<>();
        Map<String, Integer> filePathCounts = new HashMap<>();
        Map<String, Integer> agentBottlenecks = new HashMap<>();
        Map<String, Integer> keywordFrequency = new HashMap<>();
        List<String> problematicFiles = new ArrayList<>();

        for (RunEntity run : runs) {
            String currentAgent = run.getCurrentAgent();
            if (currentAgent != null) {
                agentBottlenecks.merge(currentAgent, 1, Integer::sum);
            }

            run.getArtifacts().stream()
                    .filter(a -> "escalation.json".equals(a.getArtifactType()))
                    .findFirst()
                    .ifPresent(artifact -> analyzeEscalationArtifact(
                            artifact, errorPatterns, filePathCounts, keywordFrequency));
        }

        problematicFiles.addAll(filePathCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .map(Map.Entry::getKey)
                .toList());

        List<EscalationInsightDto.EscalationCluster> clusters = buildClusters(errorPatterns);
        List<EscalationInsightDto.FilePathPattern> filePatterns = buildFilePathPatterns(filePathCounts);
        List<EscalationInsightDto.AgentBottleneck> agentBottleneckList = buildAgentBottlenecks(agentBottlenecks);
        List<EscalationInsightDto.KeywordInsight> topKeywords = buildTopKeywords(keywordFrequency);

        EscalationInsightDto insights = new EscalationInsightDto(
                runs.size(),
                errorPatterns,
                problematicFiles,
                clusters,
                filePatterns,
                agentBottleneckList,
                topKeywords,
                Instant.now()
        );

        saveInsightsToFile(insights);

        return insights;
    }

    private void saveInsightsToFile(EscalationInsightDto insights) {
        try {
            Path outputPath = Paths.get(insightsOutputDir);
            Files.createDirectories(outputPath);

            String timestamp = DateTimeFormatter.ISO_INSTANT.format(insights.generatedAt())
                    .replaceAll(":", "-");
            Path filePath = outputPath.resolve("escalation_insights_" + timestamp + ".json");

            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(insights);

            Files.writeString(filePath, jsonContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            Path latestLink = outputPath.resolve("escalation_insights.json");
            Files.writeString(latestLink, jsonContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Escalation insights saved to: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to save escalation insights to file", e);
        }
    }

    private void analyzeEscalationArtifact(
            RunArtifactEntity artifact,
            Map<String, Integer> errorPatterns,
            Map<String, Integer> filePathCounts,
            Map<String, Integer> keywordFrequency) {

        String payload = artifact.getPayload();

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            String reason = extractTextFromJson(jsonNode, "reason");
            String details = extractTextFromJson(jsonNode, "details");
            String errorMessage = extractTextFromJson(jsonNode, "error");
            String context = extractTextFromJson(jsonNode, "context");
            
            String allText = String.join(" ", reason, details, errorMessage, context).toLowerCase();

            classifyErrorPattern(allText, errorPatterns);
            extractFilePaths(allText, filePathCounts);
            extractKeywords(allText, keywordFrequency);

        } catch (Exception e) {
            log.warn("Failed to parse escalation JSON, falling back to text analysis: {}", e.getMessage());
            String lowerPayload = payload.toLowerCase();
            classifyErrorPattern(lowerPayload, errorPatterns);
            extractFilePaths(lowerPayload, filePathCounts);
            extractKeywords(lowerPayload, keywordFrequency);
        }
    }

    private String extractTextFromJson(JsonNode node, String fieldName) {
        if (node.has(fieldName)) {
            JsonNode field = node.get(fieldName);
            if (field.isTextual()) {
                return field.asText();
            } else if (field.isObject() || field.isArray()) {
                return field.toString();
            }
        }
        return "";
    }

    private void classifyErrorPattern(String text, Map<String, Integer> errorPatterns) {
        if (text.contains("timeout") || text.contains("timed out")) {
            errorPatterns.merge("TIMEOUT", 1, Integer::sum);
        }
        if (text.contains("compile") || text.contains("compilation") || text.contains("syntax")) {
            errorPatterns.merge("COMPILATION_ERROR", 1, Integer::sum);
        }
        if (text.contains("selector") || text.contains("element not found") || text.contains("cannot find element")) {
            errorPatterns.merge("E2E_SELECTOR_MISSING", 1, Integer::sum);
        }
        if (text.contains("null pointer") || text.contains("nullpointerexception") || text.contains("undefined")) {
            errorPatterns.merge("NULL_REFERENCE", 1, Integer::sum);
        }
        if (text.contains("dependency") || text.contains("module not found") || text.contains("import")) {
            errorPatterns.merge("DEPENDENCY_ERROR", 1, Integer::sum);
        }
        if (text.contains("test failed") || text.contains("assertion") || text.contains("expected")) {
            errorPatterns.merge("TEST_FAILURE", 1, Integer::sum);
        }
        if (text.contains("permission") || text.contains("access denied") || text.contains("forbidden")) {
            errorPatterns.merge("PERMISSION_ERROR", 1, Integer::sum);
        }
        if (text.contains("network") || text.contains("connection") || text.contains("econnrefused")) {
            errorPatterns.merge("NETWORK_ERROR", 1, Integer::sum);
        }
        if (text.contains("memory") || text.contains("heap") || text.contains("oom")) {
            errorPatterns.merge("MEMORY_ERROR", 1, Integer::sum);
        }
        if (!errorPatterns.isEmpty() && errorPatterns.values().stream().mapToInt(Integer::intValue).sum() == 0) {
            errorPatterns.merge("UNCLASSIFIED", 1, Integer::sum);
        }
    }

    private void extractFilePaths(String text, Map<String, Integer> filePathCounts) {
        Matcher matcher = FILE_PATH_PATTERN.matcher(text);
        while (matcher.find()) {
            String filePath = matcher.group(1);
            if (filePath.length() > 3 && filePath.length() < 200) {
                filePathCounts.merge(filePath, 1, Integer::sum);
            }
        }
    }

    private void extractKeywords(String text, Map<String, Integer> keywordFrequency) {
        String[] words = text.split("\\W+");
        for (String word : words) {
            if (word.length() > 4 && word.length() < 30 && !isCommonWord(word)) {
                keywordFrequency.merge(word, 1, Integer::sum);
            }
        }
    }

    private boolean isCommonWord(String word) {
        Set<String> commonWords = Set.of(
                "error", "failed", "failure", "exception", "warning", "cannot", "unable",
                "message", "string", "number", "boolean", "object", "array", "function",
                "method", "class", "interface", "public", "private", "protected", "static",
                "return", "value", "parameter", "argument", "variable", "field", "property",
                "expected", "actual", "received", "found", "missing", "required", "optional"
        );
        return commonWords.contains(word);
    }

    private List<EscalationInsightDto.EscalationCluster> buildClusters(Map<String, Integer> errorPatterns) {
        return errorPatterns.entrySet().stream()
                .map(e -> new EscalationInsightDto.EscalationCluster(
                        e.getKey(),
                        e.getValue(),
                        getSuggestedRootCause(e.getKey()),
                        calculatePercentage(e.getValue(), getTotalCount(errorPatterns))
                ))
                .sorted(Comparator.comparingInt(EscalationInsightDto.EscalationCluster::count).reversed())
                .toList();
    }

    private List<EscalationInsightDto.FilePathPattern> buildFilePathPatterns(Map<String, Integer> filePathCounts) {
        return filePathCounts.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_KEYWORD_FREQUENCY)
                .map(e -> new EscalationInsightDto.FilePathPattern(
                        e.getKey(),
                        e.getValue(),
                        extractFileType(e.getKey())
                ))
                .sorted(Comparator.comparingInt(EscalationInsightDto.FilePathPattern::frequency).reversed())
                .limit(50)
                .toList();
    }

    private List<EscalationInsightDto.AgentBottleneck> buildAgentBottlenecks(Map<String, Integer> agentBottlenecks) {
        int totalEscalations = agentBottlenecks.values().stream().mapToInt(Integer::intValue).sum();
        return agentBottlenecks.entrySet().stream()
                .map(e -> new EscalationInsightDto.AgentBottleneck(
                        e.getKey(),
                        e.getValue(),
                        calculatePercentage(e.getValue(), totalEscalations)
                ))
                .sorted(Comparator.comparingInt(EscalationInsightDto.AgentBottleneck::escalationCount).reversed())
                .toList();
    }

    private List<EscalationInsightDto.KeywordInsight> buildTopKeywords(Map<String, Integer> keywordFrequency) {
        return keywordFrequency.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_KEYWORD_FREQUENCY)
                .map(e -> new EscalationInsightDto.KeywordInsight(
                        e.getKey(),
                        e.getValue()
                ))
                .sorted(Comparator.comparingInt(EscalationInsightDto.KeywordInsight::frequency).reversed())
                .limit(30)
                .toList();
    }

    private String extractFileType(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot + 1);
        }
        return "unknown";
    }

    private double calculatePercentage(int count, int total) {
        if (total == 0) return 0.0;
        return Math.round((double) count / total * 10000.0) / 100.0;
    }

    private int getTotalCount(Map<String, Integer> map) {
        return map.values().stream().mapToInt(Integer::intValue).sum();
    }

    private String getSuggestedRootCause(String pattern) {
        return switch (pattern) {
            case "TIMEOUT" -> "Slow environment, infinite loops, or unoptimized queries";
            case "COMPILATION_ERROR" -> "Incomplete code generation, syntax errors, or missing dependencies";
            case "E2E_SELECTOR_MISSING" -> "Frontend UI changes not reflected in E2E test selectors";
            case "NULL_REFERENCE" -> "Missing null checks or uninitialized variables";
            case "DEPENDENCY_ERROR" -> "Missing dependencies, version conflicts, or import errors";
            case "TEST_FAILURE" -> "Test expectations don't match implementation or brittle tests";
            case "PERMISSION_ERROR" -> "Insufficient permissions, authentication issues, or access control problems";
            case "NETWORK_ERROR" -> "Service unavailability, network connectivity, or firewall issues";
            case "MEMORY_ERROR" -> "Memory leaks, large data structures, or insufficient heap size";
            default -> "Needs manual investigation";
        };
    }
}
