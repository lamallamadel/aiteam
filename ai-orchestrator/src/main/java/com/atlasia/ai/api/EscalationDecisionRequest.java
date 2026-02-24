package com.atlasia.ai.api;

import com.atlasia.ai.service.exception.validation.SafeHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EscalationDecisionRequest(
        @NotBlank(message = "Decision is required")
        @Pattern(regexp = "^(PROCEED|ABORT)$", message = "Decision must be either 'PROCEED' or 'ABORT'")
        String decision,
        
        @SafeHtml
        @Size(max = 5000, message = "Guidance must not exceed 5000 characters")
        String guidance
) {}
