package com.atlasia.ai.e2e.scenarios;

import com.atlasia.ai.api.RunRequest;
import com.atlasia.ai.e2e.configuration.AbstractE2ETest;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class EnhancedCodeGenerationE2ETest extends AbstractE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        WireMock.reset();
        jdbcTemplate.execute("DELETE FROM ai_trace_event; DELETE FROM ai_run_artifact; DELETE FROM ai_run;");
    }

    @Test
    void testCodeGenerationWithPRCreationVerification() {
        mockGitHubApiEndpoints();
        mockLLMEndpoints();

        RunRequest request = new RunRequest("test-owner/test-repo", 456, "code", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-github-token");
        HttpEntity<RunRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange("/api/a2a/tasks", HttpMethod.POST, entity,
                String.class);
        assertEquals(202, response.getStatusCode().value(),
                "Task should be accepted. Body: " + response.getBody());

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            var runs = runRepository.findAll();
            assertFalse(runs.isEmpty(), "Run should be saved to database");
            var savedRun = runs.get(0);

            assertEquals(RunStatus.DONE, savedRun.getStatus(),
                    "Run should eventually hit DONE status: " + savedRun.getStatus());
            assertTrue(savedRun.getArtifacts().size() >= 1, "Should have generated artifacts");
        });

        verify(1, postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/pulls"))
                .withRequestBody(containing("\"title\":")));

        verify(moreThanOrExactly(1), postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/git/blobs")));
        verify(moreThanOrExactly(1), postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/git/commits")));
    }

    @Test
    void testCodeGenerationWithMultipleFileChanges() {
        mockGitHubApiEndpoints();
        mockLLMEndpointsWithMultipleFiles();

        RunRequest request = new RunRequest("test-owner/test-repo", 789, "code", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-github-token");
        HttpEntity<RunRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange("/api/a2a/tasks", HttpMethod.POST, entity,
                String.class);
        assertEquals(202, response.getStatusCode().value());

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            var runs = runRepository.findAll();
            var savedRun = runs.get(0);
            assertEquals(RunStatus.DONE, savedRun.getStatus());
        });

        verify(moreThanOrExactly(2), postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/git/blobs")));
    }

    @Test
    void testCodeGenerationWithReviewerFeedback() {
        mockGitHubApiEndpoints();
        mockLLMEndpointsWithReviewerFeedback();

        RunRequest request = new RunRequest("test-owner/test-repo", 999, "code", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-github-token");
        HttpEntity<RunRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange("/api/a2a/tasks", HttpMethod.POST, entity,
                String.class);
        assertEquals(202, response.getStatusCode().value());

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            var runs = runRepository.findAll();
            var savedRun = runs.get(0);
            assertEquals(RunStatus.DONE, savedRun.getStatus());

            List<RunArtifactEntity> artifacts = savedRun.getArtifacts();
            boolean hasReviewArtifact = artifacts.stream()
                    .anyMatch(a -> a.getArtifactType().contains("review") || a.getAgentName().contains("review"));
            assertTrue(hasReviewArtifact, "Should have review artifacts");
        });
    }

    @Test
    void testCodeGenerationWithBranchCreation() {
        mockGitHubApiEndpoints();
        mockLLMEndpoints();

        RunRequest request = new RunRequest("test-owner/test-repo", 111, "code", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-github-token");
        HttpEntity<RunRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange("/api/a2a/tasks", HttpMethod.POST, entity,
                String.class);
        assertEquals(202, response.getStatusCode().value());

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            var runs = runRepository.findAll();
            var savedRun = runs.get(0);
            assertEquals(RunStatus.DONE, savedRun.getStatus());
        });

        verify(1, postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/git/refs"))
                .withRequestBody(containing("\"ref\":\"refs/heads/")));
    }

    @Test
    void testCodeGenerationWithTestVerification() {
        mockGitHubApiEndpoints();
        mockLLMEndpoints();

        RunRequest request = new RunRequest("test-owner/test-repo", 222, "code", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-github-token");
        HttpEntity<RunRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange("/api/a2a/tasks", HttpMethod.POST, entity,
                String.class);
        assertEquals(202, response.getStatusCode().value());

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            var runs = runRepository.findAll();
            var savedRun = runs.get(0);
            assertEquals(RunStatus.DONE, savedRun.getStatus());
        });

        verify(moreThanOrExactly(1), getRequestedFor(urlPathMatching("/repos/test-owner/test-repo/commits/.*/check-runs")));
    }

    @Test
    void testCodeGenerationFullWorkflowWithArtifactValidation() throws Exception {
        mockGitHubApiEndpoints();
        mockLLMEndpoints();

        RunRequest request = new RunRequest("test-owner/test-repo", 333, "code", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-github-token");
        HttpEntity<RunRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange("/api/a2a/tasks", HttpMethod.POST, entity,
                String.class);
        assertEquals(202, response.getStatusCode().value());

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            var runs = runRepository.findAll();
            assertFalse(runs.isEmpty());
            var savedRun = runs.get(0);
            assertEquals(RunStatus.DONE, savedRun.getStatus());

            List<RunArtifactEntity> artifacts = savedRun.getArtifacts();
            assertTrue(artifacts.size() >= 1, "Should have at least one artifact");

            boolean hasPMOutput = artifacts.stream()
                    .anyMatch(a -> a.getAgentName().toLowerCase().contains("pm") || 
                                   a.getAgentName().toLowerCase().contains("product"));
            boolean hasDevOutput = artifacts.stream()
                    .anyMatch(a -> a.getAgentName().toLowerCase().contains("dev"));

            assertTrue(hasPMOutput || hasDevOutput, "Should have PM or Developer artifacts");
        });

        var runs = runRepository.findAll();
        var savedRun = runs.get(0);
        for (RunArtifactEntity artifact : savedRun.getArtifacts()) {
            assertNotNull(artifact.getPayload(), "Artifact payload should not be null");
            assertFalse(artifact.getPayload().isEmpty(), "Artifact payload should not be empty");

            if (artifact.getPayload().startsWith("{")) {
                JsonNode jsonData = objectMapper.readTree(artifact.getPayload());
                assertNotNull(jsonData, "JSON artifact should be parseable");
            }
        }
    }

    private void mockGitHubApiEndpoints() {
        stubFor(get(urlPathEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"login\": \"test-app\"}")));

        stubFor(get(urlPathMatching("/repos/test-owner/test-repo/issues/.*"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"title\": \"Create feature\", \"body\": \"Implement new feature\"}")));

        stubFor(get(urlPathMatching("/repos/test-owner/test-repo/git/ref/heads.*main"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"object\": {\"sha\": \"main-sha-123\"}}")));

        stubFor(get(urlPathMatching("/repos/test-owner/test-repo/git/ref/heads.*ai.*"))
                .inScenario("Branch Creation")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(404)));

        stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/git/refs"))
                .inScenario("Branch Creation")
                .willSetStateTo("Branch Created")
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ref\": \"refs/heads/ai/issue-123\"}")));

        stubFor(get(urlPathMatching("/repos/test-owner/test-repo/git/ref/heads.*ai.*"))
                .inScenario("Branch Creation")
                .whenScenarioStateIs("Branch Created")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"object\": {\"sha\": \"commit-sha\"}}")));

        stubFor(get(urlPathMatching("/repos/test-owner/test-repo/git/trees/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sha\": \"tree-sha\", \"tree\": []}")));

        stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/git/blobs"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sha\": \"blob-sha\"}")));

        stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/git/trees"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sha\": \"new-tree-sha\"}")));

        stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/git/commits"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sha\": \"commit-sha\"}")));

        stubFor(patch(urlPathMatching("/repos/test-owner/test-repo/git/refs/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ref\": \"refs/heads/ai/issue-123\"}")));

        stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/pulls"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"html_url\": \"https://github.com/test-owner/test-repo/pull/1\"}")));

        stubFor(get(urlPathMatching("/repos/test-owner/test-repo/commits/.*/check-runs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"total_count\": 2, \"check_runs\": [{\"name\": \"backend\", \"status\": \"completed\", \"conclusion\": \"success\"}, {\"name\": \"e2e\", \"status\": \"completed\", \"conclusion\": \"success\"}]}")));
    }

    private void mockLLMEndpoints() {
        stubFor(post(urlPathEqualTo("/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"shouldUpdate\\\":false,\\\"sectionsToUpdate\\\":[]}\"}}]}")));

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("You are a product manager"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"issueId\\\":123,\\\"title\\\":\\\"Feature\\\",\\\"summary\\\":\\\"Summary\\\",\\\"acceptanceCriteria\\\":[\\\"A\\\"],\\\"outOfScope\\\":[],\\\"risks\\\":[],\\\"priority\\\":\\\"P1\\\"}\"}}]}")));

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("You are a technical qualifier"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"branchName\\\":\\\"ai/issue-123\\\",\\\"tasks\\\":[{\\\"id\\\":\\\"T1\\\",\\\"area\\\":\\\"backend\\\",\\\"description\\\":\\\"Task\\\",\\\"filesLikely\\\":[],\\\"tests\\\":[]}],\\\"commands\\\":{\\\"backendVerify\\\":\\\"mvn\\\",\\\"frontendLint\\\":\\\"npm\\\"},\\\"definitionOfDone\\\":[\\\"Done\\\"]}\"}}]}")));

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("You are a software architect"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"identifiedPatterns\\\":[],\\\"recommendedPatterns\\\":[],\\\"componentDiagram\\\":\\\"diagram\\\",\\\"architectureDecisions\\\":[],\\\"componentsAffected\\\":[],\\\"dataFlow\\\":\\\"Flow\\\",\\\"integrationPoints\\\":[],\\\"technicalRisks\\\":[],\\\"testingStrategy\\\":{\\\"unitTests\\\":\\\"A\\\",\\\"integrationTests\\\":\\\"B\\\",\\\"e2eTests\\\":\\\"C\\\"}}\"}}]}")));

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("expert software developer implementing code changes"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"summary\\\":\\\"Implementation complete\\\",\\\"files\\\":[{\\\"path\\\":\\\"src/main.java\\\",\\\"operation\\\":\\\"create\\\",\\\"content\\\":\\\"public class Main {}\\\",\\\"explanation\\\":\\\"Main class\\\"}],\\\"testingNotes\\\":\\\"Tests pass\\\",\\\"implementationNotes\\\":\\\"Complete\\\"}\"}}]}")));

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("# Code Review Request"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"personaName\\\":\\\"Security\\\",\\\"riskFindings\\\":[],\\\"requiredEnhancements\\\":[],\\\"overallAssessment\\\":{\\\"status\\\":\\\"approved\\\",\\\"summary\\\":\\\"Good\\\",\\\"criticalIssueCount\\\":0,\\\"highIssueCount\\\":0,\\\"mediumIssueCount\\\":0,\\\"lowIssueCount\\\":0}}\"}}]}")));

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("You are the Judge"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"verdict\\\":\\\"pass\\\",\\\"overall_score\\\":1.0,\\\"confidence\\\":1.0,\\\"per_criterion\\\":[]}\"}}]}")));

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("creating comprehensive changelogs"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"summary\\\":\\\"Changelog\\\",\\\"added\\\":[\\\"Feature\\\"],\\\"changed\\\":[],\\\"deprecated\\\":[],\\\"removed\\\":[],\\\"fixed\\\":[],\\\"security\\\":[],\\\"technicalDetails\\\":\\\"Details\\\",\\\"breakingChanges\\\":[],\\\"migrationGuide\\\":\\\"\\\"}\"}}]}")));
    }

    private void mockLLMEndpointsWithMultipleFiles() {
        mockLLMEndpoints();

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("expert software developer implementing code changes"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"summary\\\":\\\"Multi-file implementation\\\",\\\"files\\\":[{\\\"path\\\":\\\"src/main.java\\\",\\\"operation\\\":\\\"create\\\",\\\"content\\\":\\\"class Main {}\\\",\\\"explanation\\\":\\\"Main\\\"},{\\\"path\\\":\\\"src/helper.java\\\",\\\"operation\\\":\\\"create\\\",\\\"content\\\":\\\"class Helper {}\\\",\\\"explanation\\\":\\\"Helper\\\"}],\\\"testingNotes\\\":\\\"Tests pass\\\",\\\"implementationNotes\\\":\\\"Complete\\\"}\"}}]}")));
    }

    private void mockLLMEndpointsWithReviewerFeedback() {
        mockLLMEndpoints();

        stubFor(post(urlPathEqualTo("/chat/completions"))
                .withRequestBody(containing("# Code Review Request"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"choices\": [{\"message\": {\"content\": \"{\\\"personaName\\\":\\\"Security\\\",\\\"riskFindings\\\":[{\\\"severity\\\":\\\"medium\\\",\\\"finding\\\":\\\"Issue\\\",\\\"recommendation\\\":\\\"Fix\\\"}],\\\"requiredEnhancements\\\":[{\\\"enhancement\\\":\\\"Improve logging\\\"}],\\\"overallAssessment\\\":{\\\"status\\\":\\\"approved_with_suggestions\\\",\\\"summary\\\":\\\"Good with minor improvements\\\",\\\"criticalIssueCount\\\":0,\\\"highIssueCount\\\":0,\\\"mediumIssueCount\\\":1,\\\"lowIssueCount\\\":0}}\"}}]}")));
    }
}
