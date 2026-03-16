package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.CodegenRequest;
import com.atlasia.ai.api.dto.CodegenResponse;
import com.atlasia.ai.domain.CodeGenerationResult;

import java.util.List;

/**
 * Orchestrates Chat Mode code generation: builds a structured JSON prompt,
 * calls the LLM, parses the response, persists generated artifacts with
 * versioning, and injects existing-file context on follow-up turns.
 */
public interface ChatCodegenService {

    /**
     * Runs one code generation turn.
     *
     * <p>Returns a sealed {@link CodeGenerationResult} — callers switch exhaustively:</p>
     * <pre>
     * switch (codegenService.generate(request)) {
     *   case Generated g        → ResponseEntity.ok(g.toCodegenResponse())
     *   case Explanatory e      → ResponseEntity.ok(toExplanatoryDto(e))
     *   case GenerationFailed f → ResponseEntity.status(f.httpStatus()).body(...)
     * }
     * </pre>
     */
    CodeGenerationResult generate(CodegenRequest request);

    /**
     * Returns all latest artifacts for a session (the current generated file tree).
     */
    List<CodegenResponse.ArtifactDto> listArtifacts(String userId, String personaId);
}
