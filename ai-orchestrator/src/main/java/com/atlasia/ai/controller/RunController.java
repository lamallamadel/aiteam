package com.atlasia.ai.controller;

import com.atlasia.ai.api.ArtifactResponse;
import com.atlasia.ai.api.EscalationDecisionRequest;
import com.atlasia.ai.api.RunRequest;
import com.atlasia.ai.api.RunResponse;
import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.GitHubApiClient;
import com.atlasia.ai.service.WorkflowEngine;
import com.atlasia.ai.service.event.WorkflowEventBus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunRepository runRepository;
    private final OrchestratorProperties props;
    private final WorkflowEngine workflowEngine;
    private final GitHubApiClient gitHubApiClient;
    private final WorkflowEventBus eventBus;

    public RunController(RunRepository runRepository, OrchestratorProperties props,
            WorkflowEngine workflowEngine, GitHubApiClient gitHubApiClient,
            WorkflowEventBus eventBus) {
        this.runRepository = runRepository;
        this.props = props;
        this.workflowEngine = workflowEngine;
        this.gitHubApiClient = gitHubApiClient;
        this.eventBus = eventBus;
    }

    @GetMapping
    public ResponseEntity<List<RunResponse>> listRuns(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<RunResponse> runs = runRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toRunResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(runs);
    }

    @PostMapping
    public ResponseEntity<RunResponse> createRun(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RunRequest request) {
        String token = getValidatedToken(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID id = UUID.randomUUID();
        RunEntity entity = new RunEntity(
                id,
                request.repo(),
                request.issueNumber(),
                request.mode(),
                RunStatus.RECEIVED,
                Instant.now());
        if (request.autonomy() != null && !request.autonomy().isBlank()) {
            entity.setAutonomy(request.autonomy());
        }
        runRepository.save(entity);

        workflowEngine.executeWorkflowAsync(id, token);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(toRunResponse(entity));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunResponse> get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runRepository.findById(id)
                .map(this::toRunResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/artifacts")
    public ResponseEntity<List<ArtifactResponse>> getArtifacts(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return runRepository.findById(id)
                .map(run -> run.getArtifacts().stream()
                        .map(artifact -> new ArtifactResponse(
                                artifact.getId(),
                                artifact.getAgentName(),
                                artifact.getArtifactType(),
                                artifact.getPayload(),
                                artifact.getCreatedAt()))
                        .collect(Collectors.toList()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "token", required = false) String queryToken,
            @PathVariable("id") UUID id) {
        String effectiveAuth = authorization != null ? authorization :
                (queryToken != null ? "Bearer " + queryToken : null);
        if (getValidatedToken(effectiveAuth) == null) {
            SseEmitter emitter = new SseEmitter(0L);
            emitter.completeWithError(new SecurityException("Unauthorized"));
            return emitter;
        }

        return runRepository.findById(id)
                .map(run -> eventBus.registerEmitter(id))
                .orElseGet(() -> {
                    SseEmitter emitter = new SseEmitter(0L);
                    emitter.completeWithError(new IllegalArgumentException("Run not found: " + id));
                    return emitter;
                });
    }

    /** Returns the serialized environment checkpoint for a run. */
    @GetMapping("/{id}/environment")
    public ResponseEntity<String> getEnvironment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runRepository.findById(id)
                .map(run -> {
                    String checkpoint = run.getEnvironmentCheckpoint();
                    if (checkpoint == null || checkpoint.isBlank()) {
                        return ResponseEntity.noContent().<String>build();
                    }
                    return ResponseEntity.ok(checkpoint);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Resume a run from its environment checkpoint.
     * Re-executes the workflow; WorkflowEngine will load checkpoint context.
     */
    @PostMapping("/{id}/resume")
    public ResponseEntity<RunResponse> resume(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id) {
        String token = getValidatedToken(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runRepository.findById(id)
                .map(run -> {
                    run.setStatus(RunStatus.RECEIVED);
                    run.setAutonomyDevGatePassed(true); // human has reviewed; bypass the autonomy gate
                    runRepository.save(run);
                    workflowEngine.executeWorkflowAsync(id, token);
                    return ResponseEntity.accepted().<RunResponse>body(toRunResponse(run));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/escalation-decision")
    public ResponseEntity<Void> handleEscalationDecision(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id,
            @Valid @RequestBody EscalationDecisionRequest request) {
        String token = getValidatedToken(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return runRepository.findById(id)
                .map(run -> {
                    if (run.getStatus() != RunStatus.ESCALATED) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).<Void>build();
                    }

                    RunArtifactEntity decisionArtifact = new RunArtifactEntity(
                            "HUMAN",
                            "escalation_decision.json",
                            String.format("{\"decision\":\"%s\",\"guidance\":\"%s\"}",
                                    request.decision(),
                                    request.guidance() != null ? request.guidance() : ""),
                            Instant.now());
                    run.addArtifact(decisionArtifact);

                    if ("PROCEED".equalsIgnoreCase(request.decision())) {
                        workflowEngine.executeWorkflowAsync(id, token);
                    } else if ("ABORT".equalsIgnoreCase(request.decision())) {
                        run.setStatus(RunStatus.FAILED);
                    }

                    runRepository.save(run);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Flag a pipeline step with an annotation (stored as a flag_annotation artifact).
     * Body: { "stepId": "PM", "note": "optional note" }
     */
    @PostMapping("/{id}/flags")
    public ResponseEntity<Void> flagStep(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id,
            @RequestBody java.util.Map<String, String> body) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runRepository.findById(id)
                .map(run -> {
                    String stepId = body.getOrDefault("stepId", "UNKNOWN");
                    String note   = body.getOrDefault("note", "");
                    RunArtifactEntity flag = new RunArtifactEntity(
                            "HUMAN",
                            "flag_annotation",
                            String.format("{\"stepId\":\"%s\",\"note\":\"%s\"}", stepId, note),
                            java.time.Instant.now());
                    run.addArtifact(flag);
                    runRepository.save(run);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update the pruned steps list.
     * Body: { "prunedSteps": "QUALIFIER,WRITER" }
     */
    @PutMapping("/{id}/pruned-steps")
    public ResponseEntity<Void> setPrunedSteps(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id,
            @RequestBody java.util.Map<String, String> body) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runRepository.findById(id)
                .map(run -> {
                    run.setPrunedSteps(body.get("prunedSteps"));
                    runRepository.save(run);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add a pending graft request (appended to JSONB array).
     * Body: { "after": "ARCHITECT", "agentName": "security-scanner-v1" }
     */
    @PostMapping("/{id}/grafts")
    public ResponseEntity<Void> addGraft(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id,
            @RequestBody java.util.Map<String, String> body) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runRepository.findById(id)
                .map(run -> {
                    String after     = body.getOrDefault("after", "");
                    String agentName = body.getOrDefault("agentName", "");
                    // Append to existing JSON array (simple string append to JSONB stored as text)
                    String existing = run.getPendingGrafts();
                    String entry = String.format("{\"after\":\"%s\",\"agentName\":\"%s\"}", after, agentName);
                    String updated;
                    if (existing == null || existing.isBlank() || existing.equals("[]")) {
                        updated = "[" + entry + "]";
                    } else {
                        // Strip trailing ] and append
                        updated = existing.substring(0, existing.lastIndexOf(']')) + "," + entry + "]";
                    }
                    run.setPendingGrafts(updated);
                    runRepository.save(run);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private RunResponse toRunResponse(RunEntity entity) {
        List<RunResponse.ArtifactSummary> artifactSummaries = entity.getArtifacts().stream()
                .map(artifact -> new RunResponse.ArtifactSummary(
                        artifact.getId(),
                        artifact.getAgentName(),
                        artifact.getArtifactType(),
                        artifact.getCreatedAt()))
                .collect(Collectors.toList());

        return new RunResponse(
                entity.getId(),
                entity.getRepo(),
                entity.getIssueNumber(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCurrentAgent(),
                entity.getCiFixCount(),
                entity.getE2eFixCount(),
                artifactSummaries);
    }

    private String getValidatedToken(String authorization) {
        if (!StringUtils.hasText(authorization))
            return null;

        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix))
            return null;

        String token = authorization.substring(prefix.length()).trim();

        // 1. Check if it's the admin token
        if (StringUtils.hasText(props.token()) && props.token().equals(token)) {
            return null; // Return null means use default credentials (app token or properties token)
        }

        // 2. Check if it's a valid GitHub token
        if (gitHubApiClient.isValidToken(token)) {
            return token;
        }

        return null;
    }
}
