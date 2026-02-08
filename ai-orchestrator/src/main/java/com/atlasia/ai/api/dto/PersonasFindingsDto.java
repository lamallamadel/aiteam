package com.atlasia.ai.api.dto;

import java.util.List;
import java.util.Map;

public record PersonasFindingsDto(
        List<PersonaStatistics> personaStatistics,
        Map<String, SeverityBreakdown> severityBreakdown,
        long totalFindings,
        long mandatoryFindings) {

    public record PersonaStatistics(
            String personaName,
            String personaRole,
            long totalFindings,
            long criticalFindings,
            long highFindings,
            long mediumFindings,
            long lowFindings,
            long mandatoryFindings,
            double averageFindingsPerRun) {
    }

    public record SeverityBreakdown(
            long count,
            double percentage) {
    }
}
