package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.*;
import com.atlasia.ai.service.AnalyticsService;
import com.atlasia.ai.service.EscalationAnalyzerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final EscalationAnalyzerService escalationAnalyzerService;

    public AnalyticsController(AnalyticsService analyticsService, EscalationAnalyzerService escalationAnalyzerService) {
        this.analyticsService = analyticsService;
        this.escalationAnalyzerService = escalationAnalyzerService;
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
}
