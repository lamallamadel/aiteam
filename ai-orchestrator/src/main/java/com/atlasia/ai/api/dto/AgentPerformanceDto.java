package com.atlasia.ai.api.dto;

import java.util.List;
import java.util.Map;

public record AgentPerformanceDto(
        Map<String, Double> avgDurationByAgent,
        Map<String, Double> errorRateByAgent,
        Map<String, Double> avgFixCountByStatus) {
}
