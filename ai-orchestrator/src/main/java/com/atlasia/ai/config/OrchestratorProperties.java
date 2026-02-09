package com.atlasia.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "atlasia.orchestrator")
public record OrchestratorProperties(
                String token,
                String repoAllowlist,
                String workflowProtectPrefix,
                GitHub github,
                Llm llm) {
        public record GitHub(
                        String appId,
                        String privateKeyPath,
                        String installationId) {
        }

        public record Llm(
                        String endpoint,
                        String model,
                        String apiKey,
                        String proxyHost,
                        Integer proxyPort,
                        String fallbackEndpoint,
                        String fallbackApiKey) {
        }
}
