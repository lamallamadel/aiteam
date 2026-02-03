package com.atlasia.ai.api;

import java.time.Instant;
import java.util.UUID;

public record RunResponse(
        UUID id,
        String status,
        Instant createdAt
) {}
