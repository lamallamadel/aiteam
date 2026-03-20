package com.atlasia.ai.api.dto;

import com.atlasia.ai.model.GraftExecutionEntity.GraftExecutionStatus;

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
        GraftExecutionStatus status,
        UUID outputArtifactId,
        String errorMessage,
        int retryCount,
        long timeoutMs,
        Instant createdAt,
        Instant updatedAt
) {}
