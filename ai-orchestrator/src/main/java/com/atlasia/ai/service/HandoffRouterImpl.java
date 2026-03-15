package com.atlasia.ai.service;

import com.atlasia.ai.config.ChatPersonaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Validates handoff routing against each persona's YAML-defined allowed list.
 *
 * Rules come from the {@code handoff} section of each persona's YAML:
 * <pre>
 *   handoff:
 *     can_hand_off_to: [frontend-designer, qa-engineer]
 *     default_target: frontend-designer
 *     auto_trigger_when:
 *       - "API spec is finalized"
 *       - "backend implementation is complete"
 * </pre>
 */
@Service
public class HandoffRouterImpl implements HandoffRouter {

    private static final Logger log = LoggerFactory.getLogger(HandoffRouterImpl.class);

    private final ChatPersonaLoader personaLoader;

    public HandoffRouterImpl(ChatPersonaLoader personaLoader) {
        this.personaLoader = personaLoader;
    }

    @Override
    public Optional<String> resolveTarget(String fromPersonaId, String proposedTargetId) {
        var allowed = personaLoader.getAllowedHandoffTargets(fromPersonaId);

        if (allowed.contains(proposedTargetId)) {
            log.debug("Handoff approved: {} → {}", fromPersonaId, proposedTargetId);
            return Optional.of(proposedTargetId);
        }

        log.warn("Handoff rejected: {} → {} not in allowed list {}", fromPersonaId, proposedTargetId, allowed);
        return Optional.empty();
    }

    @Override
    public boolean isAutoTrigger(String fromPersonaId, String summary) {
        if (summary == null || summary.isBlank()) return false;
        String lower = summary.toLowerCase();
        return personaLoader.getAutoTriggers(fromPersonaId).stream()
                .anyMatch(trigger -> lower.contains(trigger.toLowerCase()));
    }

    @Override
    public String getDefaultTarget(String fromPersonaId) {
        return personaLoader.getDefaultTarget(fromPersonaId);
    }
}
