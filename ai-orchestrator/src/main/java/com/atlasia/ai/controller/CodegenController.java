package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.CodegenRequest;
import com.atlasia.ai.api.dto.CodegenResponse;
import com.atlasia.ai.service.ChatCodegenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<CodegenResponse> generate(@RequestBody CodegenRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.personaId() == null || request.personaId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(codegenService.generate(request));
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
