package com.atlasia.ai.api;

import jakarta.validation.constraints.NotBlank;

public record EscalationDecisionRequest(
        @NotBlank String decision,
        String guidance
) {}
