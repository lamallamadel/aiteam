package com.atlasia.ai.e2e.configuration;

import com.atlasia.ai.AtlasiaAiOrchestratorApplication;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = AtlasiaAiOrchestratorApplication.class)
@ActiveProfiles("e2e")
public abstract class AbstractE2ETest {

    protected static WireMockServer wireMockServer;
    protected static E2ETestReporter testReporter = new E2ETestReporter();

    @BeforeAll
    static void beforeAll() {

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void afterAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        testReporter.generateReport();
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        String testName = testInfo.getDisplayName();
        String className = testInfo.getTestClass().map(Class::getSimpleName).orElse("Unknown");
        testReporter.recordTestResult(testName, className);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        // Point external services to WireMock
        registry.add("atlasia.github.api-url", () -> wireMockServer.baseUrl());
        registry.add("atlasia.orchestrator.llm.endpoint", () -> wireMockServer.baseUrl());
        registry.add("atlasia.orchestrator.llm.fallback-endpoint", () -> wireMockServer.baseUrl());
    }

    /**
     * Builds a diagnostic message when a run is not DONE (e.g. FAILED), for clearer test failures.
     * Includes status, currentAgent, and the first error_details artifact payload if present.
     */
    protected static String runFailureDiagnostic(RunEntity run) {
        if (run == null) return "run=null";
        StringBuilder sb = new StringBuilder();
        sb.append("status=").append(run.getStatus());
        sb.append(", currentAgent=").append(run.getCurrentAgent());
        sb.append(", runId=").append(run.getId());
        if (run.getStatus() == RunStatus.FAILED && run.getArtifacts() != null) {
            run.getArtifacts().stream()
                    .filter(a -> "error_details".equals(a.getArtifactType()))
                    .findFirst()
                    .map(RunArtifactEntity::getPayload)
                    .ifPresent(payload -> sb.append("; error_details=").append(payload != null ? payload.replace("\n", " ") : "null"));
        }
        return sb.toString();
    }
}
