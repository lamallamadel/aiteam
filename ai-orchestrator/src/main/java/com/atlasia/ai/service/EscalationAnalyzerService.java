package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.EscalationInsightDto;
import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EscalationAnalyzerService {
    private static final Logger log = LoggerFactory.getLogger(EscalationAnalyzerService.class);

    private final RunRepository runRepository;

    public EscalationAnalyzerService(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public EscalationInsightDto analyzeEscalations() {
        List<RunEntity> runs = runRepository.findEscalatedRunsWithArtifacts();
        log.info("Analyzing {} escalated runs", runs.size());

        Map<String, Integer> patterns = new HashMap<>();
        Set<String> problematicFiles = new HashSet<>();

        for (RunEntity run : runs) {
            run.getArtifacts().stream()
                    .filter(a -> "escalation.json".equals(a.getArtifactType()))
                    .findFirst()
                    .ifPresent(artifact -> {
                        String payload = artifact.getPayload().toLowerCase();

                        // Simple heuristic-based clustering
                        if (payload.contains("timeout") || payload.contains("timed out")) {
                            patterns.merge("TIMEOUT", 1, Integer::sum);
                        } else if (payload.contains("compile") || payload.contains("syntax")) {
                            patterns.merge("COMPILATION_ERROR", 1, Integer::sum);
                        } else if (payload.contains("selector") || payload.contains("not found")) {
                            patterns.merge("E2E_SELECTOR_MISSING", 1, Integer::sum);
                        } else {
                            patterns.merge("UNCLASSIFIED", 1, Integer::sum);
                        }

                        // Extract potential file paths (very simple regex-like approach)
                        if (payload.contains(".java") || payload.contains(".ts")) {
                            // This would be more robust in a real implementation
                            problematicFiles.add("Detected in context of run " + run.getId());
                        }
                    });
        }

        List<EscalationInsightDto.EscalationCluster> clusters = patterns.entrySet().stream()
                .map(e -> new EscalationInsightDto.EscalationCluster(
                        e.getKey(),
                        e.getValue(),
                        getSuggestedRootCause(e.getKey())))
                .toList();

        return new EscalationInsightDto(
                runs.size(),
                patterns,
                new ArrayList<>(problematicFiles),
                clusters);
    }

    private String getSuggestedRootCause(String pattern) {
        return switch (pattern) {
            case "TIMEOUT" -> "Slow environment or infinite loops in code/tests";
            case "COMPILATION_ERROR" -> "Incomplete code generation or missing dependencies";
            case "E2E_SELECTOR_MISSING" -> "Frontend UI changes not reflected in E2E tests";
            default -> "Needs manual investigation";
        };
    }
}
