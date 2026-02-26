package com.atlasia.ai.contract;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.controller.A2AController;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.A2ADiscoveryService;
import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.A2ADiscoveryService.AgentConstraints;
import com.atlasia.ai.service.AgentBindingService;
import com.atlasia.ai.service.AgentBindingService.AgentBinding;
import com.atlasia.ai.service.GitHubApiClient;
import com.atlasia.ai.service.WorkflowEngine;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public abstract class A2AContractTestBase {

    @Mock
    protected A2ADiscoveryService a2aDiscoveryService;
    
    @Mock
    protected AgentBindingService agentBindingService;
    
    @Mock
    protected RunRepository runRepository;
    
    @Mock
    protected WorkflowEngine workflowEngine;
    
    @Mock
    protected OrchestratorProperties props;
    
    @Mock
    protected GitHubApiClient gitHubApiClient;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        A2AController controller = new A2AController(
            a2aDiscoveryService,
            agentBindingService,
            runRepository,
            workflowEngine,
            props,
            gitHubApiClient
        );
        
        RestAssuredMockMvc.standaloneSetup(controller);
        
        setupMocks();
    }

    protected void setupMocks() {
        when(props.token()).thenReturn("test-token");
        when(gitHubApiClient.isValidToken("github-token")).thenReturn(true);
        when(gitHubApiClient.isValidToken("test-token")).thenReturn(false);
        
        when(a2aDiscoveryService.getOrchestratorCard()).thenReturn(createOrchestratorCard());
        
        AgentCard developerCard = createDeveloperCard();
        when(a2aDiscoveryService.discover(Set.of("code_generation")))
            .thenReturn(List.of(developerCard));
        
        RunEntity mockRun = new RunEntity(
            UUID.randomUUID(),
            "owner/repository",
            42,
            "code",
            RunStatus.RECEIVED,
            Instant.parse("2024-01-15T10:30:00Z")
        );
        when(runRepository.save(any(RunEntity.class))).thenReturn(mockRun);
        doNothing().when(workflowEngine).executeWorkflowAsync(any(UUID.class), anyString());
        
        UUID bindingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        AgentBinding mockBinding = createMockBinding(bindingId);
        when(agentBindingService.getActiveBindings()).thenReturn(Map.of(bindingId, mockBinding));
        when(agentBindingService.verifyBinding(mockBinding)).thenReturn(true);
    }

    protected AgentCard createOrchestratorCard() {
        AgentConstraints constraints = new AgentConstraints(32768, 1_800_000, 1.00);
        return new AgentCard(
            "atlasia-orchestrator",
            "1.0",
            "ORCHESTRATOR",
            "atlasia",
            "Atlasia AI Orchestrator: full PM→Qualifier→Architect→Developer→Review→Tester→Writer pipeline",
            Set.of("ticket_analysis", "requirement_extraction", "system_design", "code_generation",
                   "code_review", "ci_validation", "documentation", "quality_arbitration",
                   "majority_voting", "multi_agent_pipeline"),
            "pipeline_result",
            List.of(),
            constraints,
            "http",
            "/actuator/health",
            "active"
        );
    }

    protected AgentCard createDeveloperCard() {
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        return new AgentCard(
            "developer-v1",
            "1.0",
            "DEVELOPER",
            "atlasia",
            "Developer agent: generates code, runs security review, and commits changes",
            Set.of("code_generation", "security_review", "code_quality", "multi_file_commit"),
            "code_changes",
            List.of(),
            constraints,
            "local",
            null,
            "active"
        );
    }

    protected AgentBinding createMockBinding(UUID bindingId) {
        UUID runId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        
        return new AgentBinding(
            bindingId,
            runId,
            "developer-v1",
            "DEVELOPER",
            Set.of("code_generation", "security_review"),
            Set.of("code_generation"),
            constraints,
            Instant.parse("2024-01-15T10:30:00Z"),
            Instant.parse("2024-01-15T10:35:00Z"),
            "base64-encoded-signature"
        );
    }
}
