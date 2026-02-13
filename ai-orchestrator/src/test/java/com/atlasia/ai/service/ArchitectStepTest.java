package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchitectStepTest {

        @Mock
        private LlmService llmService;

        @Mock
        private GitHubApiClient gitHubApiClient;

        private ObjectMapper objectMapper;
        private ArchitectStep architectStep;
        private RunContext context;
        private RunEntity runEntity;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                architectStep = new ArchitectStep(llmService, gitHubApiClient, objectMapper);

                runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.ARCHITECT,
                                Instant.now());

                context = new RunContext(runEntity, "owner", "repo");
                setupGitHubMocks();
        }

        @Test
        void execute_withValidLlmResponse_generatesArchitectureNotes() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertNotNull(result);
                assertTrue(result.contains("Architecture Notes"));
                assertTrue(result.contains("Design Patterns"));
                assertTrue(result.contains("Component Architecture"));
                assertTrue(result.contains("ADR"));
                assertEquals(result, context.getArchitectureNotes());
        }

        @Test
        void execute_identifiesRepositoryPattern() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertTrue(result.contains("Repository Pattern") || result.contains("Repository"));
        }

        @Test
        void execute_includesMermaidDiagram() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                analysis.setComponentDiagram("graph TB\n  A[Component A] --> B[Component B]");
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertTrue(result.contains("```mermaid"));
                assertTrue(result.contains("graph TB"));
                assertTrue(result.contains("Component A"));
        }

        @Test
        void execute_withLlmFailure_usesFallback() throws Exception {
                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenThrow(new RuntimeException("LLM service unavailable"));

                String result = architectStep.execute(context);

                assertNotNull(result);
                assertTrue(result.contains("Architecture Notes"));
                assertTrue(result.contains("Layered Architecture"));
        }

        @Test
        void execute_parsesRepoStructure() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                architectStep.execute(context);

                verify(gitHubApiClient).getReference(eq("owner"), eq("repo"), eq("heads/main"));
                verify(gitHubApiClient).getRepoTree(eq("owner"), eq("repo"), anyString(), eq(true));
        }

        @Test
        void execute_storesReasoningTrace() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                architectStep.execute(context);

                assertEquals(1, runEntity.getArtifacts().size());
                com.atlasia.ai.model.RunArtifactEntity artifact = runEntity.getArtifacts().get(0);
                assertEquals("architect", artifact.getAgentName());
                assertEquals("reasoning_trace", artifact.getArtifactType());
                assertTrue(artifact.getPayload().contains("architecture_design_patterns"));
        }

        @Test
        void execute_includesTechnicalRisks() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                ArchitectStep.TechnicalRisk risk = new ArchitectStep.TechnicalRisk(
                                "High complexity in database migrations",
                                "Use Flyway for versioned migrations");
                analysis.setTechnicalRisks(List.of(risk));

                String llmResponse = objectMapper.writeValueAsString(analysis);
                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertTrue(result.contains("Technical Risks"));
                assertTrue(result.contains("High complexity in database migrations"));
                assertTrue(result.contains("Use Flyway for versioned migrations"));
        }

        @Test
        void execute_includesTestingStrategy() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                ArchitectStep.TestingStrategy strategy = new ArchitectStep.TestingStrategy(
                                "Unit tests for all service methods",
                                "Integration tests for API endpoints",
                                "E2E tests for critical user flows");
                analysis.setTestingStrategy(strategy);

                String llmResponse = objectMapper.writeValueAsString(analysis);
                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertTrue(result.contains("Testing Strategy"));
                assertTrue(result.contains("Unit Tests"));
                assertTrue(result.contains("Integration Tests"));
                assertTrue(result.contains("End-to-End Tests"));
        }

        @Test
        void execute_fetchesKeyFiles() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                architectStep.execute(context);

                verify(gitHubApiClient).getRepoContent(eq("owner"), eq("repo"), eq("AGENTS.md"));
                verify(gitHubApiClient).getRepoContent(eq("owner"), eq("repo"), eq("README.md"));
        }

        @Test
        void execute_includesArchitectureDecisions() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                ArchitectStep.ArchitectureDecision decision = new ArchitectStep.ArchitectureDecision(
                                "Use REST API instead of GraphQL",
                                "The team has more experience with REST",
                                "Implement RESTful endpoints with standard HTTP methods",
                                "Faster development, well-understood patterns, easier testing");
                analysis.setArchitectureDecisions(List.of(decision));

                String llmResponse = objectMapper.writeValueAsString(analysis);
                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertTrue(result.contains("Architecture Decision Records"));
                assertTrue(result.contains("Use REST API instead of GraphQL"));
        }

        @Test
        void execute_recommendsDesignPatterns() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                ArchitectStep.RecommendedPattern pattern = new ArchitectStep.RecommendedPattern(
                                "Factory Pattern",
                                "Need to create different types of processors",
                                "Create ProcessorFactory with factory method for each type");
                analysis.setRecommendedPatterns(List.of(pattern));

                String llmResponse = objectMapper.writeValueAsString(analysis);
                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertTrue(result.contains("Recommended Design Patterns"));
                assertTrue(result.contains("Factory Pattern"));
        }

        @Test
        void execute_includesDataFlow() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                analysis.setDataFlow("Request flows through controller → service → repository → database");
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertTrue(result.contains("Data Flow"));
                assertTrue(result.contains("controller → service → repository → database"));
        }

        @Test
        void execute_includesIntegrationPoints() throws Exception {
                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                analysis.setIntegrationPoints(List.of(
                                "REST API endpoints",
                                "Database transactions",
                                "Message queue integration"));
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertTrue(result.contains("Integration Points"));
                assertTrue(result.contains("REST API endpoints"));
                assertTrue(result.contains("Message queue integration"));
        }

        @Test
        void execute_withTicketPlan_includesInContext() throws Exception {
                context.setTicketPlan("{\"issueId\":123,\"title\":\"Add feature\"}");

                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), contains("Ticket Plan"), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertNotNull(result);
                verify(llmService).generateStructuredOutput(anyString(), contains("Ticket Plan"), anyMap());
        }

        @Test
        void execute_categoriesFilesCorrectly() throws Exception {
                Map<String, Object> tree = Map.of(
                                "sha", "tree-sha",
                                "tree", List.of(
                                                Map.of("path", "src/main/java/Controller.java", "type", "blob"),
                                                Map.of("path", "frontend/src/app.ts", "type", "blob"),
                                                Map.of("path", "pom.xml", "type", "blob"),
                                                Map.of("path", "README.md", "type", "blob"),
                                                Map.of("path", "src/test/java/TestClass.java", "type", "blob"),
                                                Map.of("path", "infra/docker-compose.yml", "type", "blob")));

                when(gitHubApiClient.getRepoTree(anyString(), anyString(), anyString(), anyBoolean()))
                                .thenReturn(tree);

                ArchitectStep.ArchitectureAnalysis analysis = createValidAnalysis();
                String llmResponse = objectMapper.writeValueAsString(analysis);

                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                String result = architectStep.execute(context);

                assertNotNull(result);
                verify(llmService).generateStructuredOutput(
                                anyString(),
                                contains("Backend (Java)"),
                                anyMap());
        }

        // Helper methods

        private void setupGitHubMocks() {
                lenient().when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/main")))
                                .thenReturn(Map.of("object", Map.of("sha", "main-sha")));

                Map<String, Object> tree = Map.of(
                                "sha", "tree-sha",
                                "tree", List.of(
                                                Map.of("path", "src/main/java/Example.java", "type", "blob"),
                                                Map.of("path", "README.md", "type", "blob")));
                lenient().when(gitHubApiClient.getRepoTree(eq("owner"), eq("repo"), eq("main-sha"), eq(true)))
                                .thenReturn(tree);

                lenient().when(gitHubApiClient.getRepoContent(eq("owner"), eq("repo"), anyString()))
                                .thenReturn(Map.of("content",
                                                Base64.getEncoder().encodeToString("file content".getBytes())));
        }

        private ArchitectStep.ArchitectureAnalysis createValidAnalysis() {
                ArchitectStep.ArchitectureAnalysis analysis = new ArchitectStep.ArchitectureAnalysis();

                List<ArchitectStep.DesignPattern> identifiedPatterns = List.of(
                                new ArchitectStep.DesignPattern(
                                                "Repository Pattern",
                                                "Data access layer",
                                                "JPA repositories for database access"),
                                new ArchitectStep.DesignPattern(
                                                "Layered Architecture",
                                                "Throughout application",
                                                "Controller → Service → Repository → Entity"));
                analysis.setIdentifiedPatterns(identifiedPatterns);

                List<ArchitectStep.RecommendedPattern> recommendedPatterns = List.of(
                                new ArchitectStep.RecommendedPattern(
                                                "Service Layer Pattern",
                                                "Encapsulate business logic",
                                                "Create service classes with @Service annotation"));
                analysis.setRecommendedPatterns(recommendedPatterns);

                analysis.setComponentDiagram("graph TB\n  A[Controller] --> B[Service]\n  B --> C[Repository]");

                List<ArchitectStep.ArchitectureDecision> decisions = List.of(
                                new ArchitectStep.ArchitectureDecision(
                                                "Use Spring Boot",
                                                "Need a robust framework",
                                                "Adopt Spring Boot for the backend",
                                                "Fast development, large ecosystem, good documentation"));
                analysis.setArchitectureDecisions(decisions);

                analysis.setComponentsAffected(List.of("Backend services", "Database layer"));
                analysis.setDataFlow("Request → Controller → Service → Repository → Database");
                analysis.setIntegrationPoints(List.of("REST API", "Database"));

                List<ArchitectStep.TechnicalRisk> risks = List.of(
                                new ArchitectStep.TechnicalRisk(
                                                "Database schema changes may break existing code",
                                                "Use database migrations and versioning"));
                analysis.setTechnicalRisks(risks);

                ArchitectStep.TestingStrategy strategy = new ArchitectStep.TestingStrategy(
                                "Unit tests for services",
                                "Integration tests for APIs",
                                "E2E tests for workflows");
                analysis.setTestingStrategy(strategy);

                return analysis;
        }
}
