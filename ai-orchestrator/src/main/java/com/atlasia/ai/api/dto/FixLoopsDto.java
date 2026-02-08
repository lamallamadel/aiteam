package com.atlasia.ai.api.dto;

import java.util.List;
import java.util.Map;

public record FixLoopsDto(
        List<FixLoopPattern> patterns,
        Map<String, LoopStatistics> loopStatistics,
        double averageCiIterations,
        double averageE2eIterations,
        long runsWithMultipleCiIterations,
        long runsWithMultipleE2eIterations) {

    public record FixLoopPattern(
            String repo,
            int issueNumber,
            int ciFixCount,
            int e2eFixCount,
            int totalIterations,
            String status,
            String pattern) {
    }

    public record LoopStatistics(
            long totalRuns,
            double averageIterations,
            long maxIterations,
            double successRate) {
    }
}
