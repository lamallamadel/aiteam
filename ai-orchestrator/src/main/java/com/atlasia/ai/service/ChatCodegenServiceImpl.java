package com.atlasia.ai.service;

import com.atlasia.ai.api.AiProviderRouter;
import com.atlasia.ai.api.dto.AiWireTypes.AiPrompt;
import com.atlasia.ai.api.dto.CodegenRequest;
import com.atlasia.ai.api.dto.CodegenResponse;
import com.atlasia.ai.config.ChatPersonaLoader;
import com.atlasia.ai.model.ConversationTurnEntity;
import com.atlasia.ai.domain.CodeGenerationResult;
import com.atlasia.ai.model.ChatArtifactEntity;
import com.atlasia.ai.model.ChatGenerationRunEntity;
import com.atlasia.ai.persistence.ChatArtifactRepository;
import com.atlasia.ai.persistence.ChatGenerationRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implements Chat Mode code generation without Spring AI.
 *
 * The LLM is prompted via {@link AiProviderRouter} (multi-provider Chat Mode).
 * A JSON schema instruction is appended to the system prompt so the model returns
 * structured output. The response is parsed with Jackson; if parsing fails the run
 * is recorded as PARTIAL and the raw text is returned in the summary field.
 *
 * Existing artifacts are injected into the system prompt as context so the LLM
 * knows what has already been generated and can produce consistent follow-up files.
 */
@Service
public class ChatCodegenServiceImpl implements ChatCodegenService {

    private static final Logger log = LoggerFactory.getLogger(ChatCodegenServiceImpl.class);

    private static final String CODEGEN_SCHEMA_INSTRUCTION = """

# Code generation mode
You are in structured code generation mode. You MUST respond with valid JSON only.
No text before or after the JSON. Use this exact schema:

{
  "summary": "what you are generating and why",
  "files": [
    {
      "file_name": "FileName.java",
      "file_path": "src/main/java/com/atlasia/ai/...",
      "language": "java",
      "artifact_type": "SOURCE_CODE",
      "content": "full file content here",
      "description": "purpose of this file"
    }
  ],
  "next_steps": ["step 1", "step 2"]
}

artifact_type values: SOURCE_CODE, SPEC, CONFIG, TEST, MIGRATION, OTHER
""";

    private final AiProviderRouter aiProviderRouter;
    private final ChatPersonaLoader personaLoader;
    private final ConversationMemoryService memoryService;
    private final ChatArtifactRepository artifactRepo;
    private final ChatGenerationRunRepository runRepo;
    private final ObjectMapper objectMapper;

    public ChatCodegenServiceImpl(
            AiProviderRouter aiProviderRouter,
            ChatPersonaLoader personaLoader,
            ConversationMemoryService memoryService,
            ChatArtifactRepository artifactRepo,
            ChatGenerationRunRepository runRepo,
            ObjectMapper objectMapper) {
        this.aiProviderRouter = aiProviderRouter;
        this.personaLoader = personaLoader;
        this.memoryService = memoryService;
        this.artifactRepo = artifactRepo;
        this.runRepo = runRepo;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CodeGenerationResult generate(CodegenRequest request) {
        String sessionKey = request.userId() + "::" + request.personaId();

        String systemPrompt = buildCodegenSystemPrompt(request.personaId(), sessionKey);

        var history = memoryService.getContextWindow(request.userId(), request.personaId());
        List<Map<String, String>> histMaps = new ArrayList<>();
        for (ConversationTurnEntity t : history) {
            histMaps.add(Map.of("role", t.getRole(), "content", t.getContent()));
        }

        log.debug("Codegen: userId={} persona={} historyTurns={}", request.userId(),
                request.personaId(), history.size());

        var aiResponse = aiProviderRouter.call(
                request.personaId(), new AiPrompt(systemPrompt, request.message(), histMaps));
        String rawReply = aiResponse.content();

        memoryService.saveTurns(request.userId(), request.personaId(), request.message(), rawReply);

        return parseAndPersist(request, sessionKey, rawReply);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CodegenResponse.ArtifactDto> listArtifacts(String userId, String personaId) {
        String sessionKey = userId + "::" + personaId;
        return artifactRepo.findBySessionKeyAndIsLatestTrueOrderByCreatedAtDesc(sessionKey)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private String buildCodegenSystemPrompt(String personaId, String sessionKey) {
        StringBuilder sb = new StringBuilder();

        // 1. Persona identity
        if (personaLoader.hasPersona(personaId)) {
            sb.append(personaLoader.getSystemPrompt(personaId));
        } else {
            sb.append("You are a skilled software developer.\n");
        }

        // 2. JSON schema instruction
        sb.append(CODEGEN_SCHEMA_INSTRUCTION);

        // 3. Existing artifacts context
        List<ChatArtifactEntity> existing =
                artifactRepo.findBySessionKeyAndIsLatestTrueOrderByCreatedAtDesc(sessionKey);
        if (!existing.isEmpty()) {
            sb.append("\n# Files already generated in this session\n");
            existing.forEach(a ->
                sb.append("- `").append(a.getFilePath()).append("` (")
                  .append(a.getArtifactType()).append("): ").append(a.getDescription()).append("\n")
            );
            sb.append("Do not regenerate these files unless the user explicitly asks.\n");
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private CodeGenerationResult parseAndPersist(CodegenRequest request, String sessionKey, String rawReply) {
        Map<String, Object> parsed;
        try {
            // Strip markdown code fences if the LLM wrapped the JSON
            String json = rawReply.strip();
            if (json.startsWith("```")) {
                int start = json.indexOf('\n') + 1;
                int end = json.lastIndexOf("```");
                json = end > start ? json.substring(start, end).strip() : json;
            }
            parsed = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Codegen: LLM reply was not valid JSON for persona={} — storing as PARTIAL run",
                    request.personaId());
            runRepo.save(new ChatGenerationRunEntity(
                    sessionKey, request.userId(), request.personaId(),
                    request.message(), "FAILED", 0));
            return CodeGenerationResult.failed(
                    request.personaId(), request.userId(),
                    "LLM did not return valid JSON: " + e.getMessage(),
                    CodeGenerationResult.FailureKind.PARSE_ERROR, e);
        }

        String summary = (String) parsed.getOrDefault("summary", "");
        List<String> nextSteps = (List<String>) parsed.getOrDefault("next_steps", List.of());
        List<Map<String, Object>> files =
                (List<Map<String, Object>>) parsed.getOrDefault("files", List.of());

        // Explanatory response — LLM answered in prose, no files produced
        if (files.isEmpty() && !summary.isBlank()) {
            runRepo.save(new ChatGenerationRunEntity(
                    sessionKey, request.userId(), request.personaId(),
                    request.message(), "EXPLANATORY", 0));
            log.info("Codegen explanatory: persona={} userId={}", request.personaId(), request.userId());
            return CodeGenerationResult.explanatory(
                    request.personaId(), request.userId(), null, summary, nextSteps);
        }

        var run = runRepo.save(new ChatGenerationRunEntity(
                sessionKey, request.userId(), request.personaId(),
                request.message(), "COMPLETED", files.size()));

        List<CodegenResponse.ArtifactDto> dtos = files.stream()
                .map(f -> persistArtifact(run, sessionKey, request, f, summary))
                .toList();

        log.info("Codegen complete: persona={} userId={} files={}", request.personaId(),
                request.userId(), dtos.size());
        return CodeGenerationResult.generated(
                request.personaId(), request.userId(), run.getId(),
                summary, dtos, nextSteps, 0L);
    }

    private CodegenResponse.ArtifactDto persistArtifact(
            ChatGenerationRunEntity run, String sessionKey,
            CodegenRequest request, Map<String, Object> file, String promptSummary) {

        String filePath = (String) file.getOrDefault("file_path", "");

        // Version bump: mark previous version as not-latest
        if (!filePath.isBlank()) {
            artifactRepo.markPreviousVersionsAsNotLatest(sessionKey, filePath);
        }

        var entity = new ChatArtifactEntity(
                run, sessionKey, request.userId(), request.personaId(),
                (String) file.getOrDefault("file_name", ""),
                filePath,
                (String) file.get("language"),
                (String) file.getOrDefault("artifact_type", "OTHER"),
                (String) file.getOrDefault("content", ""),
                (String) file.get("description"),
                promptSummary);

        artifactRepo.save(entity);
        return toDto(entity);
    }

    private CodegenResponse.ArtifactDto toDto(ChatArtifactEntity a) {
        return new CodegenResponse.ArtifactDto(
                a.getId(), a.getFileName(), a.getFilePath(),
                a.getLanguage(), a.getArtifactType(), a.getDescription());
    }
}
