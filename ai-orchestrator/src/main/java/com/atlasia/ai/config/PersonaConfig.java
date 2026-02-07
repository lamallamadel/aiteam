package com.atlasia.ai.config;

import java.util.List;
import java.util.Map;

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
