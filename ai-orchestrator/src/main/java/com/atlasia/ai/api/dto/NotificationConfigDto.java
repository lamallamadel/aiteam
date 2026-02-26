package com.atlasia.ai.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationConfigDto(
        UUID id,
        UUID userId,
        String provider,
        String webhookUrl,
        List<String> enabledEvents,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
