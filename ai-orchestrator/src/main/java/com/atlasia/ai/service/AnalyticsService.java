package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.AgentPerformanceDto;
import com.atlasia.ai.api.dto.AnalyticsSummaryDto;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final RunRepository runRepository;

    public AnalyticsService(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public AnalyticsSummaryDto getSummary() {
        long total = runRepository.count();
        if (total == 0) {
            return new AnalyticsSummaryDto(0, 0, 0, 0, Map.of(), Map.of());
        }

        List<Map<String, Object>> statusCounts = runRepository.countByStatus();
        Map<String, Long> statusMap = statusCounts.stream()
                .collect(Collectors.toMap(
                        m -> m.get("status").toString(),
                        m -> (Long) m.get("count")));

        List<Map<String, Object>> repoCounts = runRepository.countByRepo();
        Map<String, Long> repoMap = repoCounts.stream()
                .collect(Collectors.toMap(
                        m -> m.get("repo").toString(),
                        m -> (Long) m.get("count")));

        long success = statusMap.getOrDefault(RunStatus.DONE.name(), 0L);
        long failure = statusMap.getOrDefault(RunStatus.FAILED.name(), 0L);
        long escalated = statusMap.getOrDefault(RunStatus.ESCALATED.name(), 0L);

        return new AnalyticsSummaryDto(
                total,
                (double) success / total,
                (double) failure / total,
                (double) escalated / total,
                statusMap,
                repoMap);
    }

    public AgentPerformanceDto getPerformance() {
        // In a real app, duration would be stored in the entity.
        // For now we use fix counts as a proxy for complexity/performance.
        List<Map<String, Object>> avgFixes = runRepository.getAverageFixCountsByStatus();
        Map<String, Double> fixMap = new HashMap<>();
        avgFixes.forEach(m -> {
            fixMap.put(m.get("status").toString() + "_ci", (Double) m.get("avgCiFix"));
            fixMap.put(m.get("status").toString() + "_e2e", (Double) m.get("avgE2eFix"));
        });

        return new AgentPerformanceDto(
                Map.of(), // Placeholder for duration analytics
                Map.of(), // Placeholder for error rates
                fixMap);
    }
}
