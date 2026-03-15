package com.atlasia.ai.service;

import com.atlasia.ai.model.PersonaHandoffEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core operations for Chat Mode persona-to-persona handoffs.
 */
public interface HandoffService {

    /** Creates and persists a handoff record. Returns the saved entity. */
    PersonaHandoffEntity createHandoff(String userId, String fromPersonaId,
                                       String fromSessionKey, HandoffSignal signal);

    /** Accepts a pending handoff and records the target session key. */
    PersonaHandoffEntity acceptHandoff(UUID handoffId, String toSessionKey);

    /**
     * Builds the markdown briefing block injected into the receiving persona's
     * first system prompt after the handoff is accepted.
     */
    String buildReceivingPersonaBriefing(UUID handoffId);

    /** Marks a handoff as COMPLETED. */
    void completeHandoff(UUID handoffId);

    /** Returns all PENDING handoffs for a user. */
    List<PersonaHandoffEntity> getPendingHandoffsForUser(String userId);

    /** Returns a handoff by ID, or empty if not found. */
    Optional<PersonaHandoffEntity> findById(UUID handoffId);
}
