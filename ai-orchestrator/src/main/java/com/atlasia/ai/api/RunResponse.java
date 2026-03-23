package com.atlasia.ai.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RunResponse(
                UUID id,
                String repo,
                Integer issueNumber,
                String goal,
                String mode,
                String status,
                Instant createdAt,
                Instant updatedAt,
                String currentAgent,
                Integer ciFixCount,
                Integer e2eFixCount,
                String environmentLifecycle,
                String environmentCheckpoint,
                String prunedSteps,
                String pendingGrafts,
                List<RunResponse.ArtifactSummary> artifacts) {
        public record ArtifactSummary(
                        UUID id,
                        String agentName,
                        String artifactType,
                        Instant createdAt) {
        }
}
