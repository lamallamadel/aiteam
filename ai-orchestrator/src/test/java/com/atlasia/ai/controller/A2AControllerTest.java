package com.atlasia.ai.controller;

import com.atlasia.ai.api.RunRequest;
import com.atlasia.ai.config.OrchestratorProperties;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class A2AControllerTest {

        private static final String ADMIN_TOKEN = "admin-secret";
        private static final String GITHUB_TOKEN = "ghp_valid_token";
        private static final String INVALID_TOKEN = "bad-token";

        @Mock
        private A2ADiscoveryService a2aDiscoveryService;
        @Mock
        private AgentBindingService agentBindingService;
        @Mock
        private RunRepository runRepository;
        @Mock
        private WorkflowEngine workflowEngine;
        @Mock
        private OrchestratorProperties props;
        @Mock
        private GitHubApiClient gitHubApiClient;

        private MockMvc mockMvc;
        private final ObjectMapper objectMapper = new ObjectMapper();

        @BeforeEach
        void setUp() {
                A2AController controller = new A2AController(
                                a2aDiscoveryService, agentBindingService, runRepository,
                                workflowEngine, props, gitHubApiClient);
                mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

                // Admin token recognised, GitHub token NOT recognised as admin
                lenient().when(props.token()).thenReturn(ADMIN_TOKEN);
                // GitHub token is valid, admin token is NOT a GitHub token
                lenient().when(gitHubApiClient.isValidToken(GITHUB_TOKEN)).thenReturn(true);
                lenient().when(gitHubApiClient.isValidToken(ADMIN_TOKEN)).thenReturn(false);
                lenient().when(gitHubApiClient.isValidToken(INVALID_TOKEN)).thenReturn(false);
        }

        // -------------------------------------------------------------------------
        // GET /.well-known/agent.json — no auth required
        // -------------------------------------------------------------------------

        @Test
        void getOrchestratorCard_returnsOrchestratorCard() throws Exception {
                AgentCard card = orchestratorCard();
                when(a2aDiscoveryService.getOrchestratorCard()).thenReturn(card);

                mockMvc.perform(get("/.well-known/agent.json"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("atlasia-orchestrator"))
                                .andExpect(jsonPath("$.role").value("ORCHESTRATOR"));
        }

    @Test
    void getOrchestratorCard_noAuthRequired() throws Exception {
        when(a2aDiscoveryService.getOrchestratorCard()).thenReturn(orchestratorCard());

        // No Authorization header → still 200
        mockMvc.perform(get("/.well-known/agent.json"))
                .andExpect(status().isOk());
    }

        // -------------------------------------------------------------------------
        // GET /api/a2a/agents
        // -------------------------------------------------------------------------

        @Test
        void listAgents_withAdminToken_returnsAgents() throws Exception {
                Collection<AgentCard> agents = List.of(
                                agentCard("pm-v1", "PM"),
                                agentCard("tester-v1", "TESTER"));
                when(a2aDiscoveryService.listAgents()).thenReturn(agents);

                mockMvc.perform(get("/api/a2a/agents")
                                .header("Authorization", "Bearer " + ADMIN_TOKEN))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(2)));
        }

    @Test
    void listAgents_withGitHubToken_returnsAgents() throws Exception {
        when(a2aDiscoveryService.listAgents()).thenReturn(List.of(agentCard("pm-v1", "PM")));

        mockMvc.perform(get("/api/a2a/agents")
                        .header("Authorization", "Bearer " + GITHUB_TOKEN))
                .andExpect(status().isOk());
    }

        @Test
        void listAgents_withoutToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(get("/api/a2a/agents"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void listAgents_withInvalidToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(get("/api/a2a/agents")
                                .header("Authorization", "Bearer " + INVALID_TOKEN))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // GET /api/a2a/agents/{name}
        // -------------------------------------------------------------------------

    @Test
    void getAgent_existingAgent_returnsCard() throws Exception {
        when(a2aDiscoveryService.getAgent("pm-v1")).thenReturn(agentCard("pm-v1", "PM"));

        mockMvc.perform(get("/api/a2a/agents/pm-v1")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("pm-v1"))
                .andExpect(jsonPath("$.role").value("PM"));
    }

    @Test
    void getAgent_nonExistentAgent_returnsNotFound() throws Exception {
        when(a2aDiscoveryService.getAgent("missing-agent")).thenReturn(null);

        mockMvc.perform(get("/api/a2a/agents/missing-agent")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isNotFound());
    }

        @Test
        void getAgent_withoutToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(get("/api/a2a/agents/pm-v1"))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // POST /api/a2a/agents — requires admin token
        // -------------------------------------------------------------------------

        @Test
        void registerAgent_withAdminToken_createsAgent() throws Exception {
                AgentCard card = agentCard("external-v1", "EXTERNAL");
                doNothing().when(a2aDiscoveryService).register(any());

                mockMvc.perform(post("/api/a2a/agents")
                                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(card)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("external-v1"));

                verify(a2aDiscoveryService).register(any(AgentCard.class));
        }

        @Test
        void registerAgent_withGitHubToken_returnsUnauthorized() throws Exception {
                AgentCard card = agentCard("external-v1", "EXTERNAL");

                mockMvc.perform(post("/api/a2a/agents")
                                .header("Authorization", "Bearer " + GITHUB_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(card)))
                                .andExpect(status().isUnauthorized());

                verify(a2aDiscoveryService, never()).register(any());
        }

        @Test
        void registerAgent_withoutToken_returnsUnauthorized() throws Exception {
                AgentCard card = agentCard("external-v1", "EXTERNAL");

                mockMvc.perform(post("/api/a2a/agents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(card)))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // DELETE /api/a2a/agents/{name} — requires admin token
        // -------------------------------------------------------------------------

        @Test
        void deregisterAgent_withAdminToken_returnsNoContent() throws Exception {
                doNothing().when(a2aDiscoveryService).deregister("pm-v1");

                mockMvc.perform(delete("/api/a2a/agents/pm-v1")
                                .header("Authorization", "Bearer " + ADMIN_TOKEN))
                                .andExpect(status().isNoContent());

                verify(a2aDiscoveryService).deregister("pm-v1");
        }

        @Test
        void deregisterAgent_withGitHubToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(delete("/api/a2a/agents/pm-v1")
                                .header("Authorization", "Bearer " + GITHUB_TOKEN))
                                .andExpect(status().isUnauthorized());

                verify(a2aDiscoveryService, never()).deregister(anyString());
        }

        // -------------------------------------------------------------------------
        // GET /api/a2a/capabilities/{capability}
        // -------------------------------------------------------------------------

    @Test
    void getAgentsByCapability_withToken_returnsMatchingAgents() throws Exception {
        when(a2aDiscoveryService.discover(Set.of("ticket_analysis")))
                .thenReturn(List.of(agentCard("pm-v1", "PM")));

        mockMvc.perform(get("/api/a2a/capabilities/ticket_analysis")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("pm-v1"));
    }

    @Test
    void getAgentsByCapability_noMatchingAgents_returnsEmptyList() throws Exception {
        when(a2aDiscoveryService.discover(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/a2a/capabilities/unknown_capability")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

        @Test
        void getAgentsByCapability_withoutToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(get("/api/a2a/capabilities/ticket_analysis"))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // POST /api/a2a/tasks — requires GitHub token only
        // -------------------------------------------------------------------------

        @Test
        void submitTask_withGitHubToken_returnsAccepted() throws Exception {
                RunRequest request = new RunRequest("owner/repo", 42, "code", null);
                when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
                doNothing().when(workflowEngine).executeWorkflowAsync(any(), eq(GITHUB_TOKEN));

                mockMvc.perform(post("/api/a2a/tasks")
                                .header("Authorization", "Bearer " + GITHUB_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.status").value("submitted"))
                                .andExpect(jsonPath("$.repo").value("owner/repo"))
                                .andExpect(jsonPath("$.issueNumber").value(42));

                verify(runRepository).save(any(RunEntity.class));
                verify(workflowEngine).executeWorkflowAsync(any(UUID.class), eq(GITHUB_TOKEN));
        }

        @Test
        void submitTask_withAdminTokenOnly_returnsUnauthorized() throws Exception {
                // Admin token is not a GitHub token → task submission should reject it
                RunRequest request = new RunRequest("owner/repo", 42, "code", null);

                mockMvc.perform(post("/api/a2a/tasks")
                                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());

                verify(runRepository, never()).save(any());
                verify(workflowEngine, never()).executeWorkflowAsync(any(), any());
        }

        @Test
        void submitTask_withoutToken_returnsUnauthorized() throws Exception {
                RunRequest request = new RunRequest("owner/repo", 42, "code", null);

                mockMvc.perform(post("/api/a2a/tasks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // GET /api/a2a/tasks/{taskId}
        // -------------------------------------------------------------------------

        @Test
        void getTaskStatus_existingTask_returnsStatus() throws Exception {
                UUID taskId = UUID.randomUUID();
                RunEntity entity = new RunEntity(taskId, "owner/repo", 42, "code",
                                RunStatus.PM, Instant.now());
                when(runRepository.findById(taskId)).thenReturn(Optional.of(entity));

                mockMvc.perform(get("/api/a2a/tasks/" + taskId)
                                .header("Authorization", "Bearer " + ADMIN_TOKEN))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                                .andExpect(jsonPath("$.status").value("pm"))
                                .andExpect(jsonPath("$.repo").value("owner/repo"))
                                .andExpect(jsonPath("$.issueNumber").value(42));
        }

        @Test
        void getTaskStatus_nonExistentTask_returnsNotFound() throws Exception {
                UUID taskId = UUID.randomUUID();
                when(runRepository.findById(taskId)).thenReturn(Optional.empty());

                mockMvc.perform(get("/api/a2a/tasks/" + taskId)
                                .header("Authorization", "Bearer " + ADMIN_TOKEN))
                                .andExpect(status().isNotFound());
        }

        @Test
        void getTaskStatus_withoutToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(get("/api/a2a/tasks/" + UUID.randomUUID()))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // GET /api/a2a/bindings
        // -------------------------------------------------------------------------

        @Test
        void listBindings_withToken_returnsActiveBindings() throws Exception {
                UUID bindingId = UUID.randomUUID();
                AgentBinding binding = sampleBinding(bindingId);
                when(agentBindingService.getActiveBindings()).thenReturn(Map.of(bindingId, binding));

                mockMvc.perform(get("/api/a2a/bindings")
                                .header("Authorization", "Bearer " + ADMIN_TOKEN))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$." + bindingId + ".agentName").value("pm-v1"));
        }

    @Test
    void listBindings_emptyBindings_returnsEmptyMap() throws Exception {
        when(agentBindingService.getActiveBindings()).thenReturn(Map.of());

        mockMvc.perform(get("/api/a2a/bindings")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

        @Test
        void listBindings_withoutToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(get("/api/a2a/bindings"))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // GET /api/a2a/bindings/{id}
        // -------------------------------------------------------------------------

        @Test
        void getBinding_existingBinding_returnsVerification() throws Exception {
                UUID bindingId = UUID.randomUUID();
                AgentBinding binding = sampleBinding(bindingId);
                when(agentBindingService.getActiveBindings()).thenReturn(Map.of(bindingId, binding));
                when(agentBindingService.verifyBinding(binding)).thenReturn(true);

                mockMvc.perform(get("/api/a2a/bindings/" + bindingId)
                                .header("Authorization", "Bearer " + ADMIN_TOKEN))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.valid").value(true))
                                .andExpect(jsonPath("$.binding.agentName").value("pm-v1"));
        }

        @Test
        void getBinding_invalidSignature_returnsValidFalse() throws Exception {
                UUID bindingId = UUID.randomUUID();
                AgentBinding binding = sampleBinding(bindingId);
                when(agentBindingService.getActiveBindings()).thenReturn(Map.of(bindingId, binding));
                when(agentBindingService.verifyBinding(binding)).thenReturn(false);

                mockMvc.perform(get("/api/a2a/bindings/" + bindingId)
                                .header("Authorization", "Bearer " + ADMIN_TOKEN))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.valid").value(false));
        }

        @Test
        void getBinding_nonExistentBinding_returnsNotFound() throws Exception {
                UUID bindingId = UUID.randomUUID();
                when(agentBindingService.getActiveBindings()).thenReturn(Map.of());

                mockMvc.perform(get("/api/a2a/bindings/" + bindingId)
                                .header("Authorization", "Bearer " + ADMIN_TOKEN))
                                .andExpect(status().isNotFound());
        }

        @Test
        void getBinding_withoutToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(get("/api/a2a/bindings/" + UUID.randomUUID()))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // POST /api/a2a/agents/install — agent installation
        // -------------------------------------------------------------------------

        @Test
        void installAgent_withValidToken_installsAgent() throws Exception {
                AgentCard newAgent = agentCard("custom-agent", "CUSTOM");
                when(a2aDiscoveryService.getAgent("custom-agent")).thenReturn(null);

                mockMvc.perform(post("/api/a2a/agents/install")
                                .header("Authorization", "Bearer " + GITHUB_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newAgent)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.agentName").value("custom-agent"));

                verify(a2aDiscoveryService).register(any(AgentCard.class));
        }

        @Test
        void installAgent_alreadyExists_returnsConflict() throws Exception {
                AgentCard existingAgent = agentCard("pm-v1", "PM");
                when(a2aDiscoveryService.getAgent("pm-v1")).thenReturn(existingAgent);

                mockMvc.perform(post("/api/a2a/agents/install")
                                .header("Authorization", "Bearer " + GITHUB_TOKEN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(existingAgent)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.success").value(false));

                verify(a2aDiscoveryService, never()).register(any(AgentCard.class));
        }

        @Test
        void installAgent_withoutToken_returnsUnauthorized() throws Exception {
                AgentCard newAgent = agentCard("custom-agent", "CUSTOM");

                mockMvc.perform(post("/api/a2a/agents/install")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newAgent)))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // GET /api/a2a/capabilities — list all capabilities
        // -------------------------------------------------------------------------

        @Test
        void listCapabilities_withValidToken_returnsCapabilities() throws Exception {
                Set<String> capabilities = Set.of("ticket_analysis", "code_generation", "code_review");
                when(a2aDiscoveryService.listAllCapabilities()).thenReturn(capabilities);

                mockMvc.perform(get("/api/a2a/capabilities")
                                .header("Authorization", "Bearer " + GITHUB_TOKEN))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(3)))
                                .andExpect(jsonPath("$", hasItems("ticket_analysis", "code_generation", "code_review")));
        }

        @Test
        void listCapabilities_withoutToken_returnsUnauthorized() throws Exception {
                mockMvc.perform(get("/api/a2a/capabilities"))
                                .andExpect(status().isUnauthorized());
        }

        // -------------------------------------------------------------------------
        // Helpers
        // -------------------------------------------------------------------------

        private AgentCard agentCard(String name, String role) {
                AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
                return new AgentCard(name, "1.0", role, "atlasia", "Test agent",
                                Set.of("test_capability"), "output", List.of(), constraints, "local", null, "active");
        }

        private AgentCard orchestratorCard() {
                AgentConstraints constraints = new AgentConstraints(32768, 1_800_000, 1.00);
                return new AgentCard("atlasia-orchestrator", "1.0", "ORCHESTRATOR", "atlasia",
                                "Full pipeline", Set.of("ticket_analysis", "code_generation"),
                                "pipeline_result", List.of(), constraints, "http", "/actuator/health", "active");
        }

        private AgentBinding sampleBinding(UUID bindingId) {
                Instant now = Instant.now();
                return new AgentBinding(
                                bindingId, UUID.randomUUID(), "pm-v1", "PM",
                                Set.of("ticket_analysis"), Set.of("ticket_analysis"),
                                new AgentConstraints(8192, 300_000, 0.10),
                                now, now.plusSeconds(300), "sample-signature");
        }
}
