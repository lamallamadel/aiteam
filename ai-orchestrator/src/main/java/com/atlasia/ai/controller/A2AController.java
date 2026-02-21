package com.atlasia.ai.controller;

import com.atlasia.ai.api.RunRequest;
import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.A2ADiscoveryService;
import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.AgentBindingService;
import com.atlasia.ai.service.AgentBindingService.AgentBinding;
import com.atlasia.ai.service.GitHubApiClient;
import com.atlasia.ai.service.WorkflowEngine;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * A2A Controller — Agent-to-Agent Protocol Endpoints.
 *
 * Exposes the standard A2A discovery and task submission API:
 *   /.well-known/agent.json  — orchestrator's own card (A2A standard)
 *   /api/a2a/agents          — list/register/deregister agents
 *   /api/a2a/capabilities    — capability-based agent lookup
 *   /api/a2a/tasks           — A2A task submission (delegates to run creation)
 *   /api/a2a/bindings        — active binding audit
 */
@RestController
public class A2AController {

    private final A2ADiscoveryService a2aDiscoveryService;
    private final AgentBindingService agentBindingService;
    private final RunRepository runRepository;
    private final WorkflowEngine workflowEngine;
    private final OrchestratorProperties props;
    private final GitHubApiClient gitHubApiClient;

    public A2AController(
            A2ADiscoveryService a2aDiscoveryService,
            AgentBindingService agentBindingService,
            RunRepository runRepository,
            WorkflowEngine workflowEngine,
            OrchestratorProperties props,
            GitHubApiClient gitHubApiClient) {
        this.a2aDiscoveryService = a2aDiscoveryService;
        this.agentBindingService = agentBindingService;
        this.runRepository = runRepository;
        this.workflowEngine = workflowEngine;
        this.props = props;
        this.gitHubApiClient = gitHubApiClient;
    }

    // -------------------------------------------------------------------------
    // Discovery
    // -------------------------------------------------------------------------

    /** A2A standard discovery endpoint — returns orchestrator's own AgentCard. */
    @GetMapping("/.well-known/agent.json")
    public ResponseEntity<AgentCard> getOrchestratorCard() {
        return ResponseEntity.ok(a2aDiscoveryService.getOrchestratorCard());
    }

    /** List all registered agents. */
    @GetMapping("/api/a2a/agents")
    public ResponseEntity<Collection<AgentCard>> listAgents(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(a2aDiscoveryService.listAgents());
    }

    /** Get a specific agent card by name. */
    @GetMapping("/api/a2a/agents/{name}")
    public ResponseEntity<AgentCard> getAgent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("name") String name) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AgentCard card = a2aDiscoveryService.getAgent(name);
        return card != null ? ResponseEntity.ok(card) : ResponseEntity.notFound().build();
    }

    /** Register an external agent (inbound A2A registration) — requires admin token. */
    @PostMapping("/api/a2a/agents")
    public ResponseEntity<AgentCard> registerAgent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AgentCard card) {
        if (!isAdminToken(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        a2aDiscoveryService.register(card);
        return ResponseEntity.status(HttpStatus.CREATED).body(card);
    }

    /** Deregister an agent. Requires admin token. */
    @DeleteMapping("/api/a2a/agents/{name}")
    public ResponseEntity<Void> deregisterAgent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("name") String name) {
        if (!isAdminToken(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        a2aDiscoveryService.deregister(name);
        return ResponseEntity.noContent().build();
    }

    /** Find agents that provide a specific capability. */
    @GetMapping("/api/a2a/capabilities/{capability}")
    public ResponseEntity<Collection<AgentCard>> getAgentsByCapability(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("capability") String capability) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var matching = a2aDiscoveryService.discover(java.util.Set.of(capability));
        return ResponseEntity.ok(matching);
    }

    // -------------------------------------------------------------------------
    // Task Protocol
    // -------------------------------------------------------------------------

    /**
     * Submit a task via the A2A task protocol.
     * Delegates to the standard run creation pipeline.
     */
    @PostMapping("/api/a2a/tasks")
    public ResponseEntity<A2ATaskResponse> submitTask(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RunRequest request) {
        String token = getValidatedGitHubToken(authorization);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID taskId = UUID.randomUUID();
        RunEntity entity = new RunEntity(
                taskId,
                request.repo(),
                request.issueNumber(),
                request.mode(),
                RunStatus.RECEIVED,
                Instant.now());
        runRepository.save(entity);
        workflowEngine.executeWorkflowAsync(taskId, token);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new A2ATaskResponse(
                taskId.toString(), "submitted", request.repo(), request.issueNumber(), Instant.now()));
    }

    /** Get task status by taskId (maps to RunEntity). */
    @GetMapping("/api/a2a/tasks/{taskId}")
    public ResponseEntity<A2ATaskResponse> getTaskStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("taskId") UUID taskId) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runRepository.findById(taskId)
                .map(entity -> ResponseEntity.ok(new A2ATaskResponse(
                        entity.getId().toString(),
                        entity.getStatus().name().toLowerCase(),
                        entity.getRepo(),
                        entity.getIssueNumber(),
                        entity.getCreatedAt())))
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // Binding Audit
    // -------------------------------------------------------------------------

    /** List all active agent bindings. */
    @GetMapping("/api/a2a/bindings")
    public ResponseEntity<Map<UUID, AgentBinding>> listBindings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(agentBindingService.getActiveBindings());
    }

    /** Get a specific binding with its verification status. */
    @GetMapping("/api/a2a/bindings/{id}")
    public ResponseEntity<BindingVerification> getBinding(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("id") UUID bindingId) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Map<UUID, AgentBinding> bindings = agentBindingService.getActiveBindings();
        AgentBinding binding = bindings.get(bindingId);
        if (binding == null) {
            return ResponseEntity.notFound().build();
        }
        boolean valid = agentBindingService.verifyBinding(binding);
        return ResponseEntity.ok(new BindingVerification(binding, valid));
    }

    // -------------------------------------------------------------------------
    // Auth helpers
    // -------------------------------------------------------------------------

    private boolean isAuthorized(String authorization) {
        if (!StringUtils.hasText(authorization)) return false;
        if (!authorization.startsWith("Bearer ")) return false;
        String token = authorization.substring(7).trim();
        return (StringUtils.hasText(props.token()) && props.token().equals(token))
                || gitHubApiClient.isValidToken(token);
    }

    private boolean isAdminToken(String authorization) {
        if (!StringUtils.hasText(authorization)) return false;
        if (!authorization.startsWith("Bearer ")) return false;
        String token = authorization.substring(7).trim();
        return StringUtils.hasText(props.token()) && props.token().equals(token);
    }

    private String getValidatedGitHubToken(String authorization) {
        if (!StringUtils.hasText(authorization)) return null;
        if (!authorization.startsWith("Bearer ")) return null;
        String token = authorization.substring(7).trim();
        if (gitHubApiClient.isValidToken(token)) return token;
        return null;
    }

    // -------------------------------------------------------------------------
    // Response types
    // -------------------------------------------------------------------------

    public record A2ATaskResponse(
            String taskId,
            String status,
            String repo,
            int issueNumber,
            Instant createdAt) {}

    public record BindingVerification(AgentBinding binding, boolean valid) {}
}
