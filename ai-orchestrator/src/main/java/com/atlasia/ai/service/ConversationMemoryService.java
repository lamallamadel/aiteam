package com.atlasia.ai.service;

import com.atlasia.ai.model.ConversationTurnEntity;

import java.util.List;

public interface ConversationMemoryService {

    /**
     * Returns the last N turns (oldest→newest) for a user+persona pair,
     * ready for injection into the LLM system prompt.
     */
    List<ConversationTurnEntity> getContextWindow(String userId, String personaId);

    /**
     * Persists a user message and assistant reply atomically as two turns.
     */
    void saveTurns(String userId, String personaId, String userMessage, String assistantReply);

    /**
     * Deletes the session and all its turns — invoked by "New conversation" in the UI.
     */
    void clearSession(String userId, String personaId);

    /**
     * Formats a turn list into a single prompt block, e.g.:
     * <pre>
     * ## Conversation history
     * [user]: How do I center a div?
     * [assistant]: Use flexbox...
     * ## End of history
     * </pre>
     */
    String formatContextForPrompt(List<ConversationTurnEntity> turns);
}
