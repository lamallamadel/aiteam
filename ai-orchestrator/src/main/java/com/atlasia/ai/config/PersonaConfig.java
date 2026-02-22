package com.atlasia.ai.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonaConfig(
        String name,
        String role,
        String mission,
        List<String> focusAreas,
        List<String> reviewChecklist,
        Map<String, List<String>> severityLevels,
        List<Enhancement> requiredEnhancements
) {
    public record Enhancement(
            String type,
            String description
    ) {}
}
