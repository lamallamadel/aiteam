package com.atlasia.ai.api.dto;

import java.util.Map;

/**
 * Result of a parallel persona fan-out call.
 *
 * @param successes  map of personaId → reply for every persona that responded successfully
 * @param failures   map of personaId → error message for every persona that failed or timed out
 * @param elapsedMs  wall-clock time from fan-out start to last response collected
 */
public record ParallelPersonaResult(
        Map<String, String> successes,
        Map<String, String> failures,
        long elapsedMs
) {
    public boolean isFullSuccess() {
        return failures.isEmpty();
    }

    public boolean hasAnySuccess() {
        return !successes.isEmpty();
    }

    public int totalAttempted() {
        return successes.size() + failures.size();
    }
}
