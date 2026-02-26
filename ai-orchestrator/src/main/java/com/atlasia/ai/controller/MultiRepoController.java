package com.atlasia.ai.controller;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.config.RequiresPermission;
import com.atlasia.ai.model.RepositoryGraphEntity;
import com.atlasia.ai.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.*;

@RestController
@RequestMapping("/api/multi-repo")
@Validated
public class MultiRepoController {

    private final MultiRepoOrchestrationService orchestrationService;
    private final MultiRepoScheduler scheduler;
    private final OrchestratorProperties props;
    private final GitHubApiClient gitHubApiClient;

    public MultiRepoController(
            MultiRepoOrchestrationService orchestrationService,
            MultiRepoScheduler scheduler,
            OrchestratorProperties props,
            GitHubApiClient gitHubApiClient) {
        this.orchestrationService = orchestrationService;
        this.scheduler = scheduler;
        this.props = props;
        this.gitHubApiClient = gitHubApiClient;
    }

    @PostMapping("/repositories/register")
    @PreAuthorize("hasRole('WORKFLOW_MANAGER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_MANAGE)
    public ResponseEntity<RegistrationResponse> registerRepository(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RepositoryRegistrationRequest request) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            scheduler.registerRepository(request.repoUrl(), request.dependencies());
            return ResponseEntity.ok(new RegistrationResponse(true, "Repository registered successfully", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new RegistrationResponse(false, null, e.getMessage()));
        }
    }

    @PostMapping("/workspaces/detect")
    @PreAuthorize("hasRole('WORKFLOW_MANAGER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_MANAGE)
    public ResponseEntity<WorkspaceDetectionResponse> detectWorkspaces(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody WorkspaceDetectionRequest request) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            orchestrationService.scanAndRegisterWorkspaces(request.repoUrls());
            
            List<WorkspaceInfo> workspaces = new ArrayList<>();
            for (String repoUrl : request.repoUrls()) {
                Optional<RepositoryGraphEntity> entity = scheduler.getRepositoryGraph(repoUrl);
                if (entity.isPresent() && entity.get().getWorkspaceType() != null) {
                    workspaces.add(new WorkspaceInfo(
                        repoUrl,
                        entity.get().getWorkspaceType(),
                        entity.get().getWorkspaceConfig()
                    ));
                }
            }

            return ResponseEntity.ok(new WorkspaceDetectionResponse(true, workspaces, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new WorkspaceDetectionResponse(false, null, e.getMessage()));
        }
    }

    @GetMapping("/repositories")
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_VIEW)
    public ResponseEntity<List<RepositoryInfo>> listRepositories(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<RepositoryGraphEntity> entities = scheduler.getAllRepositories();
        List<RepositoryInfo> repositories = entities.stream()
                .map(e -> new RepositoryInfo(
                    e.getRepoUrl(),
                    e.getDependencies(),
                    e.getWorkspaceType(),
                    e.getWorkspaceConfig()
                ))
                .toList();

        return ResponseEntity.ok(repositories);
    }

    @PostMapping("/workflows/execute")
    @PreAuthorize("hasRole('WORKFLOW_MANAGER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_CREATE)
    public ResponseEntity<MultiRepoWorkflowResponse> executeWorkflow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody MultiRepoWorkflowRequestDto request) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MultiRepoOrchestrationService.MultiRepoWorkflowRequest workflowRequest =
                    new MultiRepoOrchestrationService.MultiRepoWorkflowRequest(
                            request.sourceRunId(),
                            request.sourceRepoUrl(),
                            request.targetRepoUrls(),
                            request.agentName(),
                            request.checkpoint(),
                            request.contextData() != null ? request.contextData() : Map.of()
                    );

            MultiRepoOrchestrationService.MultiRepoWorkflowResult result =
                    orchestrationService.executeMultiRepoWorkflow(workflowRequest);

            return ResponseEntity.ok(new MultiRepoWorkflowResponse(
                    result.allSuccess(),
                    result.executionOrder(),
                    result.graftResults().entrySet().stream()
                            .collect(HashMap::new,
                                    (map, entry) -> map.put(entry.getKey(), new GraftResultDto(
                                            entry.getValue().success(),
                                            entry.getValue().targetRunId(),
                                            entry.getValue().artifactId(),
                                            entry.getValue().errorMessage()
                                    )),
                                    HashMap::putAll),
                    result.errors()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MultiRepoWorkflowResponse(false, Collections.emptyList(),
                            Collections.emptyMap(), List.of(e.getMessage())));
        }
    }

    @PostMapping("/pull-requests/create")
    @PreAuthorize("hasRole('WORKFLOW_MANAGER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_CREATE)
    public ResponseEntity<CoordinatedPRResponse> createCoordinatedPRs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CoordinatedPRRequest request) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Map<String, MultiRepoOrchestrationService.PRCreationInfo> prInfo = new HashMap<>();
            for (Map.Entry<String, PRInfoDto> entry : request.pullRequests().entrySet()) {
                PRInfoDto dto = entry.getValue();
                prInfo.put(entry.getKey(), new MultiRepoOrchestrationService.PRCreationInfo(
                        dto.title(),
                        dto.head(),
                        dto.base(),
                        dto.body(),
                        dto.metadata()
                ));
            }

            MultiRepoOrchestrationService.CoordinatedPRCreationResult result =
                    orchestrationService.createCoordinatedPullRequests(prInfo);

            return ResponseEntity.ok(new CoordinatedPRResponse(
                    result.allSuccess(),
                    result.prNumbers(),
                    result.mergeOrder(),
                    result.errors()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new CoordinatedPRResponse(false, Collections.emptyMap(),
                            Collections.emptyList(), List.of(e.getMessage())));
        }
    }

    @PostMapping("/pull-requests/merge")
    @PreAuthorize("hasRole('WORKFLOW_MANAGER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_CREATE)
    public ResponseEntity<CoordinatedMergeResponse> mergeCoordinatedPRs(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CoordinatedMergeRequest request) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MultiRepoOrchestrationService.CoordinatedMergeResult result =
                    orchestrationService.mergeCoordinatedPullRequests(
                            request.repoPrMapping(),
                            request.mergeMethod() != null ? request.mergeMethod() : "merge"
                    );

            return ResponseEntity.ok(new CoordinatedMergeResponse(
                    result.allSuccess(),
                    result.mergeResults(),
                    result.errors()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new CoordinatedMergeResponse(false, Collections.emptyMap(),
                            List.of(e.getMessage())));
        }
    }

    @GetMapping("/repositories/{repoUrl}/downstream")
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_VIEW)
    public ResponseEntity<List<String>> getDownstreamRepositories(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("repoUrl") String repoUrl) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<String> downstream = scheduler.getDownstreamRepositories(repoUrl).stream()
                .map(RepositoryGraphEntity::getRepoUrl)
                .toList();

        return ResponseEntity.ok(downstream);
    }

    @PostMapping("/execution-plan")
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_RUN, action = RoleService.ACTION_VIEW)
    public ResponseEntity<ExecutionPlanResponse> computeExecutionPlan(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ExecutionPlanRequest request) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            MultiRepoScheduler.ExecutionPlan plan = scheduler.buildExecutionPlan(
                    new HashSet<>(request.repoUrls())
            );

            return ResponseEntity.ok(new ExecutionPlanResponse(
                    true,
                    plan.executionOrder(),
                    plan.dependencies(),
                    null
            ));
        } catch (MultiRepoScheduler.CyclicDependencyException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ExecutionPlanResponse(false, Collections.emptyList(),
                            Collections.emptyMap(), e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ExecutionPlanResponse(false, Collections.emptyList(),
                            Collections.emptyMap(), e.getMessage()));
        }
    }

    private String getValidatedToken(String authorization) {
        if (!StringUtils.hasText(authorization))
            return null;

        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix))
            return null;

        String token = authorization.substring(prefix.length()).trim();

        if (StringUtils.hasText(props.token()) && props.token().equals(token)) {
            return token;
        }

        if (gitHubApiClient.isValidToken(token)) {
            return token;
        }

        return null;
    }

    public record RepositoryRegistrationRequest(
            @NotBlank String repoUrl,
            @NotEmpty List<String> dependencies
    ) {}

    public record RegistrationResponse(boolean success, String message, String error) {}

    public record WorkspaceDetectionRequest(@NotEmpty List<String> repoUrls) {}

    public record WorkspaceInfo(String repoUrl, String workspaceType, String workspaceConfig) {}

    public record WorkspaceDetectionResponse(boolean success, List<WorkspaceInfo> workspaces, String error) {}

    public record RepositoryInfo(String repoUrl, String dependencies, String workspaceType, String workspaceConfig) {}

    public record MultiRepoWorkflowRequestDto(
            @NotBlank UUID sourceRunId,
            @NotBlank String sourceRepoUrl,
            @NotEmpty List<String> targetRepoUrls,
            @NotBlank String agentName,
            @NotBlank String checkpoint,
            Map<String, Object> contextData
    ) {}

    public record GraftResultDto(boolean success, UUID targetRunId, UUID artifactId, String errorMessage) {}

    public record MultiRepoWorkflowResponse(
            boolean allSuccess,
            List<String> executionOrder,
            Map<String, GraftResultDto> graftResults,
            List<String> errors
    ) {}

    public record PRInfoDto(
            @NotBlank String title,
            @NotBlank String head,
            @NotBlank String base,
            @NotBlank String body,
            Map<String, String> metadata
    ) {}

    public record CoordinatedPRRequest(@NotEmpty Map<String, PRInfoDto> pullRequests) {}

    public record CoordinatedPRResponse(
            boolean allSuccess,
            Map<String, Integer> prNumbers,
            List<String> mergeOrder,
            List<String> errors
    ) {}

    public record CoordinatedMergeRequest(
            @NotEmpty Map<String, Integer> repoPrMapping,
            String mergeMethod
    ) {}

    public record CoordinatedMergeResponse(
            boolean allSuccess,
            Map<String, Boolean> mergeResults,
            List<String> errors
    ) {}

    public record ExecutionPlanRequest(@NotEmpty List<String> repoUrls) {}

    public record ExecutionPlanResponse(
            boolean success,
            List<String> executionOrder,
            Map<String, List<String>> dependencies,
            String error
    ) {}
}
