package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.ParallelPersonaResult;

import java.util.List;
import java.util.Optional;

/**
 * Runs multiple persona calls in parallel using virtual threads.
 *
 * Three strategies are provided to match different Chat Mode use cases:
 * <ul>
 *   <li>{@code callAllParallel}  — fan-out to all personas; fails fast if any one fails</li>
 *   <li>{@code racePersonas}     — returns the first successful reply, cancels the rest</li>
 *   <li>{@code callBestEffort}   — collects every reply that arrives before the timeout</li>
 * </ul>
 */
public interface ParallelPersonaOrchestrator {

    /**
     * Calls all personas in parallel and waits for every one to complete.
     * If any call fails or times out the result will contain that failure in the failures map.
     *
     * @param timeoutSeconds wall-clock deadline for the entire fan-out
     */
    ParallelPersonaResult callAllParallel(
            String userId, List<String> personaIds, String message, long timeoutSeconds);

    /**
     * Races all personas and returns the reply from whichever responds first.
     * Useful when several personas could answer a question and speed matters.
     *
     * @param timeoutSeconds max wait before returning empty
     */
    Optional<String> racePersonas(
            String userId, List<String> personaIds, String message, long timeoutSeconds);

    /**
     * Calls all personas and collects every reply that arrives before the timeout.
     * Stragglers are ignored; at least one success is enough.
     *
     * @param timeoutSeconds wall-clock deadline for collection
     */
    ParallelPersonaResult callBestEffort(
            String userId, List<String> personaIds, String message, long timeoutSeconds);
}
