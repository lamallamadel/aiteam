package com.atlasia.ai.api.dto;

import com.atlasia.ai.model.CircuitBreakerState;

import java.time.Instant;
import java.util.List;

public record CircuitBreakerStatusDto(
        String agentName,
        CircuitBreakerState state,
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
