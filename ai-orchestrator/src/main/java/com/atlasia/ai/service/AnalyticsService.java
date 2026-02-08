package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.*;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunArtifactRepository;
import com.atlasia.ai.persistence.RunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final RunRepository runRepository;
    private final RunArtifactRepository runArtifactRepository;
    private final ObjectMapper objectMapper;

    public AnalyticsService(RunRepository runRepository, RunArtifactRepository runArtifactRepository, ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.runArtifactRepository = runArtifactRepository;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "analytics-runs-summary", key = "#startDate?.toString() + '-' + #endDate?.toString()")
    public RunsSummaryDto getRunsSummary(Instant startDate, Instant endDate) {
        log.info("Computing runs summary for period: {} to {}", startDate, endDate);

        Instant effectiveStart = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant effectiveEnd = endDate != null ? endDate : Instant.now();

        List<RunEntity> runs = runRepository.findByCreatedAtBetween(effectiveStart, effectiveEnd);

        long totalRuns = runs.size();
        if (totalRuns == 0) {
            return new RunsSummaryDto(0, 0.0, 0.0, 0.0, Map.of(), Map.of(), effectiveStart, effectiveEnd);
        }

        long successCount = runs.stream().filter(r -> r.getStatus() == RunStatus.DONE).count();
        long failureCount = runs.stream().filter(r -> r.getStatus() == RunStatus.FAILED).count();
        long escalatedCount = runs.stream().filter(r -> r.getStatus() == RunStatus.ESCALATED).count();

        double successRate = (double) successCount / totalRuns;
        double failureRate = (double) failureCount / totalRuns;
        double escalationRate = (double) escalatedCount / totalRuns;

        Map<String, Long> statusBreakdown = runs.stream()
                .collect(Collectors.groupingBy(r -> r.getStatus().name(), Collectors.counting()));

        Map<String, RunsSummaryDto.TimeSeriesData> timeSeriesData = buildTimeSeriesData(runs, effectiveStart, effectiveEnd);

        return new RunsSummaryDto(
                totalRuns,
                successRate,
                failureRate,
                escalationRate,
                statusBreakdown,
                timeSeriesData,
                effectiveStart,
                effectiveEnd
        );
    }

    @Cacheable(value = "analytics-agents-performance", key = "#startDate?.toString() + '-' + #endDate?.toString()")
    public AgentsPerformanceDto getAgentsPerformance(Instant startDate, Instant endDate) {
        log.info("Computing agents performance for period: {} to {}", startDate, endDate);

        Instant effectiveStart = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant effectiveEnd = endDate != null ? endDate : Instant.now();

        List<Map<String, Object>> agentMetricsData;
        if (startDate != null && endDate != null) {
            agentMetricsData = runRepository.getAgentPerformanceMetricsByDateRange(effectiveStart, effectiveEnd);
        } else {
            agentMetricsData = runRepository.getAgentPerformanceMetrics();
        }

        List<AgentsPerformanceDto.AgentMetrics> agentMetrics = agentMetricsData.stream()
                .map(data -> {
                    String agentName = (String) data.get("agentName");
                    long totalRuns = ((Number) data.get("totalRuns")).longValue();
                    long successfulRuns = ((Number) data.get("successfulRuns")).longValue();
                    double successRate = ((Number) data.get("successRate")).doubleValue();
                    double avgCiFix = ((Number) data.getOrDefault("avgCiFix", 0.0)).doubleValue();
                    double avgE2eFix = ((Number) data.getOrDefault("avgE2eFix", 0.0)).doubleValue();

                    double avgDuration = calculateAverageDuration(agentName, effectiveStart, effectiveEnd);
                    double errorRate = 1.0 - successRate;

                    return new AgentsPerformanceDto.AgentMetrics(
                            agentName,
                            totalRuns,
                            avgDuration,
                            errorRate,
                            successRate,
                            avgCiFix,
                            avgE2eFix
                    );
                })
                .collect(Collectors.toList());

        double overallAvgDuration = agentMetrics.stream()
                .mapToDouble(AgentsPerformanceDto.AgentMetrics::averageDuration)
                .average()
                .orElse(0.0);

        double overallErrorRate = agentMetrics.stream()
                .mapToDouble(AgentsPerformanceDto.AgentMetrics::errorRate)
                .average()
                .orElse(0.0);

        return new AgentsPerformanceDto(agentMetrics, overallAvgDuration, overallErrorRate);
    }

    @Cacheable(value = "analytics-personas-findings", key = "#startDate?.toString() + '-' + #endDate?.toString()")
    public PersonasFindingsDto getPersonasFindings(Instant startDate, Instant endDate) {
        log.info("Computing personas findings for period: {} to {}", startDate, endDate);

        Instant effectiveStart = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant effectiveEnd = endDate != null ? endDate : Instant.now();

        var personaArtifacts = runArtifactRepository.findByArtifactTypeAndDateRange(
                "persona_review.json", effectiveStart, effectiveEnd);

        Map<String, PersonaFindingsData> personaDataMap = new HashMap<>();
        long totalFindings = 0;
        long mandatoryFindings = 0;
        Map<String, Long> severityCounts = new HashMap<>();

        for (var artifact : personaArtifacts) {
            try {
                JsonNode payload = objectMapper.readTree(artifact.getPayload());
                JsonNode findings = payload.get("findings");

                if (findings != null && findings.isArray()) {
                    for (JsonNode finding : findings) {
                        String personaName = finding.has("personaName") ? finding.get("personaName").asText() : "Unknown";
                        String personaRole = finding.has("personaRole") ? finding.get("personaRole").asText() : "Unknown";

                        PersonaFindingsData data = personaDataMap.computeIfAbsent(personaName,
                                k -> new PersonaFindingsData(personaName, personaRole));

                        JsonNode issues = finding.get("issues");
                        if (issues != null && issues.isArray()) {
                            for (JsonNode issue : issues) {
                                String severity = issue.has("severity") ? issue.get("severity").asText().toLowerCase() : "unknown";
                                boolean mandatory = issue.has("mandatory") && issue.get("mandatory").asBoolean();

                                data.totalFindings++;
                                totalFindings++;

                                if (mandatory) {
                                    data.mandatoryFindings++;
                                    mandatoryFindings++;
                                }

                                severityCounts.merge(severity, 1L, Long::sum);

                                switch (severity) {
                                    case "critical" -> data.criticalFindings++;
                                    case "high" -> data.highFindings++;
                                    case "medium" -> data.mediumFindings++;
                                    case "low" -> data.lowFindings++;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse persona review artifact: {}", artifact.getId(), e);
            }
        }

        long totalRunsWithPersonaReviews = personaArtifacts.stream()
                .map(a -> a.getRun().getId())
                .distinct()
                .count();

        final long finalTotalFindings = totalFindings;
        final long finalMandatoryFindings = mandatoryFindings;

        List<PersonasFindingsDto.PersonaStatistics> personaStats = personaDataMap.values().stream()
                .map(data -> new PersonasFindingsDto.PersonaStatistics(
                        data.personaName,
                        data.personaRole,
                        data.totalFindings,
                        data.criticalFindings,
                        data.highFindings,
                        data.mediumFindings,
                        data.lowFindings,
                        data.mandatoryFindings,
                        totalRunsWithPersonaReviews > 0 ? (double) data.totalFindings / totalRunsWithPersonaReviews : 0.0
                ))
                .sorted(Comparator.comparing(PersonasFindingsDto.PersonaStatistics::totalFindings).reversed())
                .collect(Collectors.toList());

        Map<String, PersonasFindingsDto.SeverityBreakdown> severityBreakdown = severityCounts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> new PersonasFindingsDto.SeverityBreakdown(
                                e.getValue(),
                                finalTotalFindings > 0 ? (double) e.getValue() / finalTotalFindings * 100 : 0.0
                        )
                ));

        return new PersonasFindingsDto(personaStats, severityBreakdown, finalTotalFindings, finalMandatoryFindings);
    }

    @Cacheable(value = "analytics-fix-loops", key = "#startDate?.toString() + '-' + #endDate?.toString()")
    public FixLoopsDto getFixLoops(Instant startDate, Instant endDate) {
        log.info("Computing fix loops for period: {} to {}", startDate, endDate);

        Instant effectiveStart = startDate != null ? startDate : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant effectiveEnd = endDate != null ? endDate : Instant.now();

        List<RunEntity> runs = runRepository.findByCreatedAtBetween(effectiveStart, effectiveEnd);

        List<FixLoopsDto.FixLoopPattern> patterns = runs.stream()
                .filter(r -> r.getCiFixCount() > 0 || r.getE2eFixCount() > 0)
                .map(r -> {
                    int totalIterations = r.getCiFixCount() + r.getE2eFixCount();
                    String pattern = determineFixPattern(r.getCiFixCount(), r.getE2eFixCount());

                    return new FixLoopsDto.FixLoopPattern(
                            r.getRepo(),
                            r.getIssueNumber(),
                            r.getCiFixCount(),
                            r.getE2eFixCount(),
                            totalIterations,
                            r.getStatus().name(),
                            pattern
                    );
                })
                .sorted(Comparator.comparing(FixLoopsDto.FixLoopPattern::totalIterations).reversed())
                .limit(100)
                .collect(Collectors.toList());

        Map<String, FixLoopsDto.LoopStatistics> loopStatistics = new HashMap<>();

        List<RunEntity> ciLoopRuns = runs.stream().filter(r -> r.getCiFixCount() > 0).collect(Collectors.toList());
        if (!ciLoopRuns.isEmpty()) {
            long ciSuccessCount = ciLoopRuns.stream().filter(r -> r.getStatus() == RunStatus.DONE).count();
            loopStatistics.put("ci", new FixLoopsDto.LoopStatistics(
                    ciLoopRuns.size(),
                    ciLoopRuns.stream().mapToInt(RunEntity::getCiFixCount).average().orElse(0.0),
                    ciLoopRuns.stream().mapToInt(RunEntity::getCiFixCount).max().orElse(0),
                    (double) ciSuccessCount / ciLoopRuns.size()
            ));
        }

        List<RunEntity> e2eLoopRuns = runs.stream().filter(r -> r.getE2eFixCount() > 0).collect(Collectors.toList());
        if (!e2eLoopRuns.isEmpty()) {
            long e2eSuccessCount = e2eLoopRuns.stream().filter(r -> r.getStatus() == RunStatus.DONE).count();
            loopStatistics.put("e2e", new FixLoopsDto.LoopStatistics(
                    e2eLoopRuns.size(),
                    e2eLoopRuns.stream().mapToInt(RunEntity::getE2eFixCount).average().orElse(0.0),
                    e2eLoopRuns.stream().mapToInt(RunEntity::getE2eFixCount).max().orElse(0),
                    (double) e2eSuccessCount / e2eLoopRuns.size()
            ));
        }

        double avgCiIterations = runs.stream().mapToInt(RunEntity::getCiFixCount).average().orElse(0.0);
        double avgE2eIterations = runs.stream().mapToInt(RunEntity::getE2eFixCount).average().orElse(0.0);

        long runsWithMultipleCi = runs.stream().filter(r -> r.getCiFixCount() > 1).count();
        long runsWithMultipleE2e = runs.stream().filter(r -> r.getE2eFixCount() > 1).count();

        return new FixLoopsDto(
                patterns,
                loopStatistics,
                avgCiIterations,
                avgE2eIterations,
                runsWithMultipleCi,
                runsWithMultipleE2e
        );
    }

    private Map<String, RunsSummaryDto.TimeSeriesData> buildTimeSeriesData(List<RunEntity> runs, Instant start, Instant end) {
        Map<LocalDate, List<RunEntity>> runsByDate = runs.stream()
                .collect(Collectors.groupingBy(r -> r.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate()));

        Map<String, Long> runCountByDate = new LinkedHashMap<>();
        Map<String, Double> successRateByDate = new LinkedHashMap<>();
        Map<String, Double> failureRateByDate = new LinkedHashMap<>();
        Map<String, Double> escalationRateByDate = new LinkedHashMap<>();

        LocalDate startDate = start.atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = end.atZone(ZoneId.systemDefault()).toLocalDate();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String dateStr = date.toString();
            List<RunEntity> dailyRuns = runsByDate.getOrDefault(date, Collections.emptyList());

            long dailyTotal = dailyRuns.size();
            runCountByDate.put(dateStr, dailyTotal);

            if (dailyTotal > 0) {
                long dailySuccess = dailyRuns.stream().filter(r -> r.getStatus() == RunStatus.DONE).count();
                long dailyFailure = dailyRuns.stream().filter(r -> r.getStatus() == RunStatus.FAILED).count();
                long dailyEscalated = dailyRuns.stream().filter(r -> r.getStatus() == RunStatus.ESCALATED).count();

                successRateByDate.put(dateStr, (double) dailySuccess / dailyTotal);
                failureRateByDate.put(dateStr, (double) dailyFailure / dailyTotal);
                escalationRateByDate.put(dateStr, (double) dailyEscalated / dailyTotal);
            } else {
                successRateByDate.put(dateStr, 0.0);
                failureRateByDate.put(dateStr, 0.0);
                escalationRateByDate.put(dateStr, 0.0);
            }
        }

        RunsSummaryDto.TimeSeriesData timeSeriesData = new RunsSummaryDto.TimeSeriesData(
                runCountByDate,
                successRateByDate,
                failureRateByDate,
                escalationRateByDate
        );

        return Map.of("daily", timeSeriesData);
    }

    private double calculateAverageDuration(String agentName, Instant start, Instant end) {
        List<Map<String, Object>> durationData = runRepository.getAverageDurationByRepoAndStatus(start, end);

        double totalDuration = 0.0;
        int count = 0;

        for (Map<String, Object> data : durationData) {
            if (data.get("avgDurationSeconds") != null) {
                totalDuration += ((Number) data.get("avgDurationSeconds")).doubleValue();
                count++;
            }
        }

        return count > 0 ? totalDuration / count : 0.0;
    }

    private String determineFixPattern(int ciFixCount, int e2eFixCount) {
        if (ciFixCount > 0 && e2eFixCount > 0) {
            return "CI_AND_E2E";
        } else if (ciFixCount > 0) {
            return "CI_ONLY";
        } else if (e2eFixCount > 0) {
            return "E2E_ONLY";
        }
        return "NONE";
    }

    private static class PersonaFindingsData {
        String personaName;
        String personaRole;
        long totalFindings = 0;
        long criticalFindings = 0;
        long highFindings = 0;
        long mediumFindings = 0;
        long lowFindings = 0;
        long mandatoryFindings = 0;

        PersonaFindingsData(String personaName, String personaRole) {
            this.personaName = personaName;
            this.personaRole = personaRole;
        }
    }
}
