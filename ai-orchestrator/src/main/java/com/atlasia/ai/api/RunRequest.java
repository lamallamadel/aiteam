package com.atlasia.ai.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RunRequest(
        @NotBlank String repo,
        @NotNull @Min(1) Integer issueNumber,
        @NotBlank String mode
) {}
