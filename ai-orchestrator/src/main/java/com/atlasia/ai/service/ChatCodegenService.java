package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.CodegenRequest;
import com.atlasia.ai.api.dto.CodegenResponse;

import java.util.List;

/**
 * Orchestrates Chat Mode code generation: builds a structured JSON prompt,
 * calls the LLM, parses the response, persists generated artifacts with
 * versioning, and injects existing-file context on follow-up turns.
 */
public interface ChatCodegenService {

    /**
     * Runs one code generation turn and returns the persisted artifacts.
     */
    CodegenResponse generate(CodegenRequest request);

    /**
     * Returns all latest artifacts for a session (the current generated file tree).
     */
    List<CodegenResponse.ArtifactDto> listArtifacts(String userId, String personaId);
}
