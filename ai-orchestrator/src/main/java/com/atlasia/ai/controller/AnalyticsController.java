package com.atlasia.ai.controller;

import com.atlasia.ai.api.dto.AgentPerformanceDto;
import com.atlasia.ai.api.dto.AnalyticsSummaryDto;
import com.atlasia.ai.api.dto.EscalationInsightDto;
import com.atlasia.ai.api.dto.PersonaEffectivenessDto;
import com.atlasia.ai.service.AnalyticsService;
import com.atlasia.ai.service.EscalationAnalyzerService;
import com.atlasia.ai.service.PersonaLearningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final EscalationAnalyzerService escalationAnalyzerService;
    private final PersonaLearningService personaLearningService;

    public AnalyticsController(AnalyticsService analyticsService,
            EscalationAnalyzerService escalationAnalyzerService,
            PersonaLearningService personaLearningService) {
        this.analyticsService = analyticsService;
        this.escalationAnalyzerService = escalationAnalyzerService;
        this.personaLearningService = personaLearningService;
    }

    @GetMapping("/runs/summary")
    public ResponseEntity<AnalyticsSummaryDto> getRunSummary() {
        return ResponseEntity.ok(analyticsService.getSummary());
    }

    @GetMapping("/agents/performance")
    public ResponseEntity<AgentPerformanceDto> getAgentPerformance() {
        return ResponseEntity.ok(analyticsService.getPerformance());
    }

    @GetMapping("/escalations/insights")
    public ResponseEntity<EscalationInsightDto> getEscalationInsights() {
        return ResponseEntity.ok(escalationAnalyzerService.analyzeEscalations());
    }

    @GetMapping("/personas/effectiveness")
    public ResponseEntity<PersonaEffectivenessDto> getPersonaEffectiveness() {
        return ResponseEntity.ok(personaLearningService.analyzeEffectiveness());
    }
}
