package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.CircuitBreakerStatusDto;
import com.atlasia.ai.api.dto.GraftExecutionDto;
import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.config.RequiresPermission;
import com.atlasia.ai.model.GraftExecutionEntity;
import com.atlasia.ai.persistence.GraftExecutionRepository;
import com.atlasia.ai.service.A2ADiscoveryService;
import com.atlasia.ai.service.GitHubApiClient;
import com.atlasia.ai.service.GraftExecutionService;
import com.atlasia.ai.service.RoleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/grafts")
@Validated
public class GraftController {

    private final GraftExecutionRepository graftExecutionRepository;
    private final GraftExecutionService graftExecutionService;
    private final A2ADiscoveryService a2aDiscoveryService;
    private final OrchestratorProperties props;
    private final GitHubApiClient gitHubApiClient;

    public GraftController(
            GraftExecutionRepository graftExecutionRepository,
            GraftExecutionService graftExecutionService,
            A2ADiscoveryService a2aDiscoveryService,
            OrchestratorProperties props,
            GitHubApiClient gitHubApiClient) {
        this.graftExecutionRepository = graftExecutionRepository;
        this.graftExecutionService = graftExecutionService;
        this.a2aDiscoveryService = a2aDiscoveryService;
        this.props = props;
        this.gitHubApiClient = gitHubApiClient;
    }

    @GetMapping("/executions")
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_GRAFT, action = RoleService.ACTION_VIEW)
    public ResponseEntity<List<GraftExecutionDto>> getAllExecutions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "runId", required = false) UUID runId,
            @RequestParam(value = "agentName", required = false) String agentName,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<GraftExecutionEntity> executions;
        if (runId != null) {
            executions = graftExecutionRepository.findByRunId(runId);
        } else if (agentName != null) {
            executions = graftExecutionRepository.findByAgentName(agentName);
        } else {
            executions = graftExecutionRepository.findAll();
        }

        List<GraftExecutionDto> dtos = executions.stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/executions/{id}")
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_GRAFT, action = RoleService.ACTION_VIEW)
    public ResponseEntity<GraftExecutionDto> getExecution(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID id) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return graftExecutionRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/circuit-breaker/status")
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_GRAFT, action = RoleService.ACTION_VIEW)
    public ResponseEntity<List<CircuitBreakerStatusDto>> getCircuitBreakerStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "agentName", required = false) String agentName) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<String> agentNames;
        if (agentName != null) {
            agentNames = List.of(agentName);
        } else {
            agentNames = a2aDiscoveryService.listAllAgents().stream()
                    .map(A2ADiscoveryService.AgentCard::name)
                    .collect(Collectors.toList());
        }

        List<CircuitBreakerStatusDto> statuses = agentNames.stream()
                .map(this::buildCircuitBreakerStatus)
                .collect(Collectors.toList());

        return ResponseEntity.ok(statuses);
    }

    @PostMapping("/circuit-breaker/{agentName}/reset")
    @PreAuthorize("hasRole('WORKFLOW_MANAGER')")
    @RequiresPermission(resource = RoleService.RESOURCE_GRAFT, action = RoleService.ACTION_MANAGE)
    public ResponseEntity<Void> resetCircuitBreaker(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("agentName") String agentName) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        graftExecutionService.resetCircuitBreaker(agentName);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/agents")
    @PreAuthorize("hasRole('USER')")
    @RequiresPermission(resource = RoleService.RESOURCE_GRAFT, action = RoleService.ACTION_VIEW)
    public ResponseEntity<List<String>> getAvailableAgents(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (getValidatedToken(authorization) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<String> agents = a2aDiscoveryService.listAllAgents().stream()
                .map(A2ADiscoveryService.AgentCard::name)
                .sorted()
                .collect(Collectors.toList());

        return ResponseEntity.ok(agents);
    }

    private GraftExecutionDto toDto(GraftExecutionEntity entity) {
        return new GraftExecutionDto(
                entity.getId(),
                entity.getRunId(),
                entity.getGraftId(),
                entity.getAgentName(),
                entity.getCheckpointAfter(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getStatus().name(),
                entity.getOutputArtifactId(),
                entity.getErrorMessage(),
                entity.getRetryCount(),
                entity.getTimeoutMs(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private CircuitBreakerStatusDto buildCircuitBreakerStatus(String agentName) {
        Instant since = Instant.now().minus(Duration.ofMinutes(5));
        List<GraftExecutionEntity> recentFailures = 
            graftExecutionRepository.findRecentFailuresByAgent(agentName, since);
        
        long successCount = graftExecutionRepository.countSuccessfulExecutionsByAgent(agentName);
        long failureCount = graftExecutionRepository.countFailedExecutionsByAgent(agentName);
        
        String state = graftExecutionService.getCircuitBreakerState(agentName);
        int currentFailureCount = graftExecutionService.getCircuitBreakerFailureCount(agentName);
        Instant lastFailureTime = graftExecutionService.getCircuitBreakerLastFailureTime(agentName);
        
        double failureRate = (successCount + failureCount) > 0 
            ? (double) failureCount / (successCount + failureCount) 
            : 0.0;

        List<CircuitBreakerStatusDto.FailureRecord> failureRecords = recentFailures.stream()
                .map(f -> new CircuitBreakerStatusDto.FailureRecord(
                        f.getGraftId(),
                        f.getStartedAt(),
                        f.getErrorMessage()
                ))
                .collect(Collectors.toList());

        return new CircuitBreakerStatusDto(
                agentName,
                state,
                currentFailureCount,
                lastFailureTime,
                successCount,
                failureCount,
                failureRecords,
                failureRate
        );
    }

    private String getValidatedToken(String authorization) {
        if (!StringUtils.hasText(authorization))
            return null;

        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix))
            return null;

        String token = authorization.substring(prefix.length()).trim();

        if (StringUtils.hasText(props.token()) && props.token().equals(token)) {
            return null;
        }

        if (gitHubApiClient.isValidToken(token)) {
            return token;
        }

        return null;
    }
}
