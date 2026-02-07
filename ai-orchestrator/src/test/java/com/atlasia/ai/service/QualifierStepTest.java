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
    private LlmService llmService;

    @Mock
    private GitHubApiClient gitHubApiClient;

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
    }

    @Test
    void execute_WithValidLlmResponse_GeneratesWorkPlan() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        assertNotNull(result);
        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        assertEquals("ai/issue-123", resultPlan.get("branchName"));
        assertTrue(resultPlan.containsKey("tasks"));
        
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) resultPlan.get("tasks");
        assertTrue(tasks.size() >= 3);
        
        for (Map<String, Object> task : tasks) {
            assertTrue(task.containsKey("id"));
            assertTrue(task.containsKey("area"));
            assertTrue(task.containsKey("description"));
            assertTrue(task.containsKey("filesLikely"));
            assertTrue(task.containsKey("tests"));
        }
        
        assertTrue(resultPlan.containsKey("commands"));
        Map<String, Object> commands = (Map<String, Object>) resultPlan.get("commands");
        assertTrue(commands.containsKey("backendVerify"));
        assertTrue(commands.containsKey("frontendLint"));
        assertTrue(commands.containsKey("frontendTest"));
        assertTrue(commands.containsKey("e2e"));
        
        assertTrue(resultPlan.containsKey("definitionOfDone"));
    }

    @Test
    void execute_WithLlmFailure_UsesFallbackStrategy() throws Exception {
        setupMocksForSuccessfulExecution();

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("LLM service unavailable"));

        String result = qualifierStep.execute(context);

        assertNotNull(result);
        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        assertEquals("ai/issue-123", resultPlan.get("branchName"));
        
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) resultPlan.get("tasks");
        assertEquals(3, tasks.size());
    }

    @Test
    void execute_ParsesTicketPlanCorrectly() throws Exception {
        TicketPlan ticketPlan = createTicketPlan();
        context.setTicketPlan(objectMapper.writeValueAsString(ticketPlan));
        
        setupMocksForSuccessfulExecution();

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        qualifierStep.execute(context);

        verify(llmService).generateStructuredOutput(
            anyString(),
            contains("Issue #123"),
            anyMap()
        );
        verify(llmService).generateStructuredOutput(
            anyString(),
            contains("Test ticket for qualifier"),
            anyMap()
        );
    }

    @Test
    void execute_AnalyzesRepoStructure() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        qualifierStep.execute(context);

        verify(gitHubApiClient).getReference("owner", "repo", "heads/main");
        verify(gitHubApiClient).getRepoTree(eq("owner"), eq("repo"), anyString(), eq(true));
    }

    @Test
    void execute_ParsesAgentsMdForCommands() throws Exception {
        setupMocksForSuccessfulExecution();

        String agentsMdContent = """
            # AGENTS.md
            ## Commands
            - **Build**: `cd ai-orchestrator && mvn clean verify`
            - **Lint**: `cd frontend && npm run lint`
            - **Test**: `cd ai-orchestrator && mvn test` | `cd frontend && npm test -- --watch=false`
            """;
        
        Map<String, Object> agentsMdFile = new HashMap<>();
        agentsMdFile.put("content", Base64.getEncoder().encodeToString(agentsMdContent.getBytes()));
        when(gitHubApiClient.getRepoContent("owner", "repo", "AGENTS.md"))
            .thenReturn(agentsMdFile);

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        Map<String, Object> commands = (Map<String, Object>) resultPlan.get("commands");
        assertEquals("cd ai-orchestrator && mvn clean verify", commands.get("backendVerify"));
        assertEquals("cd frontend && npm run lint", commands.get("frontendLint"));
    }

    @Test
    void execute_WithAgentsMdFailure_UsesDefaultCommands() throws Exception {
        setupMocksForRepoStructure();
        
        when(gitHubApiClient.getRepoContent("owner", "repo", "AGENTS.md"))
            .thenThrow(new RuntimeException("File not found"));

        TicketPlan ticketPlan = createTicketPlan();
        context.setTicketPlan(objectMapper.writeValueAsString(ticketPlan));

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        assertNotNull(result);
        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        Map<String, Object> commands = (Map<String, Object>) resultPlan.get("commands");
        assertTrue(commands.containsKey("backendVerify"));
    }

    @Test
    void execute_WithRepoStructureFailure_UsesFallbackStructure() throws Exception {
        TicketPlan ticketPlan = createTicketPlan();
        context.setTicketPlan(objectMapper.writeValueAsString(ticketPlan));

        when(gitHubApiClient.getReference("owner", "repo", "heads/main"))
            .thenThrow(new RuntimeException("Repository not found"));

        Map<String, Object> agentsMdFile = createAgentsMdFile();
        when(gitHubApiClient.getRepoContent("owner", "repo", "AGENTS.md"))
            .thenReturn(agentsMdFile);

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        assertNotNull(result);
        verify(llmService).generateStructuredOutput(
            anyString(),
            contains("Backend files"),
            anyMap()
        );
    }

    @Test
    void execute_EnhancesWorkPlanWithCommands() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> workPlanWithoutCommands = new HashMap<>();
        workPlanWithoutCommands.put("tasks", createBasicTasks());
        workPlanWithoutCommands.put("definitionOfDone", List.of("All tests pass"));
        
        String llmResponse = objectMapper.writeValueAsString(workPlanWithoutCommands);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        assertTrue(resultPlan.containsKey("commands"));
        Map<String, Object> commands = (Map<String, Object>) resultPlan.get("commands");
        assertTrue(commands.containsKey("backendVerify"));
        assertTrue(commands.containsKey("frontendLint"));
    }

    @Test
    void execute_ValidatesWorkPlanSchema() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> invalidWorkPlan = new HashMap<>();
        invalidWorkPlan.put("tasks", List.of());
        
        String llmResponse = objectMapper.writeValueAsString(invalidWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        assertThrows(IllegalArgumentException.class, () -> {
            qualifierStep.execute(context);
        });
    }

    @Test
    void execute_RequiresAtLeastThreeTasks() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> workPlanWithTwoTasks = new HashMap<>();
        workPlanWithTwoTasks.put("tasks", List.of(
            createTask("task-1", "backend"),
            createTask("task-2", "frontend")
        ));
        workPlanWithTwoTasks.put("commands", createCommands());
        workPlanWithTwoTasks.put("definitionOfDone", List.of("All tests pass"));
        
        String llmResponse = objectMapper.writeValueAsString(workPlanWithTwoTasks);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        assertThrows(IllegalArgumentException.class, () -> {
            qualifierStep.execute(context);
        });
    }

    @Test
    void execute_ValidatesTaskAreaEnum() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> workPlanWithInvalidArea = new HashMap<>();
        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(createTask("task-1", "invalid-area"));
        tasks.add(createTask("task-2", "frontend"));
        tasks.add(createTask("task-3", "docs"));
        workPlanWithInvalidArea.put("tasks", tasks);
        workPlanWithInvalidArea.put("commands", createCommands());
        workPlanWithInvalidArea.put("definitionOfDone", List.of("All tests pass"));
        
        String llmResponse = objectMapper.writeValueAsString(workPlanWithInvalidArea);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        assertThrows(IllegalArgumentException.class, () -> {
            qualifierStep.execute(context);
        });
    }

    @Test
    void execute_InfersFilesForTasksWithoutFiles() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> workPlan = new HashMap<>();
        List<Map<String, Object>> tasks = new ArrayList<>();
        
        Map<String, Object> taskWithoutFiles = new HashMap<>();
        taskWithoutFiles.put("id", "task-1");
        taskWithoutFiles.put("area", "backend");
        taskWithoutFiles.put("description", "Backend task");
        taskWithoutFiles.put("filesLikely", List.of());
        taskWithoutFiles.put("tests", List.of());
        tasks.add(taskWithoutFiles);
        
        tasks.add(createTask("task-2", "frontend"));
        tasks.add(createTask("task-3", "docs"));
        
        workPlan.put("tasks", tasks);
        workPlan.put("definitionOfDone", List.of("All tests pass"));
        
        String llmResponse = objectMapper.writeValueAsString(workPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        List<Map<String, Object>> resultTasks = (List<Map<String, Object>>) resultPlan.get("tasks");
        List<String> filesLikely = (List<String>) resultTasks.get(0).get("filesLikely");
        assertFalse(filesLikely.isEmpty());
    }

    @Test
    void execute_AddsDefaultTestsForTasksWithoutTests() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> workPlan = new HashMap<>();
        List<Map<String, Object>> tasks = new ArrayList<>();
        
        Map<String, Object> taskWithoutTests = new HashMap<>();
        taskWithoutTests.put("id", "task-1");
        taskWithoutTests.put("area", "backend");
        taskWithoutTests.put("description", "Backend task");
        taskWithoutTests.put("filesLikely", List.of("some/path"));
        taskWithoutTests.put("tests", List.of());
        tasks.add(taskWithoutTests);
        
        tasks.add(createTask("task-2", "frontend"));
        tasks.add(createTask("task-3", "docs"));
        
        workPlan.put("tasks", tasks);
        workPlan.put("definitionOfDone", List.of("All tests pass"));
        
        String llmResponse = objectMapper.writeValueAsString(workPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        List<Map<String, Object>> resultTasks = (List<Map<String, Object>>) resultPlan.get("tasks");
        List<String> tests = (List<String>) resultTasks.get(0).get("tests");
        assertFalse(tests.isEmpty());
        assertTrue(tests.contains("Unit tests"));
    }

    @Test
    void execute_AddsDefinitionOfDoneIfMissing() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> workPlan = new HashMap<>();
        workPlan.put("tasks", createBasicTasks());
        
        String llmResponse = objectMapper.writeValueAsString(workPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        assertTrue(resultPlan.containsKey("definitionOfDone"));
        List<String> dod = (List<String>) resultPlan.get("definitionOfDone");
        assertFalse(dod.isEmpty());
    }

    @Test
    void execute_CleansMarkdownCodeBlocks() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = "```json\n" + objectMapper.writeValueAsString(expectedWorkPlan) + "\n```";
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        String result = qualifierStep.execute(context);

        assertNotNull(result);
        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        assertEquals("ai/issue-123", resultPlan.get("branchName"));
    }

    @Test
    void execute_SetsBranchNameInContext() throws Exception {
        setupMocksForSuccessfulExecution();

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        qualifierStep.execute(context);

        assertEquals("ai/issue-123", context.getBranchName());
    }

    @Test
    void execute_HandlesBackendOnlyTicket() throws Exception {
        setupMocksForSuccessfulExecution();

        TicketPlan backendTicket = new TicketPlan(
            123,
            "Add backend API endpoint",
            "This ticket requires backend API changes only",
            List.of("API endpoint created", "Tests pass"),
            List.of(),
            List.of(),
            List.of("backend")
        );
        context.setTicketPlan(objectMapper.writeValueAsString(backendTicket));

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) resultPlan.get("tasks");
        assertTrue(tasks.stream().anyMatch(t -> "backend".equals(t.get("area"))));
    }

    @Test
    void execute_HandlesFrontendOnlyTicket() throws Exception {
        setupMocksForSuccessfulExecution();

        TicketPlan frontendTicket = new TicketPlan(
            123,
            "Update UI component",
            "This ticket requires frontend UI changes only",
            List.of("Component updated", "Tests pass"),
            List.of(),
            List.of(),
            List.of("frontend")
        );
        context.setTicketPlan(objectMapper.writeValueAsString(frontendTicket));

        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("Force fallback"));

        String result = qualifierStep.execute(context);

        Map<String, Object> resultPlan = objectMapper.readValue(result, Map.class);
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) resultPlan.get("tasks");
        assertTrue(tasks.stream().anyMatch(t -> "frontend".equals(t.get("area"))));
    }

    @Test
    void execute_IncludesAcceptanceCriteriaInPrompt() throws Exception {
        setupMocksForSuccessfulExecution();

        TicketPlan ticketPlan = new TicketPlan(
            123,
            "Test ticket",
            "Summary",
            List.of("Criterion 1", "Criterion 2", "Criterion 3"),
            List.of(),
            List.of(),
            List.of("enhancement")
        );
        context.setTicketPlan(objectMapper.writeValueAsString(ticketPlan));

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        qualifierStep.execute(context);

        verify(llmService).generateStructuredOutput(
            anyString(),
            contains("Criterion 1"),
            anyMap()
        );
    }

    @Test
    void execute_IncludesRisksInPrompt() throws Exception {
        setupMocksForSuccessfulExecution();

        TicketPlan ticketPlan = new TicketPlan(
            123,
            "Test ticket",
            "Summary",
            List.of("Criterion 1"),
            List.of(),
            List.of("Risk 1", "Risk 2"),
            List.of("enhancement")
        );
        context.setTicketPlan(objectMapper.writeValueAsString(ticketPlan));

        Map<String, Object> expectedWorkPlan = createExpectedWorkPlan();
        String llmResponse = objectMapper.writeValueAsString(expectedWorkPlan);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
            .thenReturn(llmResponse);

        qualifierStep.execute(context);

        verify(llmService).generateStructuredOutput(
            anyString(),
            contains("Risk 1"),
            anyMap()
        );
    }

    private void setupMocksForSuccessfulExecution() {
        setupMocksForRepoStructure();
        
        Map<String, Object> agentsMdFile = createAgentsMdFile();
        when(gitHubApiClient.getRepoContent("owner", "repo", "AGENTS.md"))
            .thenReturn(agentsMdFile);

        TicketPlan ticketPlan = createTicketPlan();
        try {
            context.setTicketPlan(objectMapper.writeValueAsString(ticketPlan));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setupMocksForRepoStructure() {
        Map<String, Object> ref = new HashMap<>();
        Map<String, Object> refObject = new HashMap<>();
        refObject.put("sha", "abc123");
        ref.put("object", refObject);
        when(gitHubApiClient.getReference("owner", "repo", "heads/main"))
            .thenReturn(ref);

        Map<String, Object> tree = new HashMap<>();
        List<Map<String, Object>> treeItems = createRepoTreeItems();
        tree.put("tree", treeItems);
        when(gitHubApiClient.getRepoTree(eq("owner"), eq("repo"), anyString(), eq(true)))
            .thenReturn(tree);
    }

    private List<Map<String, Object>> createRepoTreeItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        
        items.add(createTreeItem("ai-orchestrator/src/main/java/Service.java", "blob"));
        items.add(createTreeItem("ai-orchestrator/src/main/java/Controller.java", "blob"));
        items.add(createTreeItem("ai-orchestrator/src/test/java/ServiceTest.java", "blob"));
        items.add(createTreeItem("frontend/src/app/component.ts", "blob"));
        items.add(createTreeItem("frontend/src/app/service.ts", "blob"));
        items.add(createTreeItem("frontend/src/app/component.spec.ts", "blob"));
        items.add(createTreeItem("docs/README.md", "blob"));
        items.add(createTreeItem("infra/docker-compose.yml", "blob"));
        
        return items;
    }

    private Map<String, Object> createTreeItem(String path, String type) {
        Map<String, Object> item = new HashMap<>();
        item.put("path", path);
        item.put("type", type);
        return item;
    }

    private Map<String, Object> createAgentsMdFile() {
        String content = """
            # AGENTS.md
            ## Commands
            - **Build**: `cd ai-orchestrator && mvn clean verify`
            - **Lint**: `cd frontend && npm run lint`
            - **Test**: `cd ai-orchestrator && mvn test` | `cd frontend && npm test -- --watch=false`
            """;
        
        Map<String, Object> file = new HashMap<>();
        file.put("content", Base64.getEncoder().encodeToString(content.getBytes()));
        return file;
    }

    private TicketPlan createTicketPlan() {
        return new TicketPlan(
            123,
            "Test ticket for qualifier",
            "This is a test ticket that requires backend and frontend changes",
            List.of("Backend changes implemented", "Frontend changes implemented", "Tests pass"),
            List.of("Mobile support"),
            List.of("Complex implementation"),
            List.of("enhancement")
        );
    }

    private Map<String, Object> createExpectedWorkPlan() {
        return Map.of(
            "tasks", createBasicTasks(),
            "definitionOfDone", List.of("All tests pass", "Code reviewed")
        );
    }

    private List<Map<String, Object>> createBasicTasks() {
        return List.of(
            createTask("task-1", "backend"),
            createTask("task-2", "frontend"),
            createTask("task-3", "docs")
        );
    }

    private Map<String, Object> createTask(String id, String area) {
        return Map.of(
            "id", id,
            "area", area,
            "description", "Task " + id + " in " + area,
            "filesLikely", List.of("path/to/file"),
            "tests", List.of("Unit tests")
        );
    }

    private Map<String, Object> createCommands() {
        return Map.of(
            "backendVerify", "cd ai-orchestrator && mvn clean verify",
            "frontendLint", "cd frontend && npm run lint",
            "frontendTest", "cd frontend && npm test -- --watch=false",
            "e2e", "cd frontend && npm run e2e"
        );
    }
}
