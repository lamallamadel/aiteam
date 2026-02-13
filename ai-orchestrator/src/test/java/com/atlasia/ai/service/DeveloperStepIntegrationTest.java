package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.service.exception.AgentStepException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
        "atlasia.orchestrator.repo-allowlist=src/,docs/,pom.xml",
        "atlasia.orchestrator.workflow-protect-prefix=.github/workflows/"
})
class DeveloperStepIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrchestratorProperties properties;

    @MockBean
    private GitHubApiClient gitHubApiClient;

    @MockBean
    private LlmService llmService;

    private DeveloperStep developerStep;
    private RunContext context;
    private RunEntity runEntity;

    @BeforeEach
    void setUp() {
        developerStep = new DeveloperStep(gitHubApiClient, llmService, objectMapper, properties);

        runEntity = new RunEntity(
                UUID.randomUUID(),
                "test-owner/test-repo",
                456,
                "full",
                RunStatus.DEVELOPER,
                Instant.now());

        context = new RunContext(runEntity, "test-owner", "test-repo");
        context.setBranchName("ai/issue-456");

        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Implement payment processing");
        issueData.put("body", "Add payment gateway integration with validation and error handling");
        context.setIssueData(issueData);

        context.setArchitectureNotes("""
                # Architecture Notes

                Use service layer pattern with proper validation.
                Implement idempotency for payment operations.
                Add comprehensive error handling and logging.
                """);
    }

    @Test
    void testEndToEndCodeGeneration() throws Exception {
        setupMocksForSuccessfulFlow();

        String llmResponse = """
                {
                    "summary": "Implemented payment processing with validation and error handling",
                    "files": [
                        {
                            "path": "src/main/java/com/example/service/PaymentService.java",
                            "operation": "create",
                            "content": "public class PaymentService { public void processPayment() {} }",
                            "explanation": "Payment service with business logic"
                        },
                        {
                            "path": "src/test/java/com/example/service/PaymentServiceTest.java",
                            "operation": "create",
                            "content": "public class PaymentServiceTest { @Test void testPayment() {} }",
                            "explanation": "Unit tests for payment service"
                        }
                    ],
                    "testingNotes": "Added comprehensive unit tests for all payment scenarios",
                    "implementationNotes": "Implemented with idempotency and proper error handling"
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        String result = developerStep.execute(context);

        assertNotNull(result);
        assertTrue(result.contains("github.com"));
        assertTrue(result.contains("pull"));

        verify(gitHubApiClient, times(1)).createBranch(anyString(), anyString(), anyString(), anyString());
        verify(gitHubApiClient, times(2)).createBlob(anyString(), anyString(), anyString(), anyString());
        verify(gitHubApiClient, times(1)).createTree(anyString(), anyString(), anyList(), anyString());
        verify(gitHubApiClient, times(1)).createCommit(anyString(), anyString(), anyString(), anyString(), anyList(),
                anyMap(), anyMap());
        verify(gitHubApiClient, times(1)).createPullRequest(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void testLlmRetryMechanism() throws Exception {
        setupMocksForSuccessfulFlow();

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenReturn("""
                        {
                            "summary": "Test implementation",
                            "files": [
                                {
                                    "path": "src/main/java/Test.java",
                                    "operation": "create",
                                    "content": "public class Test {}",
                                    "explanation": "Test class"
                                }
                            ],
                            "testingNotes": "Tests included",
                            "implementationNotes": "Clean implementation"
                        }
                        """);

        String result = developerStep.execute(context);

        assertNotNull(result);
        verify(llmService, times(3)).generateStructuredOutput(anyString(), anyString(), anyMap());
    }

    @Test
    void testFallbackWhenLlmCompletelyFails() throws Exception {
        setupMocksForSuccessfulFlow();

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("LLM service down"));

        String result = developerStep.execute(context);

        assertNotNull(result);
        verify(llmService, times(3)).generateStructuredOutput(anyString(), anyString(), anyMap());
        verify(gitHubApiClient).createBlob(eq("test-owner"), eq("test-repo"), contains("Implementation Plan"),
                eq("utf-8"));
    }

    @Test
    void testValidationRejectsInvalidPaths() {
        setupMocksForSuccessfulFlow();

        String llmResponse = """
                {
                    "summary": "Malicious attempt",
                    "files": [
                        {
                            "path": "../../../etc/passwd",
                            "operation": "create",
                            "content": "malicious",
                            "explanation": "Bad file"
                        }
                    ],
                    "testingNotes": "Test",
                    "implementationNotes": "Test"
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        assertThrows(AgentStepException.class, () -> {
            developerStep.execute(context);
        });
    }

    @Test
    void testValidationRejectsPrivateKeys() {
        setupMocksForSuccessfulFlow();

        String llmResponse = """
                {
                    "summary": "Bad implementation",
                    "files": [
                        {
                            "path": "src/main/resources/key.pem",
                            "operation": "create",
                            "content": "-----BEGIN PRIVATE KEY-----\\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKj\\n-----END PRIVATE KEY-----",
                            "explanation": "Private key"
                        }
                    ],
                    "testingNotes": "Test",
                    "implementationNotes": "Test"
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        assertThrows(AgentStepException.class, () -> {
            developerStep.execute(context);
        });
    }

    @Test
    void testConflictResolutionOnExistingBranch() throws Exception {
        Map<String, Object> mainRef = Map.of("object", Map.of("sha", "main-sha-123"));
        when(gitHubApiClient.getReference("test-owner", "test-repo", "heads/main"))
                .thenReturn(mainRef);

        Map<String, Object> existingBranchRef = Map.of("object", Map.of("sha", "old-branch-sha"));
        when(gitHubApiClient.getReference("test-owner", "test-repo", "heads/ai/issue-456"))
                .thenReturn(existingBranchRef);

        Map<String, Object> comparison = Map.of("ahead_by", 3, "behind_by", 5);
        when(gitHubApiClient.compareCommits("test-owner", "test-repo", "main-sha-123", "old-branch-sha"))
                .thenReturn(comparison);

        when(gitHubApiClient.updateReference("test-owner", "test-repo", "heads/ai/issue-456", "main-sha-123", true))
                .thenReturn(Map.of("ref", "refs/heads/ai/issue-456"));

        setupRestOfSuccessfulFlow();

        String result = developerStep.execute(context);

        assertNotNull(result);
        verify(gitHubApiClient).compareCommits("test-owner", "test-repo", "main-sha-123", "old-branch-sha");
        verify(gitHubApiClient).updateReference("test-owner", "test-repo", "heads/ai/issue-456", "main-sha-123", true);
    }

    @Test
    void testProperCommitAttribution() throws Exception {
        setupMocksForSuccessfulFlow();

        String llmResponse = """
                {
                    "summary": "Test commit",
                    "files": [
                        {
                            "path": "src/main/java/Test.java",
                            "operation": "create",
                            "content": "public class Test {}",
                            "explanation": "Test"
                        }
                    ],
                    "testingNotes": "Tested",
                    "implementationNotes": "Clean"
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        developerStep.execute(context);

        verify(gitHubApiClient).createCommit(
                eq("test-owner"),
                eq("test-repo"),
                anyString(),
                anyString(),
                anyList(),
                argThat(author -> {
                    Map<String, Object> a = (Map<String, Object>) author;
                    return "Atlasia AI Bot".equals(a.get("name")) &&
                            "ai-bot@atlasia.io".equals(a.get("email")) &&
                            a.get("date") != null;
                }),
                argThat(committer -> {
                    Map<String, Object> c = (Map<String, Object>) committer;
                    return "Atlasia AI Bot".equals(c.get("name")) &&
                            "ai-bot@atlasia.io".equals(c.get("email")) &&
                            c.get("date") != null;
                }));
    }

    @Test
    void testReasoningTraceArtifactStorage() throws Exception {
        setupMocksForSuccessfulFlow();

        String llmResponse = """
                {
                    "summary": "Implementation with reasoning",
                    "files": [
                        {
                            "path": "src/main/java/Test.java",
                            "operation": "create",
                            "content": "public class Test {}",
                            "explanation": "Test class"
                        }
                    ],
                    "testingNotes": "Comprehensive tests",
                    "implementationNotes": "Follows best practices"
                }
                """;

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        developerStep.execute(context);

        List<com.atlasia.ai.model.RunArtifactEntity> artifacts = runEntity.getArtifacts();
        assertEquals(1, artifacts.size());

        com.atlasia.ai.model.RunArtifactEntity artifact = artifacts.get(0);
        assertEquals("developer", artifact.getAgentName());
        assertEquals("reasoning_trace", artifact.getArtifactType());

        String payload = artifact.getPayload();
        assertTrue(payload.contains("code_generation"));
        assertTrue(payload.contains("Implementation with reasoning"));
    }

    private void setupMocksForSuccessfulFlow() {
        Map<String, Object> mainRef = Map.of("object", Map.of("sha", "main-sha-123"));
        when(gitHubApiClient.getReference("test-owner", "test-repo", "heads/main"))
                .thenReturn(mainRef);

        when(gitHubApiClient.getReference("test-owner", "test-repo", "heads/ai/issue-456"))
                .thenThrow(new RuntimeException("Branch not found"));

        when(gitHubApiClient.createBranch("test-owner", "test-repo", "ai/issue-456", "main-sha-123"))
                .thenReturn(Map.of("ref", "refs/heads/ai/issue-456"));

        setupRestOfSuccessfulFlow();
    }

    private void setupRestOfSuccessfulFlow() {
        when(gitHubApiClient.getRepoTree(anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(Map.of("sha", "tree-sha", "tree", List.of()));

        when(gitHubApiClient.createBlob(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("sha", "blob-sha-123"));

        when(gitHubApiClient.createTree(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(Map.of("sha", "new-tree-sha"));

        when(gitHubApiClient.createCommit(anyString(), anyString(), anyString(), anyString(), anyList(), anyMap(),
                anyMap()))
                .thenReturn(Map.of("sha", "commit-sha-123"));

        when(gitHubApiClient.updateReference(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(Map.of("ref", "refs/heads/test"));

        when(gitHubApiClient.createPullRequest(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString()))
                .thenReturn(Map.of("html_url", "https://github.com/test-owner/test-repo/pull/789"));
    }
}
