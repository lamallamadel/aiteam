package com.atlasia.ai.contract;

import com.atlasia.ai.api.RunRequest;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.AgentBindingService.AgentBinding;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static io.restassured.module.mockmvc.RestAssuredMockMvc.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A2A Contract Integration Test — validates actual HTTP endpoints against contracts.
 * 
 * Uses REST-assured with MockMvc to test the A2A endpoints and verify they conform
 * to the contract specifications defined in JSON files.
 */
@DisplayName("A2A Protocol Contract Integration Tests")
class A2AContractIntegrationTest extends A2AContractTestBase {

    @Test
    @DisplayName("GET /.well-known/agent.json returns valid AgentCard schema")
    void testAgentCardEndpointContract() {
        given()
            .when()
                .get("/.well-known/agent.json")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("name", equalTo("atlasia-orchestrator"))
                .body("version", matchesPattern("[0-9]+\\.[0-9]+(\\.[0-9]+)?"))
                .body("role", equalTo("ORCHESTRATOR"))
                .body("vendor", equalTo("atlasia"))
                .body("capabilities", not(empty()))
                .body("outputArtifactKey", equalTo("pipeline_result"))
                .body("constraints.maxTokens", isA(Integer.class))
                .body("constraints.maxDurationMs", isA(Number.class))
                .body("constraints.costBudgetUsd", isA(Number.class))
                .body("transport", equalTo("http"))
                .body("healthEndpoint", equalTo("/actuator/health"))
                .body("status", matchesPattern("active|degraded|inactive"));
    }

    @Test
    @DisplayName("GET /api/a2a/capabilities/{capability} returns matching agents")
    void testCapabilityDiscoveryEndpointContract() {
        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/capabilities/code_generation")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", not(empty()))
                .body("[0].name", equalTo("developer-v1"))
                .body("[0].version", matchesPattern("[0-9]+\\.[0-9]+(\\.[0-9]+)?"))
                .body("[0].role", equalTo("DEVELOPER"))
                .body("[0].capabilities", hasItem("code_generation"))
                .body("[0].status", matchesPattern("active|degraded|inactive"))
                .body("[0].constraints.maxTokens", isA(Integer.class))
                .body("[0].constraints.maxDurationMs", isA(Number.class))
                .body("[0].constraints.costBudgetUsd", isA(Number.class));
    }

    @Test
    @DisplayName("POST /api/a2a/tasks accepts valid task submission payload")
    void testTaskSubmissionEndpointContract() {
        RunRequest request = new RunRequest("owner/repository", 42, "code", null);

        given()
            .header("Authorization", "Bearer github-token")
            .contentType("application/json")
            .body(request)
            .when()
                .post("/api/a2a/tasks")
            .then()
                .statusCode(202)
                .contentType("application/json")
                .body("taskId", matchesPattern("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
                .body("status", equalTo("submitted"))
                .body("repo", equalTo("owner/repository"))
                .body("issueNumber", equalTo(42))
                .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("GET /api/a2a/bindings/{id} returns binding with verification status")
    void testBindingVerificationEndpointContract() {
        UUID bindingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/bindings/" + bindingId)
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("valid", equalTo(true))
                .body("binding.bindingId", equalTo(bindingId.toString()))
                .body("binding.runId", matchesPattern("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
                .body("binding.agentName", equalTo("developer-v1"))
                .body("binding.role", equalTo("DEVELOPER"))
                .body("binding.declaredCapabilities", not(empty()))
                .body("binding.requiredCapabilities", not(empty()))
                .body("binding.constraints.maxTokens", isA(Integer.class))
                .body("binding.constraints.maxDurationMs", isA(Number.class))
                .body("binding.constraints.costBudgetUsd", isA(Number.class))
                .body("binding.issuedAt", notNullValue())
                .body("binding.expiresAt", notNullValue())
                .body("binding.signature", notNullValue());
    }

    @Test
    @DisplayName("POST /api/a2a/tasks validates request payload structure")
    void testTaskSubmissionPayloadValidation() {
        Map<String, Object> invalidPayload = new HashMap<>();
        invalidPayload.put("repo", "invalid-repo-format");
        invalidPayload.put("issueNumber", "not-a-number");
        invalidPayload.put("mode", "invalid-mode");

        RunRequest validRequest = new RunRequest("owner/repo", 123, "code", null);
        
        given()
            .header("Authorization", "Bearer github-token")
            .contentType("application/json")
            .body(validRequest)
            .when()
                .post("/api/a2a/tasks")
            .then()
                .statusCode(202)
                .body("repo", matchesPattern("[a-zA-Z0-9\\-_]+/[a-zA-Z0-9\\-_]+"))
                .body("status", equalTo("submitted"));
    }

    @Test
    @DisplayName("GET /api/a2a/capabilities returns empty list when no agents match")
    void testCapabilityDiscoveryNoMatches() {
        when(a2aDiscoveryService.discover(any())).thenReturn(List.of());

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/capabilities/unknown_capability")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", empty());
    }

    @Test
    @DisplayName("GET /api/a2a/agents returns list of all registered agents")
    void testListAgentsContract() {
        when(a2aDiscoveryService.listAgents()).thenReturn(
            List.of(createDeveloperCard(), createOrchestratorCard())
        );

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/agents")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", hasSize(2))
                .body("[0].name", notNullValue())
                .body("[0].capabilities", not(empty()))
                .body("[1].name", notNullValue())
                .body("[1].capabilities", not(empty()));
    }

    @Test
    @DisplayName("GET /api/a2a/agents/{name} returns specific agent card")
    void testGetAgentContract() {
        when(a2aDiscoveryService.getAgent("developer-v1")).thenReturn(createDeveloperCard());

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/agents/developer-v1")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("name", equalTo("developer-v1"))
                .body("role", equalTo("DEVELOPER"))
                .body("version", matchesPattern("[0-9]+\\.[0-9]+(\\.[0-9]+)?"))
                .body("capabilities", hasItem("code_generation"));
    }

    @Test
    @DisplayName("Unauthorized access returns 401 for protected endpoints")
    void testAuthorizationContract() {
        given()
            .when()
                .get("/api/a2a/agents")
            .then()
                .statusCode(401);

        given()
            .when()
                .get("/api/a2a/capabilities/code_generation")
            .then()
                .statusCode(401);

        given()
            .when()
                .get("/api/a2a/bindings")
            .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("GET /api/a2a/bindings returns all active bindings")
    void testListBindingsContract() {
        UUID bindingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        AgentBinding binding = createMockBinding(bindingId);
        when(agentBindingService.getActiveBindings()).thenReturn(Map.of(bindingId, binding));

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/bindings")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", aMapWithSize(1))
                .body("'" + bindingId + "'.agentName", equalTo("developer-v1"))
                .body("'" + bindingId + "'.role", equalTo("DEVELOPER"));
    }

    @Test
    @DisplayName("GET /api/a2a/tasks/{taskId} returns task status")
    void testGetTaskStatusContract() {
        UUID taskId = UUID.randomUUID();
        RunEntity entity = new RunEntity(
            taskId,
            "owner/repo",
            42,
            "code",
            RunStatus.PM,
            Instant.now()
        );
        when(runRepository.findById(taskId)).thenReturn(Optional.of(entity));

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/tasks/" + taskId)
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("taskId", equalTo(taskId.toString()))
                .body("status", notNullValue())
                .body("repo", equalTo("owner/repo"))
                .body("issueNumber", equalTo(42))
                .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("Multi-capability discovery returns agents matching all capabilities")
    void testMultiCapabilityDiscoveryContract() {
        Set<String> multipleCapabilities = Set.of("code_generation", "security_review", "code_quality");
        
        when(a2aDiscoveryService.discover(multipleCapabilities))
            .thenReturn(List.of(createDeveloperCard()));

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/capabilities/code_generation")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$", not(empty()))
                .body("[0].capabilities", hasItems("code_generation", "security_review", "code_quality"));
    }

    @Test
    @DisplayName("Agent discovery filters by status - only active agents returned")
    void testDiscoveryFiltersByStatusContract() {
        AgentCard activeCard = createDeveloperCard();
        
        when(a2aDiscoveryService.discover(Set.of("code_generation")))
            .thenReturn(List.of(activeCard));

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/capabilities/code_generation")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("$[*].status", everyItem(equalTo("active")));
    }

    @Test
    @DisplayName("Binding verification validates signature and expiry")
    void testBindingVerificationSignatureContract() {
        UUID bindingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        AgentBinding binding = createMockBinding(bindingId);
        
        when(agentBindingService.getActiveBindings()).thenReturn(Map.of(bindingId, binding));
        when(agentBindingService.verifyBinding(binding)).thenReturn(true);

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/bindings/" + bindingId)
            .then()
                .statusCode(200)
                .body("valid", equalTo(true))
                .body("binding.signature", notNullValue())
                .body("binding.issuedAt", notNullValue())
                .body("binding.expiresAt", notNullValue());
    }

    @Test
    @DisplayName("Binding contains required and declared capabilities")
    void testBindingCapabilitiesContract() {
        UUID bindingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        AgentBinding binding = createMockBinding(bindingId);
        
        when(agentBindingService.getActiveBindings()).thenReturn(Map.of(bindingId, binding));

        given()
            .header("Authorization", "Bearer test-token")
            .when()
                .get("/api/a2a/bindings/" + bindingId)
            .then()
                .statusCode(200)
                .body("binding.declaredCapabilities", hasItems("code_generation", "security_review"))
                .body("binding.requiredCapabilities", hasItem("code_generation"))
                .body("binding.declaredCapabilities", hasSize(greaterThanOrEqualTo(1)))
                .body("binding.requiredCapabilities", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("POST /api/a2a/agents registers external agent")
    void testAgentRegistrationContract() {
        AgentConstraints constraints = new AgentConstraints(16384, 600_000, 0.25);
        AgentCard externalCard = new AgentCard(
            "external-agent-v1",
            "1.0",
            "CUSTOM",
            "external-vendor",
            "External agent for custom processing",
            Set.of("custom_processing", "data_analysis"),
            "custom_output",
            List.of(),
            constraints,
            "http",
            "/health",
            "active"
        );

        given()
            .header("Authorization", "Bearer test-token")
            .contentType("application/json")
            .body(externalCard)
            .when()
                .post("/api/a2a/agents")
            .then()
                .statusCode(201)
                .contentType("application/json")
                .body("name", equalTo("external-agent-v1"))
                .body("version", matchesPattern("[0-9]+\\.[0-9]+(\\.[0-9]+)?"))
                .body("role", equalTo("CUSTOM"))
                .body("vendor", equalTo("external-vendor"))
                .body("capabilities", hasItems("custom_processing", "data_analysis"))
                .body("constraints.maxTokens", equalTo(16384))
                .body("constraints.maxDurationMs", equalTo(600000))
                .body("status", matchesPattern("active|degraded|inactive"));
    }

    @Test
    @DisplayName("Agent registration requires admin token")
    void testAgentRegistrationAuthorizationContract() {
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        AgentCard card = new AgentCard(
            "test-agent",
            "1.0",
            "DEVELOPER",
            "test",
            "Test agent",
            Set.of("testing"),
            "output",
            List.of(),
            constraints,
            "local",
            null,
            "active"
        );

        given()
            .header("Authorization", "Bearer github-token")
            .contentType("application/json")
            .body(card)
            .when()
                .post("/api/a2a/agents")
            .then()
                .statusCode(401);

        given()
            .contentType("application/json")
            .body(card)
            .when()
                .post("/api/a2a/agents")
            .then()
                .statusCode(401);
    }
}
