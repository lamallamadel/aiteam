package com.atlasia.ai.service;

import java.util.List;

/**
 * Subset of fields from {@code ai/agents/&lt;name&gt;.yaml} used to enrich LLM system prompts.
 */
public record AgentContract(
        String name,
        String persona,
        String mission,
        String chainOfThought,
        List<String> rules) {

    public static AgentContract missing(String name) {
        return new AgentContract(name, "", "", "", List.of());
    }

    public boolean isEmpty() {
        return (persona == null || persona.isBlank())
                && (mission == null || mission.isBlank())
                && (chainOfThought == null || chainOfThought.isBlank())
                && (rules == null || rules.isEmpty());
    }
}
