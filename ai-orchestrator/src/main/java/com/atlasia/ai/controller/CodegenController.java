package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.CodegenRequest;
import com.atlasia.ai.api.dto.CodegenResponse;
import com.atlasia.ai.domain.CodeGenerationResult;
import com.atlasia.ai.domain.CodeGenerationResult.*;
import com.atlasia.ai.service.ChatCodegenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * POST /api/codegen      — generate code files with a Chat Mode persona
 * GET  /api/codegen/artifacts?userId=&personaId= — list current generated files
 */
@RestController
@RequestMapping("/api/codegen")
public class CodegenController {

    private final ChatCodegenService codegenService;

    public CodegenController(ChatCodegenService codegenService) {
        this.codegenService = codegenService;
    }

    @PostMapping
    public ResponseEntity<?> generate(@RequestBody CodegenRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.personaId() == null || request.personaId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return switch (codegenService.generate(request)) {

            case Generated g ->
                    ResponseEntity.ok(g.toCodegenResponse());

            case Explanatory e ->
                    ResponseEntity.ok(Map.of(
                            "personaId",          e.personaId(),
                            "response",           e.response(),
                            "followUpQuestions",  e.followUpQuestions() != null
                                                  ? e.followUpQuestions() : List.of()
                    ));

            case GenerationFailed f ->
                    ResponseEntity.status(f.httpStatus())
                            .body(Map.of(
                                    "error",   f.reason(),
                                    "kind",    f.kind().name(),
                                    "retryable", f.isRetryable()
                            ));
        };
    }

    @GetMapping("/artifacts")
    public ResponseEntity<List<CodegenResponse.ArtifactDto>> listArtifacts(
            @RequestParam String userId,
            @RequestParam String personaId) {
        if (userId == null || userId.isBlank() || personaId == null || personaId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(codegenService.listArtifacts(userId, personaId));
    }
}
