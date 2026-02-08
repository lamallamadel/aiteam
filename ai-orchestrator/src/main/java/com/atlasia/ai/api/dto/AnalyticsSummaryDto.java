package com.atlasia.ai.api.dto;

import java.util.Map;

public record AnalyticsSummaryDto(
        long totalRuns,
        double successRate,
        double failureRate,
        double escalationRate,
        Map<String, Long> statusBreakdown,
        Map<String, Long> repoBreakdown) {
}
