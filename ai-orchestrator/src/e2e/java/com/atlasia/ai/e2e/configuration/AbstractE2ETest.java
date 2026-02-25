package com.atlasia.ai.e2e.configuration;

import com.atlasia.ai.AtlasiaAiOrchestratorApplication;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = AtlasiaAiOrchestratorApplication.class)
@ActiveProfiles("e2e")
public abstract class AbstractE2ETest {

    protected static WireMockServer wireMockServer;

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
        // Testcontainers will automatically stop the container when the JVM shuts down
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {

        // Point external services to WireMock
        registry.add("atlasia.github.api-url", () -> wireMockServer.baseUrl());
        registry.add("atlasia.orchestrator.llm.endpoint", () -> wireMockServer.baseUrl());
        registry.add("atlasia.orchestrator.llm.fallback-endpoint", () -> wireMockServer.baseUrl());
    }
}
