package com.atlasia.ai.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response from POST /api/codegen.
 *
 * @param runId        ID of the ChatGenerationRunEntity created for this turn
 * @param success      false only when the LLM returned unparseable output
 * @param summary      what the persona did
 * @param artifacts    the files generated in this turn
 * @param nextSteps    persona-suggested follow-up actions
 * @param errorMessage set only when success=false
 */
public record CodegenResponse(
        UUID runId,
        boolean success,
        String summary,
        List<ArtifactDto> artifacts,
        List<String> nextSteps,
        String errorMessage
) {
    public record ArtifactDto(
            UUID id,
            String fileName,
            String filePath,
            String language,
            String artifactType,
            String description
    ) {}
}
