package com.atlasia.ai.api;

import com.atlasia.ai.service.exception.validation.RepositoryPath;
import com.atlasia.ai.service.exception.validation.SafeHtml;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RunRequest(
        @NotBlank(message = "Repository is required")
        @RepositoryPath
        @Size(max = 255, message = "Repository path must not exceed 255 characters")
        String repo,
        
        @NotNull(message = "Issue number is required")
        @Min(value = 1, message = "Issue number must be at least 1")
        Integer issueNumber,
        
        @NotBlank(message = "Mode is required")
        @Pattern(regexp = "^(code|chat)$", message = "Mode must be either 'code' or 'chat'")
        @Size(max = 50, message = "Mode must not exceed 50 characters")
        String mode,
        
        @Pattern(regexp = "^(autonomous|confirm|observe)?$", message = "Autonomy must be 'autonomous', 'confirm', or 'observe'")
        @Size(max = 50, message = "Autonomy must not exceed 50 characters")
        String autonomy   // optional: "autonomous" (default), "confirm", "observe"
) {}
