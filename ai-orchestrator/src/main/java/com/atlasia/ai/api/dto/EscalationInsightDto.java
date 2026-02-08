package com.atlasia.ai.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EscalationInsightDto(
        int totalEscalationsAnalysed,
        Map<String, Integer> topErrorPatterns,
        List<String> problematicFiles,
        List<EscalationCluster> clusters,
        List<FilePathPattern> filePathPatterns,
        List<AgentBottleneck> agentBottlenecks,
        List<KeywordInsight> topKeywords,
        Instant generatedAt) {

    public record EscalationCluster(
            String label,
            int count,
            String suggestedRootCause,
            double percentage) {
    }

    public record FilePathPattern(
            String filePath,
            int frequency,
            String fileType) {
    }

    public record AgentBottleneck(
            String agentName,
            int escalationCount,
            double percentage) {
    }

    public record KeywordInsight(
            String keyword,
            int frequency) {
    }
}
