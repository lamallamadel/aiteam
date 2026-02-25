package com.atlasia.ai.e2e.scenarios;

import com.atlasia.ai.api.RunRequest;
import com.atlasia.ai.e2e.configuration.AbstractE2ETest;
import com.atlasia.ai.model.RoleEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.JwtService;
import com.github.tomakehurst.wiremock.client.WireMock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class HappyPathCodeGenerationE2ETest extends AbstractE2ETest {

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private RunRepository runRepository;

        @Autowired
        private JwtService jwtService;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        private String validJwt;

        @BeforeEach
        void setUp() {
                WireMock.reset();

                // Clean database
                jdbcTemplate.execute("DELETE FROM ai_trace_event; DELETE FROM ai_run_artifact; DELETE FROM ai_run;");
        }

        @Test
        void testAutonomousCodeGeneration() {
                // --- 1. MOCK GITHUB API ---

                // Validate Token (GitHub API /user)
                stubFor(get(urlPathEqualTo("/user"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"login\": \"test-app\"}")));

                // Fetch Issue
                stubFor(get(urlPathEqualTo("/repos/test-owner/test-repo/issues/123"))
                                .willReturn(aResponse()
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"title\": \"Create hello world endpoint\", \"body\": \"Just make a simple GET endpoint\"}")));

                // Fetch References (Base Branch)
                stubFor(get(urlPathMatching("/repos/test-owner/test-repo/git/ref/heads.*main"))
                                .willReturn(aResponse()
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"object\": {\"sha\": \"main-sha-123\"}}")));

                // 404 for feature branch existence initially
                stubFor(get(urlPathMatching("/repos/test-owner/test-repo/git/ref/heads.*ai.*issue-123.*"))
                                .inScenario("Branch Creation")
                                .whenScenarioStateIs("Started")
                                .willReturn(aResponse().withStatus(404)));

                // Create Branch (refs)
                stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/git/refs"))
                                .inScenario("Branch Creation")
                                .willSetStateTo("Branch Created")
                                .willReturn(aResponse()
                                                .withStatus(201)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"ref\": \"refs/heads/ai/issue-123\"}")));

                // 200 for feature branch existence later (for TesterStep)
                stubFor(get(urlPathMatching("/repos/test-owner/test-repo/git/ref/heads.*ai.*issue-123.*"))
                                .inScenario("Branch Creation")
                                .whenScenarioStateIs("Branch Created")
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"object\": {\"sha\": \"commit-sha\"}}")));

                // Get Tree
                stubFor(get(urlPathMatching("/repos/test-owner/test-repo/git/trees/.*"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sha\": \"tree-sha\", \"tree\": []}")));

                // Create Blob
                stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/git/blobs"))
                                .willReturn(aResponse()
                                                .withStatus(201)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sha\": \"blob-sha\"}")));

                // Create Tree
                stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/git/trees"))
                                .willReturn(aResponse()
                                                .withStatus(201)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sha\": \"new-tree-sha\"}")));

                // Create Commit
                stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/git/commits"))
                                .willReturn(aResponse()
                                                .withStatus(201)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"sha\": \"commit-sha\"}")));

                // Update Ref - github API might use PATCH for updating reference
                stubFor(patch(urlPathMatching("/repos/test-owner/test-repo/git/refs/.*"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"ref\": \"refs/heads/ai/issue-123\"}")));

                // Create PR
                stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/pulls"))
                                .willReturn(aResponse()
                                                .withStatus(201)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody("{\"html_url\": \"https://github.com/test-owner/test-repo/pull/1\"}")));

                // Mock Check Runs (for Tester validation)
                stubFor(get(urlPathMatching("/repos/test-owner/test-repo/commits/.*/check-runs"))
                                .willReturn(aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"total_count\": 2, \"check_runs\": [{\"name\": \"backend\", \"status\": \"completed\", \"conclusion\": \"success\", \"id\": 1}, {\"name\": \"e2e\", \"status\": \"completed\", \"conclusion\": \"success\", \"id\": 2}]}")));

                // --- 2. MOCK LLM API ---

                // Default fallback LLM mock
                stubFor(post(urlPathEqualTo("/chat/completions"))
                                .willReturn(aResponse()
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"shouldUpdate\\\":false,\\\"sectionsToUpdate\\\":[]}\"}}]}")));

                // PM Agent
                stubFor(post(urlPathEqualTo("/chat/completions"))
                                .withRequestBody(containing("You are a product manager"))
                                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"issueId\\\":123,\\\"title\\\":\\\"Test\\\",\\\"summary\\\":\\\"Summary\\\",\\\"acceptanceCriteria\\\":[\\\"A\\\",\\\"B\\\",\\\"C\\\"],\\\"outOfScope\\\":[\\\"D\\\"],\\\"risks\\\":[\\\"E\\\"],\\\"priority\\\":\\\"P2\\\"}\"}}]}")));

                // Qualifier Agent
                stubFor(post(urlPathEqualTo("/chat/completions"))
                                .withRequestBody(containing("You are a technical qualifier"))
                                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"branchName\\\":\\\"ai/issue-123\\\",\\\"tasks\\\":[{\\\"id\\\":\\\"T1\\\",\\\"area\\\":\\\"backend\\\",\\\"description\\\":\\\"Test\\\",\\\"filesLikely\\\":[],\\\"tests\\\":[]},{\\\"id\\\":\\\"T2\\\",\\\"area\\\":\\\"backend\\\",\\\"description\\\":\\\"Test\\\",\\\"filesLikely\\\":[],\\\"tests\\\":[]},{\\\"id\\\":\\\"T3\\\",\\\"area\\\":\\\"backend\\\",\\\"description\\\":\\\"Test\\\",\\\"filesLikely\\\":[],\\\"tests\\\":[]}],\\\"commands\\\":{\\\"backendVerify\\\":\\\"mvn\\\",\\\"frontendLint\\\":\\\"npm\\\",\\\"frontendTest\\\":\\\"npm\\\",\\\"e2e\\\":\\\"npm\\\"},\\\"definitionOfDone\\\":[\\\"Done\\\"]}\"}}]}")));

                // Architect Agent
                stubFor(post(urlPathEqualTo("/chat/completions"))
                                .withRequestBody(containing("You are a software architect"))
                                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"identifiedPatterns\\\":[{\\\"pattern\\\":\\\"A\\\",\\\"location\\\":\\\"B\\\",\\\"description\\\":\\\"C\\\"}],\\\"recommendedPatterns\\\":[{\\\"pattern\\\":\\\"A\\\",\\\"rationale\\\":\\\"B\\\",\\\"implementation\\\":\\\"C\\\"}],\\\"componentDiagram\\\":\\\"diagram\\\",\\\"architectureDecisions\\\":[{\\\"title\\\":\\\"A\\\",\\\"context\\\":\\\"B\\\",\\\"decision\\\":\\\"C\\\",\\\"consequences\\\":\\\"D\\\"}],\\\"componentsAffected\\\":[\\\"A\\\"],\\\"dataFlow\\\":\\\"Flow\\\",\\\"integrationPoints\\\":[\\\"A\\\"],\\\"technicalRisks\\\":[{\\\"risk\\\":\\\"A\\\",\\\"mitigation\\\":\\\"B\\\"}],\\\"testingStrategy\\\":{\\\"unitTests\\\":\\\"A\\\",\\\"integrationTests\\\":\\\"B\\\",\\\"e2eTests\\\":\\\"C\\\"}}\"}}]}")));

                // Developer Agent
                stubFor(post(urlPathEqualTo("/chat/completions"))
                                .withRequestBody(containing("expert software developer implementing code changes"))
                                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"summary\\\":\\\"Dev\\\",\\\"files\\\":[{\\\"path\\\":\\\"backend/test.txt\\\",\\\"operation\\\":\\\"create\\\",\\\"content\\\":\\\"hello\\\",\\\"explanation\\\":\\\"test\\\"}],\\\"testingNotes\\\":\\\"Test\\\",\\\"implementationNotes\\\":\\\"Test\\\"}\"}}]}")));

                // Reviewer Agent
                stubFor(post(urlPathEqualTo("/chat/completions"))
                                .withRequestBody(containing("# Code Review Request"))
                                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"personaName\\\":\\\"Security\\\",\\\"riskFindings\\\":[],\\\"requiredEnhancements\\\":[],\\\"overallAssessment\\\":{\\\"status\\\":\\\"approved\\\",\\\"summary\\\":\\\"Good\\\",\\\"criticalIssueCount\\\":0,\\\"highIssueCount\\\":0,\\\"mediumIssueCount\\\":0,\\\"lowIssueCount\\\":0}}\"}}]}")));

                // Judge Agent
                stubFor(post(urlPathEqualTo("/chat/completions"))
                                .withRequestBody(containing("You are the Judge"))
                                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"run_id\\\":\\\"123e4567-e89b-12d3-a456-426614174000\\\",\\\"checkpoint\\\":\\\"pre_merge\\\",\\\"artifact_key\\\":\\\"artifact_key\\\",\\\"rubric_name\\\":\\\"default\\\",\\\"overall_score\\\":1.0,\\\"verdict\\\":\\\"pass\\\",\\\"confidence\\\":1.0,\\\"per_criterion\\\":[{\\\"criterion\\\":\\\"A\\\",\\\"weight\\\":1.0,\\\"score\\\":1.0,\\\"level\\\":\\\"excellent\\\",\\\"evidence\\\":\\\"Ev\\\"}],\\\"timestamp\\\":\\\"2023-10-10T10:10:10Z\\\"}\"}}]}")));

                // Writer Agent (Changelog)
                stubFor(post(urlPathEqualTo("/chat/completions"))
                                .withRequestBody(containing("creating comprehensive changelogs"))
                                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                                                .withBody(
                                                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"summary\\\":\\\"Updated\\\",\\\"added\\\":[\\\"A\\\"],\\\"changed\\\":[],\\\"deprecated\\\":[],\\\"removed\\\":[],\\\"fixed\\\":[],\\\"security\\\":[],\\\"technicalDetails\\\":\\\"A\\\",\\\"breakingChanges\\\":[],\\\"migrationGuide\\\":\\\"\\\"}\"}}]}")));

                // --- 3. EXECUTE INTEGRATION ---

                RunRequest request = new RunRequest("test-owner/test-repo", 123, "code", null);
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth("test-github-token");
                HttpEntity<RunRequest> entity = new HttpEntity<>(request, headers);

                // Submit the task via REST
                ResponseEntity<String> response = restTemplate.exchange("/api/a2a/tasks", HttpMethod.POST, entity,
                                String.class);
                assertEquals(202, response.getStatusCode().value(),
                                "Task should be accepted. Body: " + response.getBody());

                // Polling the database/API using Awaitility for completion of async agents
                await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
                        var runs = runRepository.findAll();
                        assertFalse(runs.isEmpty(), "Run should be saved to database");
                        var savedRun = runs.get(0);

                        assertEquals(RunStatus.DONE, savedRun.getStatus(),
                                        "Run should eventually hit DONE status: " + savedRun.getStatus());
                        assertTrue(savedRun.getArtifacts().size() >= 1, "Should have generated artifacts");
                });

                // Verify wiremock actually received the PR creation call, proving the full
                // workflow touched GitHub
                verify(1, postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/pulls")));
        }
}
