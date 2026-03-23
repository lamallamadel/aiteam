package com.atlasia.ai.api;

import jakarta.validation.constraints.NotBlank;

public record GateRespondRequest(
        @NotBlank String decision,
        String comment) {
}
