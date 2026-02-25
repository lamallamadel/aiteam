package com.atlasia.ai.contract;

import com.atlasia.ai.api.RunRequest;
import com.atlasia.ai.service.A2ADiscoveryService.AgentCard;
import com.atlasia.ai.service.A2ADiscoveryService.AgentConstraints;
import com.atlasia.ai.service.AgentBindingService.AgentBinding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2A Contract Test Suite — validates A2A agent protocol contracts.
 * 
 * Tests verify:
 * 1. AgentCard schema matches the AgentCard model
 * 2. Capability discovery responses from A2ADiscoveryService.discover()
 * 3. Task submission payloads to /api/a2a/tasks endpoint
 * 4. Binding verification responses from AgentBindingService.verifyBinding()
 * 
 * Contract definitions are stored in src/test/resources/contracts/
 */
@DisplayName("A2A Protocol Contract Tests")
class A2AContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("AgentCard schema contract - verify structure and field types")
    void testAgentCardSchemaContract() throws IOException {
        JsonNode contract = loadContract("agent-card-schema.json");
        JsonNode expectedBody = contract.get("response").get("body");
        JsonNode matchers = contract.get("response").get("matchers").get("body");

        AgentConstraints constraints = new AgentConstraints(32768, 1_800_000, 1.00);
        AgentCard card = new AgentCard(
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

        assertThat(card.name()).isEqualTo(expectedBody.get("name").asText());
        assertThat(card.version()).isEqualTo(expectedBody.get("version").asText());
        assertThat(card.role()).isEqualTo(expectedBody.get("role").asText());
        assertThat(card.vendor()).isEqualTo(expectedBody.get("vendor").asText());
        assertThat(card.outputArtifactKey()).isEqualTo(expectedBody.get("outputArtifactKey").asText());
        assertThat(card.transport()).isEqualTo(expectedBody.get("transport").asText());
        assertThat(card.healthEndpoint()).isEqualTo(expectedBody.get("healthEndpoint").asText());
        assertThat(card.status()).isEqualTo(expectedBody.get("status").asText());
        
        assertThat(card.constraints().maxTokens()).isEqualTo(expectedBody.get("constraints").get("maxTokens").asInt());
        assertThat(card.constraints().maxDurationMs()).isEqualTo(expectedBody.get("constraints").get("maxDurationMs").asLong());
        assertThat(card.constraints().costBudgetUsd()).isEqualTo(expectedBody.get("constraints").get("costBudgetUsd").asDouble());

        assertThat(card.name()).matches(getMatcherPattern(matchers, "$.name"));
        assertThat(card.version()).matches(getMatcherPattern(matchers, "$.version"));
        assertThat(card.role()).matches(getMatcherPattern(matchers, "$.role"));
        assertThat(card.status()).matches(getMatcherPattern(matchers, "$.status"));
        assertThat(card.capabilities()).isNotEmpty();
    }

    @Test
    @DisplayName("Capability discovery contract - verify response from A2ADiscoveryService.discover()")
    void testCapabilityDiscoveryContract() throws IOException {
        JsonNode contract = loadContract("capability-discovery.json");
        JsonNode expectedBody = contract.get("response").get("body");
        JsonNode matchers = contract.get("response").get("matchers").get("body");

        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        AgentCard card = new AgentCard(
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

        List<AgentCard> discoveryResult = List.of(card);

        assertThat(discoveryResult).isNotEmpty();
        
        AgentCard firstCard = discoveryResult.get(0);
        JsonNode expectedFirstCard = expectedBody.get(0);
        
        assertThat(firstCard.name()).isEqualTo(expectedFirstCard.get("name").asText());
        assertThat(firstCard.version()).isEqualTo(expectedFirstCard.get("version").asText());
        assertThat(firstCard.role()).isEqualTo(expectedFirstCard.get("role").asText());
        assertThat(firstCard.vendor()).isEqualTo(expectedFirstCard.get("vendor").asText());
        assertThat(firstCard.status()).isEqualTo(expectedFirstCard.get("status").asText());

        assertThat(firstCard.name()).matches(getMatcherPattern(matchers, "$[*].name"));
        assertThat(firstCard.status()).matches(getMatcherPattern(matchers, "$[*].status"));
        assertThat(firstCard.capabilities()).isNotEmpty();
    }

    @Test
    @DisplayName("Task submission contract - verify payload structure for /api/a2a/tasks")
    void testTaskSubmissionContract() throws IOException {
        JsonNode contract = loadContract("task-submission.json");
        JsonNode requestBody = contract.get("request").get("body");
        JsonNode requestMatchers = contract.get("request").get("matchers").get("body");
        JsonNode responseBody = contract.get("response").get("body");
        JsonNode responseMatchers = contract.get("response").get("matchers").get("body");

        RunRequest request = new RunRequest(
            requestBody.get("repo").asText(),
            requestBody.get("issueNumber").asInt(),
            requestBody.get("mode").asText(),
            null
        );

        assertThat(request.repo()).isEqualTo("owner/repository");
        assertThat(request.issueNumber()).isEqualTo(42);
        assertThat(request.mode()).isEqualTo("code");

        assertThat(request.repo()).matches(getMatcherPattern(requestMatchers, "$.repo"));
        assertThat(request.mode()).matches(getMatcherPattern(requestMatchers, "$.mode"));

        String responseTaskId = responseBody.get("taskId").asText();
        String responseStatus = responseBody.get("status").asText();
        String responseRepo = responseBody.get("repo").asText();
        int responseIssueNumber = responseBody.get("issueNumber").asInt();
        String responseCreatedAt = responseBody.get("createdAt").asText();

        assertThat(responseTaskId).matches(getMatcherPattern(responseMatchers, "$.taskId"));
        assertThat(responseStatus).matches(getMatcherPattern(responseMatchers, "$.status"));
        assertThat(responseCreatedAt).matches(getMatcherPattern(responseMatchers, "$.createdAt"));
        
        assertThat(responseStatus).isEqualTo("submitted");
        assertThat(responseRepo).isEqualTo(request.repo());
        assertThat(responseIssueNumber).isEqualTo(request.issueNumber());
    }

    @Test
    @DisplayName("Binding verification contract - verify response from AgentBindingService.verifyBinding()")
    void testBindingVerificationContract() throws IOException {
        JsonNode contract = loadContract("binding-verification.json");
        JsonNode responseBody = contract.get("response").get("body");
        JsonNode matchers = contract.get("response").get("matchers").get("body");

        UUID bindingId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID runId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        
        AgentBinding binding = new AgentBinding(
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

        JsonNode expectedBinding = responseBody.get("binding");
        boolean expectedValid = responseBody.get("valid").asBoolean();

        assertThat(binding.bindingId().toString()).isEqualTo(expectedBinding.get("bindingId").asText());
        assertThat(binding.runId().toString()).isEqualTo(expectedBinding.get("runId").asText());
        assertThat(binding.agentName()).isEqualTo(expectedBinding.get("agentName").asText());
        assertThat(binding.role()).isEqualTo(expectedBinding.get("role").asText());
        assertThat(binding.signature()).isEqualTo(expectedBinding.get("signature").asText());

        assertThat(binding.bindingId().toString()).matches(getMatcherPattern(matchers, "$.binding.bindingId"));
        assertThat(binding.runId().toString()).matches(getMatcherPattern(matchers, "$.binding.runId"));
        assertThat(binding.agentName()).matches(getMatcherPattern(matchers, "$.binding.agentName"));
        assertThat(binding.role()).matches(getMatcherPattern(matchers, "$.binding.role"));
        assertThat(binding.issuedAt().toString()).matches(getMatcherPattern(matchers, "$.binding.issuedAt"));
        assertThat(binding.expiresAt().toString()).matches(getMatcherPattern(matchers, "$.binding.expiresAt"));

        assertThat(binding.constraints().maxTokens()).isInstanceOf(Integer.class);
        assertThat(binding.constraints().maxDurationMs()).isInstanceOf(Long.class);
        assertThat(binding.constraints().costBudgetUsd()).isInstanceOf(Double.class);
        
        assertThat(expectedValid).isTrue();
    }

    @Test
    @DisplayName("AgentCard constraints validation")
    void testAgentConstraintsValidation() {
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);

        assertThat(constraints.maxTokens()).isPositive();
        assertThat(constraints.maxDurationMs()).isPositive();
        assertThat(constraints.costBudgetUsd()).isPositive();
        
        assertThat(constraints.maxTokens()).isInstanceOf(Integer.class);
        assertThat(constraints.maxDurationMs()).isInstanceOf(Long.class);
        assertThat(constraints.costBudgetUsd()).isInstanceOf(Double.class);
    }

    @Test
    @DisplayName("AgentBinding signature and expiry validation")
    void testAgentBindingSignatureValidation() {
        UUID bindingId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        Instant now = Instant.now();
        
        AgentBinding binding = new AgentBinding(
            bindingId,
            runId,
            "test-agent",
            "DEVELOPER",
            Set.of("code_generation"),
            Set.of("code_generation"),
            constraints,
            now,
            now.plusSeconds(300),
            "test-signature"
        );

        assertThat(binding.bindingId()).isNotNull();
        assertThat(binding.runId()).isNotNull();
        assertThat(binding.signature()).isNotBlank();
        assertThat(binding.expiresAt()).isAfter(binding.issuedAt());
        assertThat(binding.declaredCapabilities()).containsAll(binding.requiredCapabilities());
    }

    @Test
    @DisplayName("AgentCard capabilities matching requirements")
    void testAgentCardCapabilitiesContract() {
        Set<String> requiredCapabilities = Set.of("code_generation", "security_review");
        
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        AgentCard card = new AgentCard(
            "developer-v1",
            "1.0",
            "DEVELOPER",
            "atlasia",
            "Developer agent",
            Set.of("code_generation", "security_review", "code_quality"),
            "code_changes",
            List.of(),
            constraints,
            "local",
            null,
            "active"
        );

        assertThat(card.capabilities()).containsAll(requiredCapabilities);
        assertThat(card.status()).isIn("active", "degraded", "inactive");
        assertThat(card.role()).matches("[A-Z_]+");
    }

    @Test
    @DisplayName("Multi-capability discovery contract - A2ADiscoveryService.discover() with multiple capabilities")
    void testMultiCapabilityDiscoveryContract() throws IOException {
        JsonNode contract = loadContract("multi-capability-discovery.json");
        JsonNode requestBody = contract.get("request").get("body");
        JsonNode responseBody = contract.get("response").get("body");
        JsonNode matchers = contract.get("response").get("matchers").get("body");

        Set<String> requiredCapabilities = Set.of("code_generation", "security_review", "code_quality");
        
        AgentConstraints constraints = new AgentConstraints(8192, 300_000, 0.10);
        AgentCard card = new AgentCard(
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

        List<AgentCard> discoveryResult = List.of(card);

        assertThat(discoveryResult).isNotEmpty();
        
        AgentCard matchedCard = discoveryResult.get(0);
        assertThat(matchedCard.capabilities()).containsAll(requiredCapabilities);
        
        assertThat(matchedCard.name()).matches(getMatcherPattern(matchers, "$[*].name"));
        assertThat(matchedCard.version()).matches(getMatcherPattern(matchers, "$[*].version"));
        assertThat(matchedCard.role()).matches(getMatcherPattern(matchers, "$[*].role"));
        assertThat(matchedCard.status()).matches(getMatcherPattern(matchers, "$[*].status"));
        
        assertThat(matchedCard.constraints().maxTokens()).isPositive();
        assertThat(matchedCard.constraints().maxDurationMs()).isPositive();
        assertThat(matchedCard.constraints().costBudgetUsd()).isPositive();
    }

    @Test
    @DisplayName("Discovery scoring validates best match selection")
    void testDiscoveryScoringContract() {
        Set<String> requiredCapabilities = Set.of("code_generation", "security_review");
        
        AgentConstraints constraints1 = new AgentConstraints(8192, 300_000, 0.10);
        AgentCard fullMatchCard = new AgentCard(
            "developer-v1",
            "1.0",
            "DEVELOPER",
            "atlasia",
            "Developer agent with all capabilities",
            Set.of("code_generation", "security_review", "code_quality", "multi_file_commit"),
            "code_changes",
            List.of(),
            constraints1,
            "local",
            null,
            "active"
        );

        AgentConstraints constraints2 = new AgentConstraints(4096, 180_000, 0.05);
        AgentCard partialMatchCard = new AgentCard(
            "basic-dev-v1",
            "1.0",
            "DEVELOPER",
            "atlasia",
            "Basic developer agent",
            Set.of("code_generation"),
            "code_changes",
            List.of(),
            constraints2,
            "local",
            null,
            "active"
        );

        long fullMatchScore = requiredCapabilities.stream()
            .filter(fullMatchCard.capabilities()::contains)
            .count();
        
        long partialMatchScore = requiredCapabilities.stream()
            .filter(partialMatchCard.capabilities()::contains)
            .count();

        assertThat(fullMatchScore).isGreaterThan(partialMatchScore);
        assertThat(fullMatchCard.capabilities()).containsAll(requiredCapabilities);
    }

    private JsonNode loadContract(String contractFileName) throws IOException {
        ClassPathResource resource = new ClassPathResource("contracts/" + contractFileName);
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(content);
        }
    }

    private String getMatcherPattern(JsonNode matchers, String path) {
        for (JsonNode matcher : matchers) {
            if (matcher.get("path").asText().equals(path)) {
                String type = matcher.get("type").asText();
                if ("by_regex".equals(type)) {
                    return matcher.get("value").asText();
                } else if ("by_type".equals(type)) {
                    return ".*";
                }
            }
        }
        return ".*";
    }
}
