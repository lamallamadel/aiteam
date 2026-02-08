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
class WriterStepTest {

    @Mock
    private GitHubApiClient gitHubApiClient;

    @Mock
    private LlmService llmService;

    private ObjectMapper objectMapper;
    private WriterStep writerStep;
    private RunContext context;
    private RunEntity runEntity;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        writerStep = new WriterStep(gitHubApiClient, llmService, objectMapper);

        runEntity = new RunEntity(
                UUID.randomUUID(),
                "owner/repo",
                123,
                "full",
                RunStatus.WRITER,
                Instant.now());

        context = new RunContext(runEntity, "owner", "repo");
        context.setBranchName("ai/issue-123");
        context.setPrUrl("https://github.com/owner/repo/pull/1");

        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Add new feature");
        issueData.put("body", "Implement user authentication");
        context.setIssueData(issueData);

        setupGitHubMocks();
    }

    @Test
    void execute_generatesChangelog() throws Exception {
        setupLlmMocks();

        String result = writerStep.execute(context);

        assertNotNull(result);
        assertTrue(result.contains("Documentation updated"));
        verify(llmService, atLeastOnce()).generateStructuredOutput(anyString(), contains("Changelog"), anyMap());
    }

    @Test
    void execute_createsChangelogArtifact() throws Exception {
        setupLlmMocks();

        writerStep.execute(context);

        verify(gitHubApiClient).createBlob(eq("owner"), eq("repo"), anyString(), eq("base64"));
        verify(gitHubApiClient).createTree(eq("owner"), eq("repo"), anyList(), anyString());
        verify(gitHubApiClient).createCommit(
                eq("owner"),
                eq("repo"),
                contains("Update documentation"),
                anyString(),
                anyList(),
                anyMap(),
                anyMap());
    }

    @Test
    void execute_detectsBreakingChanges() throws Exception {
        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Breaking change");
        issueData.put("body", "This is a breaking change that affects the API");
        context.setIssueData(issueData);

        setupLlmMocks();

        writerStep.execute(context);

        verify(llmService).generateStructuredOutput(
                anyString(),
                contains("breaking"),
                anyMap());
    }

    @Test
    void execute_generatesReadmeUpdates() throws Exception {
        context.setWorkPlan("{\"tasks\":[{\"area\":\"docs\",\"description\":\"Update README\"}]}");

        WriterStep.ReadmeUpdate readmeUpdate = new WriterStep.ReadmeUpdate();
        readmeUpdate.setShouldUpdate(true);

        WriterStep.SectionUpdate sectionUpdate = new WriterStep.SectionUpdate();
        sectionUpdate.setSectionName("Installation");
        sectionUpdate.setUpdateType("modify");
        sectionUpdate.setNewContent("Updated installation instructions");
        sectionUpdate.setReason("New dependencies added");
        readmeUpdate.setSectionsToUpdate(List.of(sectionUpdate));

        String readmeJson = objectMapper.writeValueAsString(readmeUpdate);
        when(llmService.generateStructuredOutput(anyString(), contains("README"), anyMap()))
                .thenReturn(readmeJson);

        setupChangelogMock();

        writerStep.execute(context);

        verify(llmService).generateStructuredOutput(anyString(), contains("README"), anyMap());
    }

    @Test
    void execute_validatesMarkdownFormatting() throws Exception {
        WriterStep.ChangelogContent changelog = new WriterStep.ChangelogContent();
        changelog.setSummary("Test summary");
        changelog.setAdded(List.of("New feature"));
        changelog.setChanged(List.of());
        changelog.setDeprecated(List.of());
        changelog.setRemoved(List.of());
        changelog.setFixed(List.of());
        changelog.setSecurity(List.of());
        changelog.setTechnicalDetails("Technical details");
        changelog.setBreakingChanges(List.of());
        changelog.setMigrationGuide("");

        String changelogJson = objectMapper.writeValueAsString(changelog);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(changelogJson);

        assertDoesNotThrow(() -> writerStep.execute(context));
    }

    @Test
    void execute_storesDocumentationArtifact() throws Exception {
        setupLlmMocks();

        writerStep.execute(context);

        assertEquals(1, runEntity.getArtifacts().size());
        com.atlasia.ai.model.RunArtifactEntity artifact = runEntity.getArtifacts().get(0);
        assertEquals("writer", artifact.getAgentName());
        assertEquals("documentation", artifact.getArtifactType());
        assertTrue(artifact.getPayload().contains("documentation"));
    }

    @Test
    void execute_handlesLlmFailureGracefully() throws Exception {
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        assertDoesNotThrow(() -> writerStep.execute(context));

        verify(gitHubApiClient).createBlob(eq("owner"), eq("repo"), anyString(), eq("base64"));
    }

    @Test
    void execute_includesPrUrlInChangelog() throws Exception {
        setupLlmMocks();

        writerStep.execute(context);

        verify(gitHubApiClient).createBlob(
                eq("owner"),
                eq("repo"),
                contains("https://github.com/owner/repo/pull/1"),
                eq("base64"));
    }

    @Test
    void execute_categorizesByChangeType() throws Exception {
        WriterStep.ChangelogContent changelog = new WriterStep.ChangelogContent();
        changelog.setSummary("Test changes");
        changelog.setAdded(List.of("New authentication system"));
        changelog.setChanged(List.of("Updated API endpoints"));
        changelog.setFixed(List.of("Fixed login bug"));
        changelog.setDeprecated(List.of("Old API deprecated"));
        changelog.setRemoved(List.of());
        changelog.setSecurity(List.of("Fixed XSS vulnerability"));
        changelog.setTechnicalDetails("Implemented using JWT");
        changelog.setBreakingChanges(List.of("API v1 removed"));
        changelog.setMigrationGuide("Migrate to API v2");

        String changelogJson = objectMapper.writeValueAsString(changelog);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(changelogJson);

        writerStep.execute(context);

        verify(gitHubApiClient).createBlob(
                eq("owner"),
                eq("repo"),
                argThat(content -> content.contains("Added") &&
                        content.contains("Changed") &&
                        content.contains("Fixed") &&
                        content.contains("Security") &&
                        content.contains("BREAKING CHANGES")),
                eq("base64"));
    }

    @Test
    void execute_includesMigrationGuideForBreakingChanges() throws Exception {
        WriterStep.ChangelogContent changelog = new WriterStep.ChangelogContent();
        changelog.setSummary("Breaking changes");
        changelog.setAdded(List.of());
        changelog.setChanged(List.of());
        changelog.setDeprecated(List.of());
        changelog.setRemoved(List.of());
        changelog.setFixed(List.of());
        changelog.setSecurity(List.of());
        changelog.setTechnicalDetails("Details");
        changelog.setBreakingChanges(List.of("API signature changed"));
        changelog.setMigrationGuide("Update your API calls to use the new signature");

        String changelogJson = objectMapper.writeValueAsString(changelog);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(changelogJson);

        writerStep.execute(context);

        verify(gitHubApiClient).createBlob(
                eq("owner"),
                eq("repo"),
                contains("Migration Guide"),
                eq("base64"));
    }

    @Test
    void execute_detectsApiDocumentationNeed() throws Exception {
        context.setWorkPlan("{\"tasks\":[{\"area\":\"backend\",\"description\":\"Add API endpoint\"}]}");

        setupLlmMocks();

        writerStep.execute(context);

        verify(llmService, atLeastOnce()).generateStructuredOutput(anyString(), anyString(), anyMap());
    }

    @Test
    void execute_truncatesLongContent() throws Exception {
        StringBuilder longBody = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longBody.append("This is a very long body. ");
        }

        Map<String, Object> issueData = new HashMap<>();
        issueData.put("title", "Long issue");
        issueData.put("body", longBody.toString());
        context.setIssueData(issueData);

        setupLlmMocks();

        assertDoesNotThrow(() -> writerStep.execute(context));
    }

    @Test
    void execute_validatesUnclosedCodeBlocks() throws Exception {
        WriterStep.ChangelogContent changelog = new WriterStep.ChangelogContent();
        changelog.setSummary("Test");
        changelog.setAdded(List.of());
        changelog.setChanged(List.of());
        changelog.setDeprecated(List.of());
        changelog.setRemoved(List.of());
        changelog.setFixed(List.of());
        changelog.setSecurity(List.of());
        changelog.setTechnicalDetails("Some code: ```java\npublic class Test {}");
        changelog.setBreakingChanges(List.of());
        changelog.setMigrationGuide("");

        String changelogJson = objectMapper.writeValueAsString(changelog);
        when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                .thenReturn(changelogJson);

        assertDoesNotThrow(() -> writerStep.execute(context));
    }

    // Helper methods

    private void setupGitHubMocks() {
        when(gitHubApiClient.getReference(eq("owner"), eq("repo"), eq("heads/ai/issue-123")))
                .thenReturn(Map.of("object", Map.of("sha", "commit-sha")));

        when(gitHubApiClient.getRepoContent(eq("owner"), eq("repo"), eq("README.md")))
                .thenReturn(Map.of(
                        "content", Base64.getEncoder().encodeToString("# README\n\nExisting content".getBytes()),
                        "sha", "readme-sha"));

        when(gitHubApiClient.createBlob(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Map.of("sha", "blob-sha"));

        when(gitHubApiClient.createTree(anyString(), anyString(), anyList(), anyString()))
                .thenReturn(Map.of("sha", "tree-sha"));

        when(gitHubApiClient.createCommit(anyString(), anyString(), anyString(), anyString(), anyList(), anyMap(),
                anyMap()))
                .thenReturn(Map.of("sha", "commit-sha-new"));

        when(gitHubApiClient.updateReference(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
                .thenReturn(Map.of("ref", "refs/heads/ai/issue-123"));
    }

    private void setupLlmMocks() {
        WriterStep.ChangelogContent changelog = new WriterStep.ChangelogContent();
        changelog.setSummary("Implemented user authentication");
        changelog.setAdded(List.of("User authentication system"));
        changelog.setChanged(List.of());
        changelog.setDeprecated(List.of());
        changelog.setRemoved(List.of());
        changelog.setFixed(List.of());
        changelog.setSecurity(List.of());
        changelog.setTechnicalDetails("Uses JWT tokens for session management");
        changelog.setBreakingChanges(List.of());
        changelog.setMigrationGuide("");

        try {
            String changelogJson = objectMapper.writeValueAsString(changelog);
            when(llmService.generateStructuredOutput(anyString(), contains("Changelog"), anyMap()))
                    .thenReturn(changelogJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        setupReadmeUpdateMock();
    }

    private void setupReadmeUpdateMock() {
        WriterStep.ReadmeUpdate readmeUpdate = new WriterStep.ReadmeUpdate();
        readmeUpdate.setShouldUpdate(false);
        readmeUpdate.setSectionsToUpdate(List.of());

        try {
            String readmeJson = objectMapper.writeValueAsString(readmeUpdate);
            when(llmService.generateStructuredOutput(anyString(), contains("README"), anyMap()))
                    .thenReturn(readmeJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String codeDocPrompt = "Documentation guide for affected code";
        when(llmService.generateCompletion(anyString(), anyString()))
                .thenReturn(codeDocPrompt);
    }

    private void setupChangelogMock() {
        WriterStep.ChangelogContent changelog = new WriterStep.ChangelogContent();
        changelog.setSummary("Test");
        changelog.setAdded(List.of());
        changelog.setChanged(List.of());
        changelog.setDeprecated(List.of());
        changelog.setRemoved(List.of());
        changelog.setFixed(List.of());
        changelog.setSecurity(List.of());
        changelog.setTechnicalDetails("Details");
        changelog.setBreakingChanges(List.of());
        changelog.setMigrationGuide("");

        try {
            String changelogJson = objectMapper.writeValueAsString(changelog);
            when(llmService.generateStructuredOutput(anyString(), contains("Changelog"), anyMap()))
                    .thenReturn(changelogJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
