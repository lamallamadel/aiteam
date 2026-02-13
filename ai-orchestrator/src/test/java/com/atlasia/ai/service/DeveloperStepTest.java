package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeveloperStepTest {

        @Mock
        private GitHubApiClient gitHubApiClient;

        @Mock
        private LlmService llmService;

        @Mock
        private OrchestratorProperties properties;

        private ObjectMapper objectMapper;
        private DeveloperStep developerStep;
        private RunContext context;
        private RunEntity runEntity;

        @BeforeEach
        void setUp() {
                objectMapper = new ObjectMapper();
                lenient().when(properties.repoAllowlist()).thenReturn("src/,docs/,pom.xml");
                lenient().when(properties.workflowProtectPrefix()).thenReturn(".github/workflows/");

                developerStep = new DeveloperStep(gitHubApiClient, llmService, objectMapper, properties);

                runEntity = new RunEntity(
                                UUID.randomUUID(),
                                "owner/repo",
                                123,
                                "full",
                                RunStatus.DEVELOPER,
                                Instant.now());

                context = new RunContext(runEntity, "owner", "repo");
                context.setBranchName("ai/issue-123");

                Map<String, Object> issueData = new HashMap<>();
                issueData.put("title", "Add user authentication");
                issueData.put("body", "Implement user authentication with JWT");
                context.setIssueData(issueData);
        }

        @Test
        void testExecute_createsSuccessfulImplementation() throws Exception {
                // Setup mocks for GitHub API calls
                Map<String, Object> mainRef = Map.of("object", Map.of("sha", "main-sha-123"));
                when(gitHubApiClient.getReference("owner", "repo", "heads/main"))
                                .thenReturn(mainRef);

                when(gitHubApiClient.getReference("owner", "repo", "heads/ai/issue-123"))
                                .thenThrow(new RuntimeException("Branch does not exist"));

                when(gitHubApiClient.createBranch("owner", "repo", "ai/issue-123", "main-sha-123"))
                                .thenReturn(Map.of("ref", "refs/heads/ai/issue-123"));

                Map<String, Object> tree = Map.of(
                                "sha", "tree-sha",
                                "tree", List.of(
                                                Map.of("path", "src/main/java/Example.java", "type", "blob"),
                                                Map.of("path", "AGENTS.md", "type", "blob")));
                when(gitHubApiClient.getRepoTree("owner", "repo", "main-sha-123", true))
                                .thenReturn(tree);

                when(gitHubApiClient.getRepoContent(eq("owner"), eq("repo"), anyString()))
                                .thenReturn(Map.of("content",
                                                Base64.getEncoder().encodeToString("file content".getBytes())));

                // Mock LLM response
                String llmResponse = """
                                {
                                    "summary": "Added authentication service and controller",
                                    "files": [
                                        {
                                            "path": "src/main/java/AuthService.java",
                                            "operation": "create",
                                            "content": "public class AuthService {}",
                                            "explanation": "Authentication service"
                                        }
                                    ],
                                    "testingNotes": "Added unit tests",
                                    "implementationNotes": "Follows existing patterns"
                                }
                                """;
                when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                when(gitHubApiClient.createBlob(eq("owner"), eq("repo"), anyString(), eq("utf-8")))
                                .thenReturn(Map.of("sha", "blob-sha-123"));

                when(gitHubApiClient.createTree(eq("owner"), eq("repo"), anyList(), eq("main-sha-123")))
                                .thenReturn(Map.of("sha", "new-tree-sha"));

                when(gitHubApiClient.createCommit(
                                eq("owner"), eq("repo"), anyString(), eq("new-tree-sha"),
                                eq(List.of("main-sha-123")), anyMap(), anyMap()))
                                .thenReturn(Map.of("sha", "commit-sha-123"));

                when(gitHubApiClient.updateReference(eq("owner"), eq("repo"),
                                eq("heads/ai/issue-123"), eq("commit-sha-123"), eq(false)))
                                .thenReturn(Map.of("ref", "refs/heads/ai/issue-123"));

                when(gitHubApiClient.createPullRequest(
                                eq("owner"), eq("repo"), anyString(), eq("ai/issue-123"), eq("main"), anyString()))
                                .thenReturn(Map.of("html_url", "https://github.com/owner/repo/pull/1"));

                // Execute
                String result = developerStep.execute(context);

                // Verify
                assertEquals("https://github.com/owner/repo/pull/1", result);
                assertEquals("https://github.com/owner/repo/pull/1", context.getPrUrl());

                verify(gitHubApiClient).createBranch("owner", "repo", "ai/issue-123", "main-sha-123");
                verify(llmService).generateStructuredOutput(anyString(), anyString(), anyMap());
                verify(gitHubApiClient).createBlob(eq("owner"), eq("repo"), anyString(), eq("utf-8"));
                verify(gitHubApiClient).createTree(eq("owner"), eq("repo"), anyList(), eq("main-sha-123"));
                verify(gitHubApiClient).createCommit(
                                eq("owner"), eq("repo"), anyString(), eq("new-tree-sha"),
                                anyList(), anyMap(), anyMap());
                verify(gitHubApiClient).createPullRequest(
                                eq("owner"), eq("repo"), anyString(), eq("ai/issue-123"), eq("main"), anyString());
        }

        @Test
        void testExecute_handlesConflictsOnExistingBranch() throws Exception {
                // Branch already exists
                Map<String, Object> mainRef = Map.of("object", Map.of("sha", "main-sha-123"));
                when(gitHubApiClient.getReference("owner", "repo", "heads/main"))
                                .thenReturn(mainRef);

                Map<String, Object> existingBranchRef = Map.of("object", Map.of("sha", "old-branch-sha"));
                when(gitHubApiClient.getReference("owner", "repo", "heads/ai/issue-123"))
                                .thenReturn(existingBranchRef);

                Map<String, Object> comparison = Map.of("ahead_by", 1, "behind_by", 2);
                when(gitHubApiClient.compareCommits("owner", "repo", "main-sha-123", "old-branch-sha"))
                                .thenReturn(comparison);

                lenient().when(gitHubApiClient.updateReference("owner", "repo", "heads/ai/issue-123", "main-sha-123",
                                true))
                                .thenReturn(Map.of("ref", "refs/heads/ai/issue-123"));

                when(gitHubApiClient.createBranch("owner", "repo", "ai/issue-123", "main-sha-123"))
                                .thenReturn(Map.of("ref", "refs/heads/ai/issue-123"));

                setupSuccessfulExecution();

                // Execute
                String result = developerStep.execute(context);

                // Verify conflict resolution
                verify(gitHubApiClient).compareCommits("owner", "repo", "main-sha-123", "old-branch-sha");
                verify(gitHubApiClient).updateReference("owner", "repo", "heads/ai/issue-123", "main-sha-123", true);
                assertNotNull(result);
        }

        @Test
        void testValidateCodeChanges_rejectsProtectedWorkflowFiles() {
                DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
                codeChanges.setSummary("Test");
                codeChanges.setTestingNotes("Test");
                codeChanges.setImplementationNotes("Test");

                List<DeveloperStep.FileChange> files = new ArrayList<>();
                DeveloperStep.FileChange file = new DeveloperStep.FileChange();
                file.setPath(".github/workflows/ci.yml");
                file.setOperation("modify");
                file.setContent("workflow content");
                file.setExplanation("Modified workflow");
                files.add(file);

                codeChanges.setFiles(files);

                // Should throw exception for protected workflow
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> invokeValidateCodeChanges(codeChanges));
                assertTrue(exception.getMessage().contains("protected workflow"));
        }

        @Test
        void testValidateCodeChanges_rejectsFileNotInAllowlist() {
                DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
                codeChanges.setSummary("Test");
                codeChanges.setTestingNotes("Test");
                codeChanges.setImplementationNotes("Test");

                List<DeveloperStep.FileChange> files = new ArrayList<>();
                DeveloperStep.FileChange file = new DeveloperStep.FileChange();
                file.setPath("random/unauthorized/file.txt");
                file.setOperation("create");
                file.setContent("content");
                file.setExplanation("Test file");
                files.add(file);

                codeChanges.setFiles(files);

                // Should throw exception for file not in allowlist
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> invokeValidateCodeChanges(codeChanges));
                assertTrue(exception.getMessage().contains("not in allowlist"));
        }

        @Test
        void testValidateCodeChanges_acceptsValidFiles() {
                DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
                codeChanges.setSummary("Test");
                codeChanges.setTestingNotes("Test");
                codeChanges.setImplementationNotes("Test");

                List<DeveloperStep.FileChange> files = new ArrayList<>();

                DeveloperStep.FileChange file1 = new DeveloperStep.FileChange();
                file1.setPath("src/main/java/Test.java");
                file1.setOperation("create");
                file1.setContent("public class Test {}");
                file1.setExplanation("Test class");
                files.add(file1);

                DeveloperStep.FileChange file2 = new DeveloperStep.FileChange();
                file2.setPath("docs/README.md");
                file2.setOperation("modify");
                file2.setContent("# Documentation");
                file2.setExplanation("Updated docs");
                files.add(file2);

                codeChanges.setFiles(files);

                // Should not throw exception
                assertDoesNotThrow(() -> invokeValidateCodeChanges(codeChanges));
        }

        @Test
        void testValidateCodeChanges_rejectsPathTraversal() {
                DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
                codeChanges.setSummary("Test");
                codeChanges.setTestingNotes("Test");
                codeChanges.setImplementationNotes("Test");

                List<DeveloperStep.FileChange> files = new ArrayList<>();
                DeveloperStep.FileChange file = new DeveloperStep.FileChange();
                file.setPath("src/../../../etc/passwd");
                file.setOperation("create");
                file.setContent("malicious");
                file.setExplanation("Bad file");
                files.add(file);

                codeChanges.setFiles(files);

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> invokeValidateCodeChanges(codeChanges));
                assertTrue(exception.getMessage().contains("Invalid file path"));
        }

        @Test
        void testValidateCodeChanges_rejectsAbsolutePaths() {
                DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
                codeChanges.setSummary("Test");
                codeChanges.setTestingNotes("Test");
                codeChanges.setImplementationNotes("Test");

                List<DeveloperStep.FileChange> files = new ArrayList<>();
                DeveloperStep.FileChange file = new DeveloperStep.FileChange();
                file.setPath("/etc/passwd");
                file.setOperation("create");
                file.setContent("malicious");
                file.setExplanation("Bad file");
                files.add(file);

                codeChanges.setFiles(files);

                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> invokeValidateCodeChanges(codeChanges));
                assertTrue(exception.getMessage().contains("Invalid file path"));
        }

        @Test
        void testBuildCommitMessage_determinesCorrectIssueType() throws Exception {
                Map<String, Object> issueData = new HashMap<>();

                // Test bug fix
                issueData.put("title", "Fix login bug");
                issueData.put("body", "The login is broken");
                context.setIssueData(issueData);

                lenient().when(gitHubApiClient.createPullRequest(anyString(), anyString(), anyString(), anyString(),
                                anyString(),
                                anyString()))
                                .thenReturn(Map.of("html_url", "https://github.com/owner/repo/pull/1"));

                String commitMessage = (String) invokePrivateMethod("buildCommitMessage",
                                new Class[] { RunContext.class, DeveloperStep.CodeChanges.class },
                                context,
                                createTestCodeChanges());
                assertTrue(commitMessage.startsWith("fix:"));

                // Test documentation
                issueData.put("title", "Update documentation");
                issueData.put("body", "Add new docs");
                commitMessage = invokePrivateMethod(
                                "buildCommitMessage",
                                new Class[] { RunContext.class, DeveloperStep.CodeChanges.class },
                                context,
                                createTestCodeChanges());
                assertTrue(commitMessage.startsWith("docs:"));

                // Test feature
                issueData.put("title", "Add new feature");
                issueData.put("body", "Implement new functionality");
                commitMessage = invokePrivateMethod(
                                "buildCommitMessage",
                                new Class[] { RunContext.class, DeveloperStep.CodeChanges.class },
                                context,
                                createTestCodeChanges());
                assertTrue(commitMessage.startsWith("feat:"));
        }

        @Test
        void testMultiFileChanges_createsCorrectTreeStructure() throws Exception {
                DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
                codeChanges.setSummary("Multiple file changes");
                codeChanges.setTestingNotes("All tested");
                codeChanges.setImplementationNotes("Clean implementation");

                List<DeveloperStep.FileChange> files = new ArrayList<>();

                DeveloperStep.FileChange file1 = new DeveloperStep.FileChange();
                file1.setPath("src/main/java/Service.java");
                file1.setOperation("create");
                file1.setContent("public class Service {}");
                file1.setExplanation("Service class");
                files.add(file1);

                DeveloperStep.FileChange file2 = new DeveloperStep.FileChange();
                file2.setPath("src/test/java/ServiceTest.java");
                file2.setOperation("create");
                file2.setContent("public class ServiceTest {}");
                file2.setExplanation("Test class");
                files.add(file2);

                DeveloperStep.FileChange file3 = new DeveloperStep.FileChange();
                file3.setPath("docs/old-file.md");
                file3.setOperation("delete");
                file3.setContent("");
                file3.setExplanation("Removed obsolete doc");
                files.add(file3);

                codeChanges.setFiles(files);

                setupGitHubTreeMocks();

                // Execute
                String commitSha = invokePrivateMethod(
                                "applyMultiFileChanges",
                                new Class[] { RunContext.class, String.class, String.class, String.class, String.class,
                                                DeveloperStep.CodeChanges.class },
                                context,
                                "owner",
                                "repo",
                                "ai/issue-123",
                                "base-sha",
                                codeChanges);

                assertEquals("commit-sha-123", commitSha);

                // Verify tree creation with correct entries
                ArgumentCaptor<List<Map<String, Object>>> treeCaptor = ArgumentCaptor.forClass(List.class);
                verify(gitHubApiClient).createTree(eq("owner"), eq("repo"), treeCaptor.capture(), eq("base-sha"));

                List<Map<String, Object>> treeEntries = treeCaptor.getValue();
                assertEquals(3, treeEntries.size());

                // Verify create operations have blob SHAs
                assertTrue(treeEntries.stream()
                                .filter(e -> e.get("path").equals("src/main/java/Service.java"))
                                .allMatch(e -> e.get("sha").equals("blob-sha-123")));

                // Verify delete operation has null SHA
                assertTrue(treeEntries.stream()
                                .filter(e -> e.get("path").equals("docs/old-file.md"))
                                .allMatch(e -> e.get("sha") == null));
        }

        @Test
        void testLlmFallback_createsFallbackImplementation() throws Exception {
                Map<String, Object> mainRef = Map.of("object", Map.of("sha", "main-sha-123"));
                lenient().when(gitHubApiClient.getReference("owner", "repo", "heads/main"))
                                .thenReturn(mainRef);

                lenient().when(gitHubApiClient.getReference("owner", "repo", "heads/ai/issue-123"))
                                .thenThrow(new RuntimeException("Branch does not exist"));

                lenient().when(gitHubApiClient.createBranch(anyString(), anyString(), anyString(), anyString()))
                                .thenReturn(Map.of("ref", "refs/heads/ai/issue-123"));

                lenient().when(gitHubApiClient.getRepoTree(anyString(), anyString(), anyString(), anyBoolean()))
                                .thenReturn(Map.of("sha", "tree-sha", "tree", List.of()));

                // LLM fails
                lenient().when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenThrow(new RuntimeException("LLM service unavailable"));

                setupGitHubTreeMocks();

                // Execute should still succeed with fallback
                String result = developerStep.execute(context);

                // Verify fallback was used
                assertNotNull(result);
                verify(gitHubApiClient).createBlob(eq("owner"), eq("repo"), contains("Implementation Plan"),
                                eq("utf-8"));
        }

        @Test
        void testCommitAttribution_includesProperAuthorAndCommitter() throws Exception {
                DeveloperStep.CodeChanges codeChanges = createTestCodeChanges();
                setupGitHubTreeMocks();

                invokePrivateMethod(
                                "applyMultiFileChanges",
                                new Class[] { RunContext.class, String.class, String.class, String.class, String.class,
                                                DeveloperStep.CodeChanges.class },
                                context,
                                "owner",
                                "repo",
                                "ai/issue-123",
                                "base-sha",
                                codeChanges);

                ArgumentCaptor<Map<String, Object>> authorCaptor = ArgumentCaptor.forClass(Map.class);
                ArgumentCaptor<Map<String, Object>> committerCaptor = ArgumentCaptor.forClass(Map.class);

                verify(gitHubApiClient).createCommit(
                                eq("owner"),
                                eq("repo"),
                                anyString(),
                                anyString(),
                                anyList(),
                                authorCaptor.capture(),
                                committerCaptor.capture());

                Map<String, Object> author = authorCaptor.getValue();
                assertEquals("Atlasia AI Bot", author.get("name"));
                assertEquals("ai-bot@atlasia.io", author.get("email"));
                assertNotNull(author.get("date"));

                Map<String, Object> committer = committerCaptor.getValue();
                assertEquals("Atlasia AI Bot", committer.get("name"));
                assertEquals("ai-bot@atlasia.io", committer.get("email"));
                assertNotNull(committer.get("date"));
        }

        @Test
        void testReasoningTraceStorage() throws Exception {
                setupSuccessfulExecution();

                developerStep.execute(context);

                // Verify reasoning trace was stored
                List<com.atlasia.ai.model.RunArtifactEntity> artifacts = runEntity.getArtifacts();
                assertEquals(1, artifacts.size());

                com.atlasia.ai.model.RunArtifactEntity artifact = artifacts.get(0);
                assertEquals("developer", artifact.getAgentName());
                assertEquals("reasoning_trace", artifact.getArtifactType());
                assertNotNull(artifact.getPayload());
                assertTrue(artifact.getPayload().contains("code_generation"));
        }

        // Helper methods

        private void setupSuccessfulExecution() throws Exception {
                lenient().when(gitHubApiClient.getRepoTree(anyString(), anyString(), anyString(), anyBoolean()))
                                .thenReturn(Map.of("sha", "tree-sha", "tree", List.of()));

                String llmResponse = """
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
                                    "testingNotes": "Tests added",
                                    "implementationNotes": "Following patterns"
                                }
                                """;
                lenient().when(llmService.generateStructuredOutput(anyString(), anyString(), anyMap()))
                                .thenReturn(llmResponse);

                setupGitHubTreeMocks();

                lenient().when(gitHubApiClient.createPullRequest(anyString(), anyString(), anyString(), anyString(),
                                anyString(),
                                anyString()))
                                .thenReturn(Map.of("html_url", "https://github.com/owner/repo/pull/1"));
        }

        private void setupGitHubTreeMocks() {
                lenient().when(gitHubApiClient.createBlob(anyString(), anyString(), anyString(), anyString()))
                                .thenReturn(Map.of("sha", "blob-sha-123"));

                lenient().when(gitHubApiClient.createTree(anyString(), anyString(), anyList(), anyString()))
                                .thenReturn(Map.of("sha", "new-tree-sha"));

                lenient().when(gitHubApiClient.createCommit(anyString(), anyString(), anyString(), anyString(),
                                anyList(),
                                anyMap(), anyMap()))
                                .thenReturn(Map.of("sha", "commit-sha-123"));

                lenient().when(gitHubApiClient.updateReference(anyString(), anyString(), anyString(), anyString(),
                                anyBoolean()))
                                .thenReturn(Map.of("ref", "refs/heads/test"));
        }

        private DeveloperStep.CodeChanges createTestCodeChanges() {
                DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
                codeChanges.setSummary("Test changes");
                codeChanges.setTestingNotes("All tests pass");
                codeChanges.setImplementationNotes("Clean code");

                List<DeveloperStep.FileChange> files = new ArrayList<>();
                DeveloperStep.FileChange file = new DeveloperStep.FileChange();
                file.setPath("src/main/java/Test.java");
                file.setOperation("create");
                file.setContent("public class Test {}");
                file.setExplanation("Test class");
                files.add(file);

                codeChanges.setFiles(files);
                return codeChanges;
        }

        private void invokeValidateCodeChanges(DeveloperStep.CodeChanges codeChanges) throws Exception {
                java.lang.reflect.Method method = DeveloperStep.class.getDeclaredMethod(
                                "validateCodeChanges",
                                DeveloperStep.CodeChanges.class);
                method.setAccessible(true);
                try {
                        method.invoke(developerStep, codeChanges);
                } catch (java.lang.reflect.InvocationTargetException e) {
                        if (e.getCause() instanceof IllegalArgumentException) {
                                throw (IllegalArgumentException) e.getCause();
                        }
                        if (e.getCause() instanceof RuntimeException) {
                                throw (RuntimeException) e.getCause();
                        }
                        throw e;
                }
        }

        @SuppressWarnings("unchecked")
        private <T> T invokePrivateMethod(String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
                java.lang.reflect.Method method = DeveloperStep.class.getDeclaredMethod(methodName, paramTypes);
                method.setAccessible(true);
                try {
                        return (T) method.invoke(developerStep, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                        throw new IllegalArgumentException(
                                        e.getCause() != null ? e.getCause().getMessage() : e.getMessage(),
                                        e.getCause());
                }
        }
}
