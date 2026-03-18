package com.atlasia.ai.domain;

import com.atlasia.ai.api.dto.CodegenResponse;

import java.util.List;
import java.util.UUID;

/**
 * Sealed result hierarchy for Chat Mode code generation.
 *
 * <p>Replaces {@link CodegenResponse} (boolean success + nullable fields) with
 * a type-safe hierarchy where each subtype carries exactly what it needs.</p>
 *
 * <pre>
 * BEFORE — one class, nullable fields, boolean flag:
 *   CodegenResponse(UUID runId, boolean success, String summary,
 *                   List&lt;ArtifactDto&gt; artifacts, List&lt;String&gt; nextSteps,
 *                   String errorMessage)
 *   Problems:
 *   - Nothing prevents accessing summary when success=false
 *   - Adding a new outcome requires more nullable fields
 *   - Callers must know the implicit field-combination rules
 *
 * AFTER — sealed hierarchy, no nulls, exhaustive switch:
 *   switch (result) {
 *     case Generated g          → ResponseEntity.ok(toDto(g))
 *     case Explanatory e        → ResponseEntity.ok(toDto(e))
 *     case GenerationFailed f   → ResponseEntity.status(f.httpStatus()).body(...)
 *   }
 * </pre>
 */
public sealed interface CodeGenerationResult
        permits CodeGenerationResult.Generated,
                CodeGenerationResult.Explanatory,
                CodeGenerationResult.GenerationFailed {

    String personaId();
    String userId();

    // =========================================================================
    // Files generated — normal outcome
    // =========================================================================

    /**
     * The persona produced one or more files and has no further routing to do.
     */
    record Generated(
            String personaId,
            String userId,
            UUID runId,
            String summary,
            List<CodegenResponse.ArtifactDto> artifacts,
            List<String> nextSteps,
            long generationMillis
    ) implements CodeGenerationResult {

        public int fileCount() { return artifacts != null ? artifacts.size() : 0; }

        public boolean hasNextSteps() { return nextSteps != null && !nextSteps.isEmpty(); }

        /** Convenience: convert to legacy CodegenResponse for backward-compatible callers. */
        public CodegenResponse toCodegenResponse() {
            return new CodegenResponse(runId, true, summary, artifacts, nextSteps, null);
        }
    }

    // =========================================================================
    // Explanatory response — no files, prose answer
    // =========================================================================

    /**
     * The persona answered in prose rather than generating code.
     * This is a legitimate outcome, not a failure.
     *
     * <p>Example: User asks "Should I use REST or GraphQL?" → Architect explains
     * tradeoffs, asks clarifying questions. No files, no error.</p>
     *
     * Previously this was indistinguishable from a {@code Generated} with an empty
     * artifact list — now the type system makes the intent explicit.
     */
    record Explanatory(
            String personaId,
            String userId,
            UUID runId,
            String response,
            List<String> followUpQuestions
    ) implements CodeGenerationResult {

        public boolean hasFollowUpQuestions() {
            return followUpQuestions != null && !followUpQuestions.isEmpty();
        }
    }

    // =========================================================================
    // Generation failed — always carry a kind, never just an exception
    // =========================================================================

    /**
     * Generation failed. Always carries a typed {@link FailureKind} — never just
     * a String message — so callers can make structured decisions (retry? HTTP status?).
     */
    record GenerationFailed(
            String personaId,
            String userId,
            String reason,
            FailureKind kind,
            Throwable cause             // nullable — not all failures have an exception
    ) implements CodeGenerationResult {

        /** Transient failures: client should retry after a delay. */
        public boolean isRetryable() {
            return kind == FailureKind.AI_TIMEOUT || kind == FailureKind.AI_RATE_LIMITED;
        }

        /** Recommended retry delay in seconds for retryable failures. */
        public int retryAfterSeconds() {
            return switch (kind) {
                case AI_TIMEOUT      -> 5;
                case AI_RATE_LIMITED -> 30;
                default              -> 0;
            };
        }

        /** HTTP status code appropriate for this failure kind. */
        public int httpStatus() {
            return switch (kind) {
                case AI_TIMEOUT         -> 504;
                case AI_RATE_LIMITED    -> 429;
                case PARSE_ERROR        -> 422;
                case PERSONA_NOT_FOUND  -> 404;
                case CONTEXT_TOO_LONG   -> 413;
                case UNKNOWN            -> 500;
            };
        }
    }

    // =========================================================================
    // Failure taxonomy
    // =========================================================================

    enum FailureKind {
        AI_TIMEOUT,         // LLM did not respond in time — retryable
        AI_RATE_LIMITED,    // Provider rate limit — retryable with backoff
        PARSE_ERROR,        // LLM returned non-JSON output — not retryable
        PERSONA_NOT_FOUND,  // No YAML definition for this personaId
        CONTEXT_TOO_LONG,   // Prompt + history exceeds model context window
        UNKNOWN             // Unexpected exception — check cause field
    }

    // =========================================================================
    // Factory methods — consistent construction everywhere
    // =========================================================================

    static Generated generated(String personaId, String userId, UUID runId,
                               String summary,
                               List<CodegenResponse.ArtifactDto> artifacts,
                               List<String> nextSteps, long millis) {
        return new Generated(personaId, userId, runId, summary, artifacts, nextSteps, millis);
    }

    static Explanatory explanatory(String personaId, String userId, UUID runId,
                                   String response, List<String> followUpQuestions) {
        return new Explanatory(personaId, userId, runId, response, followUpQuestions);
    }

    static GenerationFailed failed(String personaId, String userId,
                                   String reason, FailureKind kind) {
        return new GenerationFailed(personaId, userId, reason, kind, null);
    }

    static GenerationFailed failed(String personaId, String userId,
                                   String reason, FailureKind kind, Throwable cause) {
        return new GenerationFailed(personaId, userId, reason, kind, cause);
    }
}
