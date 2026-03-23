package com.atlasia.ai.service;

import com.atlasia.ai.config.ModelTierProperties;
import com.atlasia.ai.model.TaskComplexity;
import org.springframework.stereotype.Component;

/**
 * Resolves {@link TaskComplexity} for pipeline agents from {@code atlasia.model-tiers.agent-complexity}.
 */
@Component
public class LlmComplexityResolver {

    private final ModelTierProperties modelTierProperties;

    public LlmComplexityResolver(ModelTierProperties modelTierProperties) {
        this.modelTierProperties = modelTierProperties;
    }

    public TaskComplexity forAgent(String agentKey) {
        if (agentKey == null) {
            return TaskComplexity.MEDIUM;
        }
        String raw = modelTierProperties.getAgentComplexity().get(agentKey.trim().toLowerCase());
        if (raw == null) {
            return TaskComplexity.MEDIUM;
        }
        return TaskComplexity.fromYamlKey(raw);
    }
}
