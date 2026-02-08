package com.atlasia.ai.api.dto;

import java.time.Instant;
import java.util.Map;

public record RunsSummaryDto(
        long totalRuns,
        double successRate,
        double failureRate,
        double escalationRate,
        Map<String, Long> statusBreakdown,
        Map<String, TimeSeriesData> timeSeriesData,
        Instant startDate,
        Instant endDate) {

    public record TimeSeriesData(
            Map<String, Long> runCountByDate,
            Map<String, Double> successRateByDate,
            Map<String, Double> failureRateByDate,
            Map<String, Double> escalationRateByDate) {
    }
}
