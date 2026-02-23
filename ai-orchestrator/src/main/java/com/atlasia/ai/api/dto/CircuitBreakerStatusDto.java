package com.atlasia.ai.api.dto;

import java.time.Instant;
import java.util.List;

public record CircuitBreakerStatusDto(
        String agentName,
        String state,
        int failureCount,
        Instant lastFailureTime,
        long successfulExecutions,
        long failedExecutions,
        List<FailureRecord> recentFailures,
        double failureRate
) {
    public record FailureRecord(
            String graftId,
            Instant timestamp,
            String errorMessage
    ) {}
}
