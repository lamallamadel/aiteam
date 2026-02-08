package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.PersonaEffectivenessDto;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PersonaLearningService {

    private final RunRepository runRepository;

    public PersonaLearningService(RunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public PersonaEffectivenessDto analyzeEffectiveness() {
        List<RunEntity> runs = runRepository.findAll();
        Map<String, PersonaEffectivenessDto.PersonaMetrics> metricsMap = new HashMap<>();
        List<String> recommendations = new ArrayList<>();

        for (RunEntity run : runs) {
            run.getArtifacts().stream()
                    .filter(a -> "persona_review_report.json".equals(a.getArtifactType()))
                    .forEach(artifact -> {
                        String personaName = artifact.getAgentName(); // In this context, agentName is persona

                        PersonaEffectivenessDto.PersonaMetrics current = metricsMap.getOrDefault(personaName,
                                new PersonaEffectivenessDto.PersonaMetrics(0, 0, 1.0, 0));

                        int criticals = artifact.getPayload().contains("\"critical\"") ? 1 : 0;
                        int falsePositives = (criticals > 0 && run.getStatus() == RunStatus.DONE) ? 1 : 0;

                        metricsMap.put(personaName, new PersonaEffectivenessDto.PersonaMetrics(
                                current.reviewsCount() + 1,
                                current.criticalFindings() + criticals,
                                calculateEffectiveness(current, falsePositives),
                                current.falsePositives() + falsePositives));
                    });
        }

        // Generate recommendations
        metricsMap.forEach((name, metrics) -> {
            if (metrics.falsePositives() > 2) {
                recommendations.add("Persona '" + name
                        + "' shows high false positive rate. Consider relaxing rules in its YAML config.");
            }
            if (metrics.effectivenessScore() < 0.5) {
                recommendations.add("Persona '" + name
                        + "' findings are rarely correlated with run success. Review its mission statement.");
            }
        });

        return new PersonaEffectivenessDto(metricsMap, recommendations);
    }

    private double calculateEffectiveness(PersonaEffectivenessDto.PersonaMetrics current, int newFalsePositive) {
        int totalWarnings = current.criticalFindings() + 1; // Simplification
        int totalFalsePositives = current.falsePositives() + newFalsePositive;
        return 1.0 - ((double) totalFalsePositives / totalWarnings);
    }
}
