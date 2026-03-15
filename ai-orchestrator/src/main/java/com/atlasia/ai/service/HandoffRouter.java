package com.atlasia.ai.service;

import java.util.Optional;

/**
 * Validates persona-to-persona handoff transitions using the routing rules
 * defined in each persona's YAML {@code handoff.can_hand_off_to} list.
 */
public interface HandoffRouter {

    /**
     * Returns the validated target persona ID if the transition is permitted,
     * or empty if {@code proposedTargetId} is not in the source persona's allowed list.
     */
    Optional<String> resolveTarget(String fromPersonaId, String proposedTargetId);

    /**
     * Returns true if the AI's reply summary matches any of the source persona's
     * {@code auto_trigger_when} phrases (case-insensitive contains check).
     */
    boolean isAutoTrigger(String fromPersonaId, String summary);

    /**
     * Returns the default handoff target for a persona, as defined in its YAML.
     * May be null if no default is configured.
     */
    String getDefaultTarget(String fromPersonaId);
}
