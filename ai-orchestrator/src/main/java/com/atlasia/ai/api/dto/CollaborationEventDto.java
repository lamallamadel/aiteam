package com.atlasia.ai.api.dto;

import com.atlasia.ai.model.CollaborationEventType;

import java.time.Instant;
import java.util.UUID;

public record CollaborationEventDto(
    UUID id,
    UUID runId,
    String userId,
    CollaborationEventType eventType,
    String eventData,
    Instant timestamp
) {}
