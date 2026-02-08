package com.atlasia.ai.api.dto;

import java.util.List;

public record AgentsPerformanceDto(
        List<AgentMetrics> agentMetrics,
        double overallAverageDuration,
        double overallErrorRate) {

    public record AgentMetrics(
            String agentName,
            long totalRuns,
            double averageDuration,
            double errorRate,
            double successRate,
            double averageCiFixCount,
            double averageE2eFixCount) {
    }
}
