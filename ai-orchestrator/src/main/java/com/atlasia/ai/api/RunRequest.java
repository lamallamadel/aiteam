package com.atlasia.ai.api;

import com.atlasia.ai.service.exception.validation.RepositoryPath;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RunRequest(
        @NotBlank(message = "Repository is required")
        @RepositoryPath
        @Size(max = 255, message = "Repository path must not exceed 255 characters")
        String repo,

        // Either issueNumber OR goal must be provided (validated in RunController)
        @Min(value = 1, message = "Issue number must be at least 1")
        Integer issueNumber,

        @Size(max = 4000, message = "Goal must not exceed 4000 characters")
        String goal,

        @NotBlank(message = "Mode is required")
        @Pattern(regexp = "^(code|chat)$", message = "Mode must be either 'code' or 'chat'")
        @Size(max = 50, message = "Mode must not exceed 50 characters")
        String mode,

        @Pattern(regexp = "^(autonomous|confirm|observe)?$", message = "Autonomy must be 'autonomous', 'confirm', or 'observe'")
        @Size(max = 50, message = "Autonomy must not exceed 50 characters")
        String autonomy,  // optional: "autonomous" (default), "confirm", "observe"

        String githubToken // optional: user's GitHub PAT, read from their configured git provider
) {}
