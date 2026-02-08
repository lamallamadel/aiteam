package com.atlasia.ai.api.dto;

import java.util.List;
import java.util.Map;

public record PersonaEffectivenessDto(
        Map<String, PersonaMetrics> personaMetrics,
        List<String> configurationRecommendations) {
    public record PersonaMetrics(
            int reviewsCount,
            int criticalFindings,
            double effectivenessScore, // ratio of valid warnings to total warnings
            int falsePositives // warned but run succeeded without fix
    ) {
    }
}
