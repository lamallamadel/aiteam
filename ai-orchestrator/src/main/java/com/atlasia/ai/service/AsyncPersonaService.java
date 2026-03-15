package com.atlasia.ai.service;

import java.util.concurrent.CompletableFuture;

/**
 * Async wrapper around {@link ChatService} that executes persona calls on the
 * rate-limited {@code aiCallExecutor} virtual thread pool.
 */
public interface AsyncPersonaService {

    /**
     * Calls a persona and returns a future that completes with the reply.
     * The call is submitted to the bounded {@code aiCallExecutor} so concurrent
     * LLM requests are throttled without blocking platform threads.
     */
    CompletableFuture<String> callPersonaAsync(String userId, String personaId, String message);
}
