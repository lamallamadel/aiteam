package com.atlasia.ai.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CollaborationEventDto(
    UUID id,
    UUID runId,
    String userId,
    String eventType,
    String eventData,
    Instant timestamp
) {}
