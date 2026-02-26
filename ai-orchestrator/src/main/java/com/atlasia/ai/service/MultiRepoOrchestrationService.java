package com.atlasia.ai.service;

import com.atlasia.ai.model.RepositoryGraphEntity;
import com.atlasia.ai.service.GitHubApiClient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MultiRepoOrchestrationService {
    private static final Logger log = LoggerFactory.getLogger(MultiRepoOrchestrationService.class);

    private final MultiRepoScheduler scheduler;
    private final GraftExecutionService graftExecutionService;
    private final GitHubApiClient gitHubApiClient;
    private final MonorepoWorkspaceDetector workspaceDetector;

    public MultiRepoOrchestrationService(
            MultiRepoScheduler scheduler,
            GraftExecutionService graftExecutionService,
            GitHubApiClient gitHubApiClient,
            MonorepoWorkspaceDetector workspaceDetector) {
        this.scheduler = scheduler;
        this.graftExecutionService = graftExecutionService;
        this.gitHubApiClient = gitHubApiClient;
        this.workspaceDetector = workspaceDetector;
    }

    @Transactional
    public MultiRepoWorkflowResult executeMultiRepoWorkflow(MultiRepoWorkflowRequest request) {
        log.info("Starting multi-repo workflow: sourceRepo={}, targetRepos={}, graftAgent={}",
                request.sourceRepoUrl(), request.targetRepoUrls().size(), request.agentName());

        try {
            MultiRepoScheduler.ExecutionPlan plan = scheduler.buildExecutionPlan(
                new HashSet<>(request.targetRepoUrls())
            );

            log.info("Execution plan computed: order={}, totalRepos={}",
                    plan.executionOrder(), plan.executionOrder().size());

            Map<String, GraftExecutionService.CrossRepoGraftResult> graftResults = new HashMap<>();
            List<String> errors = new ArrayList<>();

            for (String targetRepoUrl : plan.executionOrder()) {
                try {
                    log.info("Executing cross-repo graft for targetRepo={}", targetRepoUrl);

                    GraftExecutionService.CrossRepoGraftResult result = 
                        graftExecutionService.executeCrossRepoGraft(
                            request.sourceRunId(),
                            request.sourceRepoUrl(),
                            targetRepoUrl,
                            UUID.randomUUID().toString(),
                            request.agentName(),
                            request.checkpoint(),
                            request.contextData()
                        );

                    graftResults.put(targetRepoUrl, result);

                    if (!result.success()) {
                        String errorMsg = "Failed to execute graft for " + targetRepoUrl + ": " + result.errorMessage();
                        errors.add(errorMsg);
                        log.error(errorMsg);
                    }

                } catch (Exception e) {
                    String errorMsg = "Exception executing graft for " + targetRepoUrl + ": " + e.getMessage();
                    errors.add(errorMsg);
                    log.error(errorMsg, e);
                }
            }

            boolean allSuccess = errors.isEmpty();
            
            return new MultiRepoWorkflowResult(
                allSuccess,
                plan.executionOrder(),
                graftResults,
                errors
            );

        } catch (Exception e) {
            log.error("Multi-repo workflow failed: {}", e.getMessage(), e);
            return new MultiRepoWorkflowResult(
                false,
                Collections.emptyList(),
                Collections.emptyMap(),
                List.of("Workflow failed: " + e.getMessage())
            );
        }
    }

    public CoordinatedPRCreationResult createCoordinatedPullRequests(
            Map<String, PRCreationInfo> repoPrInfo) {
        
        Set<String> repoUrls = repoPrInfo.keySet();
        List<String> mergeOrder;
        
        try {
            mergeOrder = scheduler.computeMergeOrder(
                repoPrInfo.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> "placeholder"))
            );
        } catch (MultiRepoScheduler.CyclicDependencyException e) {
            log.error("Cannot create coordinated PRs due to cyclic dependencies: {}", e.getMessage());
            return new CoordinatedPRCreationResult(
                false,
                Collections.emptyMap(),
                Collections.emptyList(),
                List.of("Cyclic dependency detected: " + e.getMessage())
            );
        }

        List<PRCreationRequest> requests = new ArrayList<>();
        
        for (String repoUrl : repoUrls) {
            PRCreationInfo info = repoPrInfo.get(repoUrl);
            String[] parts = parseRepoUrl(repoUrl);
            
            if (parts == null || parts.length < 2) {
                log.warn("Invalid repo URL: {}, skipping", repoUrl);
                continue;
            }

            Optional<RepositoryGraphEntity> repoGraph = scheduler.getRepositoryGraph(repoUrl);
            List<String> dependencies = Collections.emptyList();
            
            if (repoGraph.isPresent()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = 
                        new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode depsNode = 
                        mapper.readTree(repoGraph.get().getDependencies());
                    
                    dependencies = new ArrayList<>();
                    if (depsNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode node : depsNode) {
                            dependencies.add(node.asText());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse dependencies for {}: {}", repoUrl, e.getMessage());
                }
            }

            requests.add(new PRCreationRequest(
                parts[0],
                parts[1],
                info.title(),
                info.head(),
                info.base(),
                info.body(),
                dependencies,
                info.metadata()
            ));
        }

        CoordinatedPRResult result = gitHubApiClient.createCoordinatedPullRequests(requests, mergeOrder);
        
        Map<String, Integer> prNumbers = result.results().entrySet().stream()
            .filter(e -> e.getValue().success())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().prNumber()
            ));

        return new CoordinatedPRCreationResult(
            result.allSuccess(),
            prNumbers,
            result.mergeOrder(),
            result.errors()
        );
    }

    public CoordinatedMergeResult mergeCoordinatedPullRequests(
            Map<String, Integer> repoPrMapping,
            String mergeMethod) {
        
        List<String> mergeOrder;
        
        try {
            mergeOrder = scheduler.computeMergeOrder(
                repoPrMapping.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> "placeholder"))
            );
        } catch (MultiRepoScheduler.CyclicDependencyException e) {
            log.error("Cannot merge coordinated PRs due to cyclic dependencies: {}", e.getMessage());
            return new CoordinatedMergeResult(
                false,
                Collections.emptyMap(),
                List.of("Cyclic dependency detected: " + e.getMessage())
            );
        }

        GitHubApiClient.CoordinatedMergeResult result = 
            gitHubApiClient.mergeCoordinatedPullRequests(repoPrMapping, mergeOrder, mergeMethod);

        return new CoordinatedMergeResult(
            result.allSuccess(),
            result.results().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().success()
                )),
            result.errors()
        );
    }

    @Transactional
    public void scanAndRegisterWorkspaces(List<String> repoUrls) {
        for (String repoUrl : repoUrls) {
            try {
                String[] parts = parseRepoUrl(repoUrl);
                if (parts != null && parts.length >= 2) {
                    scheduler.detectAndRegisterWorkspace(parts[0], parts[1]);
                }
            } catch (Exception e) {
                log.error("Failed to scan workspace for {}: {}", repoUrl, e.getMessage(), e);
            }
        }
    }

    private String[] parseRepoUrl(String repoUrl) {
        String normalized = repoUrl.toLowerCase()
                .replaceFirst("^https?://", "")
                .replaceFirst("\\.git$", "")
                .replaceFirst("/$", "");

        String[] parts = normalized.split("/");
        if (parts.length >= 3 && parts[0].contains("github.com")) {
            return new String[] { parts[1], parts[2] };
        } else if (parts.length >= 2) {
            return new String[] { parts[0], parts[1] };
        }
        return null;
    }

    public record MultiRepoWorkflowRequest(
        UUID sourceRunId,
        String sourceRepoUrl,
        List<String> targetRepoUrls,
        String agentName,
        String checkpoint,
        Map<String, Object> contextData
    ) {}

    public record MultiRepoWorkflowResult(
        boolean allSuccess,
        List<String> executionOrder,
        Map<String, GraftExecutionService.CrossRepoGraftResult> graftResults,
        List<String> errors
    ) {}

    public record PRCreationInfo(
        String title,
        String head,
        String base,
        String body,
        Map<String, String> metadata
    ) {}

    public record CoordinatedPRCreationResult(
        boolean allSuccess,
        Map<String, Integer> prNumbers,
        List<String> mergeOrder,
        List<String> errors
    ) {}

    public record CoordinatedMergeResult(
        boolean allSuccess,
        Map<String, Boolean> mergeResults,
        List<String> errors
    ) {}
}
