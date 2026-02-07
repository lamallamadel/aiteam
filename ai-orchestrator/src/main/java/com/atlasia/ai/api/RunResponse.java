package com.atlasia.ai.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunResponse(
        UUID id,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String currentAgent,
        Integer ciFixCount,
        Integer e2eFixCount,
        List<RunResponse.ArtifactSummary> artifacts
) {
    public record ArtifactSummary(
            UUID id,
            String agentName,
            String artifactType,
            Instant createdAt
    ) {}
}
