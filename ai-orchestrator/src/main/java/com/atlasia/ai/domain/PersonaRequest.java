package com.atlasia.ai.domain;

import java.util.List;
import java.util.UUID;

/**
 * Sealed hierarchy for all Chat Mode request types.
 *
 * <p>BEFORE — one flat DTO with nullable fields and a boolean mode flag.
 * Callers set different combinations of fields and hoped the service
 * would figure out the intent. Adding a new mode meant more nullable fields.</p>
 *
 * <p>AFTER — each subtype carries exactly the fields it needs.
 * The dispatch switch is exhaustive: adding a new subtype is a compile error
 * at every unhandled call site.</p>
 *
 * <pre>
 * switch (request) {
 *   case ChatRequest     c → chatService.chat(c.userId(), c.personaId(), c.message())
 *   case GenerateRequest g → codegenService.generate(g)
 *   case HandoffRequest  h → handoffService.accept(h)
 *   case ParallelRequest p → orchestrator.fanOut(p)
 * }
 * </pre>
 */
public sealed interface PersonaRequest
        permits PersonaRequest.ChatRequest,
                PersonaRequest.GenerateRequest,
                PersonaRequest.HandoffRequest,
                PersonaRequest.ParallelRequest {

    String userId();
    String personaId();

    // ── Chat: simple question/answer, no code generation ─────────────────────

    record ChatRequest(
            String userId,
            String personaId,
            String message
    ) implements PersonaRequest {}

    // ── Generate: produce structured JSON output with code files ─────────────

    record GenerateRequest(
            String userId,
            String personaId,
            String userMessage,
            boolean forceHandoff   // true = ask AI to conclude + hand off this turn
    ) implements PersonaRequest {

        /** Convenience: normal generation, no forced handoff. */
        public GenerateRequest(String userId, String personaId, String userMessage) {
            this(userId, personaId, userMessage, false);
        }
    }

    // ── Handoff: first message after receiving a handoff from another persona ─

    record HandoffRequest(
            String userId,
            String personaId,
            UUID   handoffId,      // the accepted handoff — briefing injected automatically
            String firstMessage    // user's first message to the new persona
    ) implements PersonaRequest {}

    // ── Parallel: fan-out to multiple personas simultaneously ─────────────────

    record ParallelRequest(
            String       userId,
            String       personaId,    // kept for interface compliance — use personaIds
            List<String> personaIds,
            String       prompt,
            ParallelStrategy strategy
    ) implements PersonaRequest {

        /** Fan-out strategy for parallel persona calls. */
        public enum ParallelStrategy {
            ALL_OR_NOTHING, // wait for all; fail if any fails
            FIRST_WINS,     // return as soon as the first persona responds
            BEST_EFFORT     // return all that succeed; ignore failures
        }
    }
}
