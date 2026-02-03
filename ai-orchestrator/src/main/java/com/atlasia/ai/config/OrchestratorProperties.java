package com.atlasia.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "atlasia.orchestrator")
public record OrchestratorProperties(
        String token,
        String repoAllowlist,
        String workflowProtectPrefix
) {}
