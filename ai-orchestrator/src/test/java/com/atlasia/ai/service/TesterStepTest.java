package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
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
class TesterStepTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private LlmService llmService;

    @Mock
    private OrchestratorMetrics metrics;

    private ObjectMapper objectMapper;
    private TesterStep testerStep;
    private RunContext context;
    private RunEntity runEntity;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testerStep = new TesterStep(gitHubApiClient, llmService, objectMapper, metrics);

        runEntity = new RunEntity(
                UUID.randomUUID(),
                "owner/repo",
                123,
                "full",
                RunStatus.TESTER,
                Instant.now()
        );

        context = new RunContext(runEntity, "owner", "repo");
        context.setBranchName("ai/issue-123");
        context.setPrUrl("https://github.com/owner/repo/pull/1");
    }

    @Test
    void execute_allTestsPass_returnsSuccessReport() throws Exception {
        setupPassingCheckRuns();

        String result = testerStep.execute(context);

        assertNotNull(result);
        Map<String, Object> report = objectMapper.readValue(result, Map.class);
        assertEquals("GREEN", report.get("ciStatus"));
        assertEquals("https://github.com/owner/repo/pull/1", report.get("prUrl"));

        verify(gitHubApiClient, atLeastOnce()).listCheckRunsForRef(eq("owner"), eq("repo"), anyString());
        verify(metrics, never()).recordCiFixAttempt();
        verify(metrics, never()).recordE2eFixAttempt();
    }

    @Test
    void execute_ciFailsOnce_appliesFixAndRetries() throws Exception {
        setupFailingThenPassingCheckRuns();
        setupFixPatching();

        String result = testerStep.execute(context);

        assertNotNull(result);
        assertTrue(runEntity.getCiFixCount() > 0);
        verify(metrics).recordCiFixAttempt();
        verify(llmService).generateStructuredOutput(anyString(), contains("CI"), anyMap());
        verify(gitHubApiClient).createBlob(eq("owner"), eq("repo"), anyString(), anyString());
        verify(gitHubApiClient).createCommit(eq("owner"), eq("repo"), contains("Auto-fix CI"), anyString(), anyList(), anyMap(), anyMap());
    }

    @Test
    void execute_ciFailsMaxTimes_escalates() throws Exception {
        setupAlwaysFailingCheckRuns();
        setupFixPatching();

        EscalationException exception = assertThrows(
                EscalationException.class,
                () -> testerStep.execute(context)
        );

        assertNotNull(exception.getEscalationJson());
        assertTrue(exception.getEscalationJson().contains("CI tests failed"));
        assertEquals(3, runEntity.getCiFixCount());
        verify(metrics, times(3)).recordCiFixAttempt();
    }

    @Test
    void execute_e2eFailsOnce_appliesFixAndRetries() throws Exception {
        setupCiPassingE2eFailingOnce();
        setupFixPatching();

        String result = testerStep.execute(context);

        assertNotNull(result);
        assertTrue(runEntity.getE2eFixCount() > 0);
        verify(metrics).recordE2eFixAttempt();
        verify(llmService).generateStructuredOutput(anyString(), contains("E2E"), anyMap());
    }

    @Test
    void execute_e2eFailsMaxTimes_escalates() throws Exception {
        setupCiPassingE2eAlwaysFailing();
        setupFixPatching();

        EscalationException exception = assertThrows(
                EscalationException.class,
                () -> testerStep.execute(context)
        );

        assertNotNull(exception.getEscalationJson());
        assertTrue(exception.getEscalationJson().contains("E2E tests failed"));
        assertEquals(2, runEntity.getE2eFixCount());
        verify(metrics, times(2)).recordE2eFixAttempt();
    }

    @Test
    void execute_withoutPrUrl_throwsException() {
        context.setPrUrl(null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> testerStep.execute(context)
        );

        assertTrue(exception.getMessage().contains("PR URL is required"));
    }

    @Test
    void execute_parsesCiLogs_identifiesCompileErrors() throws Exception {
        String logs = """
                [ERROR] /src/Main.java:[10,5] cannot find symbol
                [ERROR]   symbol:   class NonExistent
                [ERROR]   location: class Main
                BUILD FAILED
                """;

        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(Map.of("object", Map.of("sha", "commit-sha")));

        Map<String, Object> checkRuns = createCheckRunsWithLogs("Build", "failure", logs);
        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), eq("commit-sha")))
                .thenReturn(checkRuns);

        when(gitHubApiClient.getJobLogs(eq("owner"), eq("repo"), anyLong()))
                .thenReturn(logs);

        setupFixPatching();

        EscalationException exception = assertThrows(
                EscalationException.class,
                () -> testerStep.execute(context)
        );

        String escalationJson = exception.getEscalationJson();
        assertTrue(escalationJson.contains("cannot find symbol"));
    }

    @Test
    void execute_parsesCiLogs_identifiesTestFailures() throws Exception {
        String logs = """
                [ERROR] Tests run: 10, Failures: 2, Errors: 0, Skipped: 0
                [ERROR] TestClass.testMethod()  Time elapsed: 0.5 s  <<< FAILURE!
                java.lang.AssertionError: expected:<5> but was:<3>
                """;

        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(Map.of("object", Map.of("sha", "commit-sha")));

        Map<String, Object> checkRuns = createCheckRunsWithLogs("Test", "failure", logs);
        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), eq("commit-sha")))
                .thenReturn(checkRuns);

        when(gitHubApiClient.getJobLogs(eq("owner"), eq("repo"), anyLong()))
                .thenReturn(logs);

        setupFixPatching();

        EscalationException exception = assertThrows(
                EscalationException.class,
                () -> testerStep.execute(context)
        );

        String escalationJson = exception.getEscalationJson();
        assertTrue(escalationJson.contains("test") || escalationJson.contains("Test"));
    }

    @Test
    void execute_distinguishesCiFromE2eTests() throws Exception {
        Map<String, Object> branchRef = Map.of("object", Map.of("sha", "commit-sha"));
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(branchRef);

        List<Map<String, Object>> checkRunsList = new ArrayList<>();
        checkRunsList.add(createCheckRun(1L, "Backend Tests", "completed", "success", false));
        checkRunsList.add(createCheckRun(2L, "Frontend Lint", "completed", "success", false));
        checkRunsList.add(createCheckRun(3L, "E2E Tests", "completed", "success", true));

        Map<String, Object> checkRuns = Map.of("check_runs", checkRunsList);
        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), eq("commit-sha")))
                .thenReturn(checkRuns);

        String result = testerStep.execute(context);

        assertNotNull(result);
        Map<String, Object> report = objectMapper.readValue(result, Map.class);
        assertEquals("GREEN", report.get("ciStatus"));
    }

    @Test
    void execute_generatesDetailedTestReport() throws Exception {
        setupPassingCheckRuns();

        String result = testerStep.execute(context);

        Map<String, Object> report = objectMapper.readValue(result, Map.class);
        assertTrue(report.containsKey("backend"));
        assertTrue(report.containsKey("frontend"));
        assertTrue(report.containsKey("e2e"));
        assertTrue(report.containsKey("notes"));
        assertTrue(report.containsKey("timestamp"));

        Map<String, Object> backend = (Map<String, Object>) report.get("backend");
        assertEquals("PASSED", backend.get("status"));
    }

    @Test
    void execute_pollsCheckRunsUntilComplete() throws Exception {
        Map<String, Object> branchRef = Map.of("object", Map.of("sha", "commit-sha"));
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(branchRef);

        List<Map<String, Object>> pendingRuns = List.of(
                createCheckRun(1L, "Tests", "in_progress", null, false)
        );

        List<Map<String, Object>> completedRuns = List.of(
                createCheckRun(1L, "Tests", "completed", "success", false)
        );

        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), eq("commit-sha")))
                .thenReturn(Map.of("check_runs", pendingRuns))
                .thenReturn(Map.of("check_runs", completedRuns));

        String result = testerStep.execute(context);

        assertNotNull(result);
        verify(gitHubApiClient, atLeast(2)).listCheckRunsForRef(eq("owner"), eq("repo"), eq("commit-sha"));
    }

    @Test
    void execute_escalationIncludesOptions() throws Exception {
        setupAlwaysFailingCheckRuns();
        setupFixPatching();

        EscalationException exception = assertThrows(
                EscalationException.class,
                () -> testerStep.execute(context)
        );

        String escalationJson = exception.getEscalationJson();
        Map<String, Object> escalation = objectMapper.readValue(escalationJson, Map.class);

        assertTrue(escalation.containsKey("options"));
        List<Map<String, Object>> options = (List<Map<String, Object>>) escalation.get("options");
        assertFalse(options.isEmpty());

        assertTrue(options.stream().anyMatch(o -> "Manual intervention".equals(o.get("name"))));
        assertTrue(escalation.containsKey("recommendation"));
        assertTrue(escalation.containsKey("decisionNeeded"));
    }

    @Test
    void execute_fixCommitMessageDescriptive() throws Exception {
        setupFailingThenPassingCheckRuns();

        TesterStep.FixPatch fixPatch = new TesterStep.FixPatch();
        fixPatch.setRootCause("Test assertion was incorrect");
        fixPatch.setFixStrategy("Update expected value in test");

        TesterStep.FilePatch filePatch = new TesterStep.FilePatch();
        filePatch.setPath("src/test/java/TestClass.java");
        filePatch.setContent("public class TestClass {}");
        filePatch.setExplanation("Fixed test assertion");
        fixPatch.setFiles(List.of(filePatch));

        String fixJson = objectMapper.writeValueAsString(fixPatch);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(fixJson);

        when(gitHubApiClient.createBlob(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("sha", "blob-sha"));
        when(gitHubApiClient.createTree(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(Map.of("sha", "tree-sha"));
        when(gitHubApiClient.createCommit(anyString(), anyString(), anyString(), anyString(), anyList(), anyMap(), anyMap()))
                .thenReturn(Map.of("sha", "commit-sha"));
        when(gitHubApiClient.updateReference(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(Map.of("ref", "refs/heads/test"));

        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(Map.of("object", Map.of("sha", "commit-sha")));

        String result = testerStep.execute(context);

        assertNotNull(result);
        verify(gitHubApiClient).createCommit(
                eq("owner"),
                eq("repo"),
                contains("Test assertion was incorrect"),
                anyString(),
                anyList(),
                anyMap(),
                anyMap()
        );
    }

    // Helper methods

    private void setupPassingCheckRuns() {
        Map<String, Object> branchRef = Map.of("object", Map.of("sha", "commit-sha"));
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(branchRef);

        List<Map<String, Object>> checkRunsList = List.of(
                createCheckRun(1L, "Backend Tests", "completed", "success", false),
                createCheckRun(2L, "Frontend Lint", "completed", "success", false),
                createCheckRun(3L, "E2E Tests", "completed", "success", true)
        );

        Map<String, Object> checkRuns = Map.of("check_runs", checkRunsList);
        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), eq("commit-sha")))
                .thenReturn(checkRuns);
    }

    private void setupFailingThenPassingCheckRuns() {
        Map<String, Object> branchRef = Map.of("object", Map.of("sha", "commit-sha"));
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(branchRef);

        List<Map<String, Object>> failingRuns = List.of(
                createCheckRun(1L, "Backend Tests", "completed", "failure", false)
        );

        List<Map<String, Object>> passingRuns = List.of(
                createCheckRun(1L, "Backend Tests", "completed", "success", false),
                createCheckRun(2L, "E2E Tests", "completed", "success", true)
        );

        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), eq("commit-sha")))
                .thenReturn(Map.of("check_runs", failingRuns))
                .thenReturn(Map.of("check_runs", passingRuns));

        when(gitHubApiClient.getJobLogs(eq("owner"), eq("repo"), eq(1L)))
                .thenReturn("Test failure logs");
    }

    private void setupAlwaysFailingCheckRuns() {
        Map<String, Object> branchRef = Map.of("object", Map.of("sha", "commit-sha"));
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(branchRef);

        List<Map<String, Object>> failingRuns = List.of(
                createCheckRun(1L, "Backend Tests", "completed", "failure", false)
        );

        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), anyString()))
                .thenReturn(Map.of("check_runs", failingRuns));

        when(gitHubApiClient.getJobLogs(eq("owner"), eq("repo"), eq(1L)))
                .thenReturn("Persistent test failure");
    }

    private void setupCiPassingE2eFailingOnce() {
        Map<String, Object> branchRef = Map.of("object", Map.of("sha", "commit-sha"));
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(branchRef);

        List<Map<String, Object>> ciPassE2eFail = List.of(
                createCheckRun(1L, "Backend Tests", "completed", "success", false),
                createCheckRun(2L, "E2E Tests", "completed", "failure", true)
        );

        List<Map<String, Object>> allPass = List.of(
                createCheckRun(1L, "Backend Tests", "completed", "success", false),
                createCheckRun(2L, "E2E Tests", "completed", "success", true)
        );

        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), eq("commit-sha")))
                .thenReturn(Map.of("check_runs", ciPassE2eFail))
                .thenReturn(Map.of("check_runs", allPass));

        when(gitHubApiClient.getJobLogs(eq("owner"), eq("repo"), eq(2L)))
                .thenReturn("E2E test failure logs");
    }

    private void setupCiPassingE2eAlwaysFailing() {
        Map<String, Object> branchRef = Map.of("object", Map.of("sha", "commit-sha"));
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(branchRef);

        List<Map<String, Object>> checkRuns = List.of(
                createCheckRun(1L, "Backend Tests", "completed", "success", false),
                createCheckRun(2L, "E2E Tests", "completed", "failure", true)
        );

        when(gitHubApiClient.listCheckRunsForRef(eq("owner"), eq("repo"), anyString()))
                .thenReturn(Map.of("check_runs", checkRuns));

        when(gitHubApiClient.getJobLogs(eq("owner"), eq("repo"), eq(2L)))
                .thenReturn("E2E test failure logs");
    }

    private void setupFixPatching() {
        TesterStep.FixPatch fixPatch = new TesterStep.FixPatch();
        fixPatch.setRootCause("Test issue");
        fixPatch.setFixStrategy("Fix the issue");

        TesterStep.FilePatch filePatch = new TesterStep.FilePatch();
        filePatch.setPath("src/test/Test.java");
        filePatch.setContent("fixed content");
        filePatch.setExplanation("Fixed");
        fixPatch.setFiles(List.of(filePatch));

        try {
            String fixJson = objectMapper.writeValueAsString(fixPatch);
            when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                    .thenReturn(fixJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(gitHubApiClient.createBlob(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("sha", "blob-sha"));
        when(gitHubApiClient.createTree(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(Map.of("sha", "tree-sha"));
        when(gitHubApiClient.createCommit(anyString(), anyString(), anyString(), anyString(), anyList(), anyMap(), anyMap()))
                .thenReturn(Map.of("sha", "commit-sha"));
        when(gitHubApiClient.updateReference(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(Map.of("ref", "refs/heads/test"));
    }

    private Map<String, Object> createCheckRun(Long id, String name, String status, String conclusion, boolean isE2e) {
        Map<String, Object> checkRun = new HashMap<>();
        checkRun.put("id", id);
        checkRun.put("name", name);
        checkRun.put("status", status);
        checkRun.put("conclusion", conclusion);
        return checkRun;
    }

    private Map<String, Object> createCheckRunsWithLogs(String name, String conclusion, String logs) {
        Map<String, Object> checkRun = createCheckRun(1L, name, "completed", conclusion, false);
        return Map.of("check_runs", List.of(checkRun));
    }
}
