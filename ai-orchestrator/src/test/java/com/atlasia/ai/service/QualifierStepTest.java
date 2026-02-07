package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.model.TicketPlan;
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
class QualifierStepTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private LlmService llmService;

    private ObjectMapper objectMapper;
    private QualifierStep qualifierStep;
    private RunContext context;
    private RunEntity runEntity;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        qualifierStep = new QualifierStep(objectMapper, llmService, gitHubApiClient);

        runEntity = new RunEntity(
                UUID.randomUUID(),
                "owner/repo",
                123,
                "full",
                RunStatus.QUALIFIER,
                Instant.now()
        );

        context = new RunContext(runEntity, "owner", "repo");

        setupGitHubMocks();
    }

    @Test
    void execute_withValidLlmResponse_generatesWorkPlan() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        assertNotNull(result);
        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        assertEquals("ai/issue-123", resultPlan.get("branchName"));
        assertTrue(resultPlan.containsKey("tasks"));
        assertTrue(resultPlan.containsKey("commands"));
        assertEquals("ai/issue-123", context.getBranchName());
    }

    @Test
    void execute_withLlmFailure_usesFallback() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        String result = qualifierStep.execute(context);

        assertNotNull(result);
        Map<String, Object> workPlan = objectMapper.readValue(result, Map.class);
        assertTrue(workPlan.containsKey("tasks"));
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) workPlan.get("tasks");
        assertTrue(tasks.size() >= 3);
    }

    @Test
    void execute_setBranchName_correctFormat() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        qualifierStep.execute(context);

        assertEquals("ai/issue-123", context.getBranchName());
    }

    @Test
    void execute_analyzesRepoStructure() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        qualifierStep.execute(context);

        verify(gitHubApiClient).getReference(eq("owner"), eq("repo"), eq("heads/main"));
        verify(gitHubApiClient).getRepoTree(eq("owner"), eq("repo"), anyString(), eq(true));
    }

    @Test
    void execute_fetchesAgentsMd() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        qualifierStep.execute(context);

        verify(gitHubApiClient).getRepoContent(eq("owner"), eq("repo"), eq("AGENTS.md"));
    }

    @Test
    void execute_includesCommandsInWorkPlan() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        assertTrue(resultPlan.containsKey("commands"));

        Map<String, Object> commands = (Map<String, Object>) resultPlan.get("commands");
        assertTrue(commands.containsKey("backendVerify"));
        assertTrue(commands.containsKey("frontendLint"));
        assertTrue(commands.containsKey("frontendTest"));
        assertTrue(commands.containsKey("e2e"));
    }

    @Test
    void execute_requiresAtLeastThreeTasks() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = new HashMap<>();
        workPlan.put("branchName", "ai/issue-123");
        workPlan.put("tasks", List.of(
                Map.of("id", "task-1", "area", "backend", "description", "Task 1", "filesLikely", List.of(), "tests", List.of())
        ));
        workPlan.put("commands", Map.of());
        workPlan.put("definitionOfDone", List.of());

        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        assertThrows(IllegalArgumentException.class, () -> qualifierStep.execute(context));
    }

    @Test
    void execute_validatesTaskArea() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = new HashMap<>();
        workPlan.put("branchName", "ai/issue-123");
        workPlan.put("tasks", List.of(
                Map.of("id", "task-1", "area", "invalid", "description", "Task 1", "filesLikely", List.of(), "tests", List.of()),
                Map.of("id", "task-2", "area", "backend", "description", "Task 2", "filesLikely", List.of(), "tests", List.of()),
                Map.of("id", "task-3", "area", "frontend", "description", "Task 3", "filesLikely", List.of(), "tests", List.of())
        ));
        workPlan.put("commands", Map.of());
        workPlan.put("definitionOfDone", List.of());

        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        assertThrows(IllegalArgumentException.class, () -> qualifierStep.execute(context));
    }

    @Test
    void execute_fallbackGeneratesBackendTasks() throws Exception {
        TicketPlan ticketPlan = new TicketPlan();
        ticketPlan.setIssueId(123);
        ticketPlan.setTitle("Add backend API");
        ticketPlan.setSummary("Implement REST API for user management");
        ticketPlan.setAcceptanceCriteria(List.of("Criterion 1"));
        ticketPlan.setOutOfScope(List.of());
        ticketPlan.setRisks(List.of());
        ticketPlan.setLabelsToApply(List.of("backend"));

        context.setTicketPlan(objectMapper.writeValueAsString(ticketPlan));

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("LLM failed"));

        String result = qualifierStep.execute(context);

        Map<String, Object> workPlan = objectMapper.readValue(result, Map.class);
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) workPlan.get("tasks");

        assertTrue(tasks.stream().anyMatch(t -> "backend".equals(t.get("area"))));
    }

    @Test
    void execute_fallbackGeneratesFrontendTasks() throws Exception {
        TicketPlan ticketPlan = new TicketPlan();
        ticketPlan.setIssueId(123);
        ticketPlan.setTitle("Add UI component");
        ticketPlan.setSummary("Create user interface for authentication");
        ticketPlan.setAcceptanceCriteria(List.of("Criterion 1"));
        ticketPlan.setOutOfScope(List.of());
        ticketPlan.setRisks(List.of());
        ticketPlan.setLabelsToApply(List.of("frontend"));

        context.setTicketPlan(objectMapper.writeValueAsString(ticketPlan));

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("LLM failed"));

        String result = qualifierStep.execute(context);

        Map<String, Object> workPlan = objectMapper.readValue(result, Map.class);
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) workPlan.get("tasks");

        assertTrue(tasks.stream().anyMatch(t -> "frontend".equals(t.get("area"))));
    }

    @Test
    void execute_enhancesWorkPlanWithMissingFields() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = new HashMap<>();
        workPlan.put("tasks", List.of(
                Map.of("id", "task-1", "area", "backend", "description", "Task 1", "filesLikely", List.of(), "tests", List.of()),
                Map.of("id", "task-2", "area", "frontend", "description", "Task 2", "filesLikely", List.of(), "tests", List.of()),
                Map.of("id", "task-3", "area", "docs", "description", "Task 3", "filesLikely", List.of(), "tests", List.of())
        ));

        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        assertTrue(resultPlan.containsKey("branchName"));
        assertTrue(resultPlan.containsKey("commands"));
        assertTrue(resultPlan.containsKey("definitionOfDone"));
    }

    @Test
    void execute_categoriesFiles() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        qualifierStep.execute(context);

        verify(llmService).generateStructuredOutput(
                anyString(),
                contains("Backend files"),
                anyMap()
        );
    }

    @Test
    void execute_parsesAgentsMdCommands() throws Exception {
        String agentsMdContent = """
                # AGENTS.md
                ## Commands
                - **Build**: `cd ai-orchestrator && mvn clean verify`
                - **Test**: `cd ai-orchestrator && mvn test`
                - **Lint**: `cd frontend && npm run lint`
                """;

        when(gitHubApiClient.getRepoContent(eq("owner"), eq("repo"), eq("AGENTS.md")))
                .thenReturn(Map.of("content", Base64.getEncoder().encodeToString(agentsMdContent.getBytes())));

        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        Map<String, Object> commands = (Map<String, Object>) resultPlan.get("commands");

        assertTrue(commands.get("backendVerify").toString().contains("mvn"));
        assertTrue(commands.get("frontendLint").toString().contains("npm"));
    }

    @Test
    void execute_cleansMarkdownCodeBlocks() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = "```json\n" + objectMapper.writeValueAsString(workPlan) + "\n```";

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        assertDoesNotThrow(() -> qualifierStep.execute(context));
    }

    @Test
    void execute_inferFilesForTasksWithoutFiles() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = new HashMap<>();
        workPlan.put("tasks", List.of(
                Map.of("id", "task-1", "area", "backend", "description", "Task 1", "tests", List.of()),
                Map.of("id", "task-2", "area", "frontend", "description", "Task 2", "tests", List.of()),
                Map.of("id", "task-3", "area", "docs", "description", "Task 3", "tests", List.of())
        ));

        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) resultPlan.get("tasks");

        for (Map<String, Object> task : tasks) {
            assertTrue(task.containsKey("filesLikely"));
            assertNotNull(task.get("filesLikely"));
        }
    }

    @Test
    void execute_includesRepoContextInPrompt() throws Exception {
        context.setTicketPlan(createTicketPlanJson());

        Map<String, Object> workPlan = createValidWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(workPlan);

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(llmResponse);

        qualifierStep.execute(context);

        verify(llmService).generateStructuredOutput(
                anyString(),
                contains("Repository Structure"),
                anyMap()
        );
    }

    // Helper methods

    private void setupGitHubMocks() {
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/main")))
                .thenReturn(Map.of("object", Map.of("sha", "main-sha")));

        Map<String, Object> tree = Map.of(
                "sha", "tree-sha",
                "tree", List.of(
                        Map.of("path", "ai-orchestrator/src/main/java/Service.java", "type", "blob"),
                        Map.of("path", "frontend/src/app.ts", "type", "blob"),
                        Map.of("path", "pom.xml", "type", "blob"),
                        Map.of("path", "README.md", "type", "blob"),
                        Map.of("path", "ai-orchestrator/src/test/java/ServiceTest.java", "type", "blob"),
                        Map.of("path", "infra/docker-compose.yml", "type", "blob")
                )
        );
        when(gitHubApiClient.getRepoTree(eq("owner"), eq("repo"), eq("main-sha"), eq(true)))
                .thenReturn(tree);

        String agentsMdContent = """
                # AGENTS.md
                ## Commands
                - **Build**: `cd ai-orchestrator && mvn clean verify`
                - **Test**: `cd ai-orchestrator && mvn test`
                - **Lint**: `cd frontend && npm run lint`
                """;

        when(gitHubApiClient.getRepoContent(eq("owner"), eq("repo"), eq("AGENTS.md")))
                .thenReturn(Map.of("content", Base64.getEncoder().encodeToString(agentsMdContent.getBytes())));
    }

    private String createTicketPlanJson() throws Exception {
        TicketPlan ticketPlan = new TicketPlan();
        ticketPlan.setIssueId(123);
        ticketPlan.setTitle("Test Issue");
        ticketPlan.setSummary("Implement new feature");
        ticketPlan.setAcceptanceCriteria(List.of("Criterion 1", "Criterion 2"));
        ticketPlan.setOutOfScope(List.of());
        ticketPlan.setRisks(List.of("Risk 1"));
        ticketPlan.setLabelsToApply(List.of("enhancement"));

        return objectMapper.writeValueAsString(ticketPlan);
    }

    private Map<String, Object> createValidWorkPlan() {
        Map<String, Object> workPlan = new HashMap<>();
        workPlan.put("branchName", "ai/issue-123");

        List<Map<String, Object>> tasks = List.of(
                Map.of(
                        "id", "task-1",
                        "area", "backend",
                        "description", "Implement service layer",
                        "filesLikely", List.of("src/main/java/Service.java"),
                        "tests", List.of("Unit tests for service")
                ),
                Map.of(
                        "id", "task-2",
                        "area", "frontend",
                        "description", "Add UI component",
                        "filesLikely", List.of("src/app/component.ts"),
                        "tests", List.of("Component tests")
                ),
                Map.of(
                        "id", "task-3",
                        "area", "docs",
                        "description", "Update documentation",
                        "filesLikely", List.of("README.md"),
                        "tests", List.of()
                )
        );
        workPlan.put("tasks", tasks);

        Map<String, Object> commands = Map.of(
                "backendVerify", "cd ai-orchestrator && mvn clean verify",
                "frontendLint", "cd frontend && npm run lint",
                "frontendTest", "cd frontend && npm test",
                "e2e", "cd frontend && npm run e2e"
        );
        workPlan.put("commands", commands);

        workPlan.put("definitionOfDone", List.of(
                "All tests pass",
                "Code follows conventions",
                "PR created"
        ));

        return workPlan;
    }
}
