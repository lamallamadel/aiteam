package com.atlasia.ai.api.dto;

import java.time.Instant;
import java.util.UUID;

public record GraftExecutionDto(
        UUID id,
        UUID runId,
        String graftId,
        String agentName,
        String checkpointAfter,
        Instant startedAt,
        Instant completedAt,
        String status,
        UUID outputArtifactId,
        String errorMessage,
        int retryCount,
        long timeoutMs,
        Instant createdAt,
        Instant updatedAt
) {}
