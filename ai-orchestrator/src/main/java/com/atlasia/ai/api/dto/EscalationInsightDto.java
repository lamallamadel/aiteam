package com.atlasia.ai.api.dto;

import java.util.List;
import java.util.Map;

public record EscalationInsightDto(
        int totalEscalationsAnalysed,
        Map<String, Integer> topErrorPatterns,
        List<String> problematicFiles,
        List<EscalationCluster> clusters) {
    public record EscalationCluster(
            String label,
            int count,
            String suggestedRootCause) {
    }
}
