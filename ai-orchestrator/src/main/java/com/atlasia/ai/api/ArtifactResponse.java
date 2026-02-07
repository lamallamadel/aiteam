package com.atlasia.ai.api;

import java.time.Instant;
import java.util.UUID;

public record ArtifactResponse(
        UUID id,
        String agentName,
        String artifactType,
        String payload,
        Instant createdAt
) {}
