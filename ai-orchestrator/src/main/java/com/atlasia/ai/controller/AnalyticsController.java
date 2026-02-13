package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.*;
import com.atlasia.ai.model.TraceEventEntity;
import com.atlasia.ai.persistence.TraceEventRepository;
import com.atlasia.ai.service.AnalyticsService;
import com.atlasia.ai.service.EscalationAnalyzerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final EscalationAnalyzerService escalationAnalyzerService;
    private final TraceEventRepository traceEventRepository;

    public AnalyticsController(AnalyticsService analyticsService,
                               EscalationAnalyzerService escalationAnalyzerService,
                               TraceEventRepository traceEventRepository) {
        this.analyticsService = analyticsService;
        this.escalationAnalyzerService = escalationAnalyzerService;
        this.traceEventRepository = traceEventRepository;
    }

    @GetMapping("/runs/summary")
    public ResponseEntity<RunsSummaryDto> getRunsSummary(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ResponseEntity.ok(analyticsService.getRunsSummary(startDate, endDate));
    }

    @GetMapping("/agents/performance")
    public ResponseEntity<AgentsPerformanceDto> getAgentsPerformance(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ResponseEntity.ok(analyticsService.getAgentsPerformance(startDate, endDate));
    }

    @GetMapping("/personas/findings")
    public ResponseEntity<PersonasFindingsDto> getPersonasFindings(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ResponseEntity.ok(analyticsService.getPersonasFindings(startDate, endDate));
    }

    @GetMapping("/fix-loops")
    public ResponseEntity<FixLoopsDto> getFixLoops(
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {
        return ResponseEntity.ok(analyticsService.getFixLoops(startDate, endDate));
    }

    @PostMapping("/escalations/insights")
    public ResponseEntity<EscalationInsightDto> generateEscalationInsights() {
        EscalationInsightDto insights = escalationAnalyzerService.analyzeEscalations();
        return ResponseEntity.ok(insights);
    }

    @GetMapping("/traces/summary")
    public ResponseEntity<Map<String, Object>> getTracesSummary(
            @RequestParam("runId") UUID runId) {
        List<TraceEventEntity> events = traceEventRepository.findByRunIdOrderByStartTimeAsc(runId);

        long totalTokens = events.stream()
                .filter(e -> e.getTokensUsed() != null)
                .mapToLong(TraceEventEntity::getTokensUsed)
                .sum();

        long llmCalls = events.stream()
                .filter(e -> "LLM_CALL".equals(e.getEventType()))
                .count();

        long totalDuration = events.stream()
                .filter(e -> e.getDurationMs() != null && "STEP".equals(e.getEventType()))
                .mapToLong(TraceEventEntity::getDurationMs)
                .sum();

        Map<String, Long> tokensByAgent = events.stream()
                .filter(e -> e.getTokensUsed() != null && e.getAgentName() != null)
                .collect(Collectors.groupingBy(
                        TraceEventEntity::getAgentName,
                        Collectors.summingLong(TraceEventEntity::getTokensUsed)));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runId", runId);
        summary.put("totalSpans", events.size());
        summary.put("totalTokens", totalTokens);
        summary.put("llmCalls", llmCalls);
        summary.put("totalDurationMs", totalDuration);
        summary.put("tokensByAgent", tokensByAgent);

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/traces/latency-trend")
    public ResponseEntity<List<Map<String, Object>>> getLatencyTrend() {
        // Returns aggregate LLM latency data across recent runs
        List<TraceEventEntity> llmEvents = traceEventRepository.findAll().stream()
                .filter(e -> "LLM_CALL".equals(e.getEventType()) && e.getDurationMs() != null)
                .sorted(Comparator.comparing(TraceEventEntity::getStartTime))
                .collect(Collectors.toList());

        // Group by agent name and compute average latency
        Map<String, List<TraceEventEntity>> byAgent = llmEvents.stream()
                .filter(e -> e.getAgentName() != null)
                .collect(Collectors.groupingBy(TraceEventEntity::getAgentName));

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map.Entry<String, List<TraceEventEntity>> entry : byAgent.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("agentName", entry.getKey());
            point.put("avgLatencyMs", entry.getValue().stream()
                    .mapToLong(TraceEventEntity::getDurationMs).average().orElse(0));
            point.put("callCount", entry.getValue().size());
            point.put("totalTokens", entry.getValue().stream()
                    .filter(e -> e.getTokensUsed() != null)
                    .mapToLong(TraceEventEntity::getTokensUsed).sum());
            trend.add(point);
        }

        return ResponseEntity.ok(trend);
    }
}
