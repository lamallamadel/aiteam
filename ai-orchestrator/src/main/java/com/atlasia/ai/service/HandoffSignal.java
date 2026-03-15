package com.atlasia.ai.service;

/**
 * Carries the data needed to create a persona handoff.
 *
 * @param toPersonaId    ID of the persona that should receive the work
 * @param triggerReason  Human-readable reason shown to the user
 * @param handoffSummary What was accomplished — injected into the receiving persona's briefing
 * @param triggerType    How the handoff was initiated (AUTO_DETECTED, USER_REQUESTED)
 */
public record HandoffSignal(
        String toPersonaId,
        String triggerReason,
        String handoffSummary,
        String triggerType
) {}
