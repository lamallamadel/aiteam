package com.atlasia.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Submits persona calls to the rate-limited {@code aiCallExecutor} virtual thread pool.
 * Each call acquires a slot from the semaphore-bounded executor before hitting the LLM,
 * so concurrent AI API calls are throttled without blocking platform threads.
 */
@Service
public class AsyncPersonaServiceImpl implements AsyncPersonaService {

    private static final Logger log = LoggerFactory.getLogger(AsyncPersonaServiceImpl.class);

    private final ChatService chatService;
    private final Executor aiCallExecutor;

    public AsyncPersonaServiceImpl(
            ChatService chatService,
            @Qualifier("aiCallExecutor") Executor aiCallExecutor) {
        this.chatService = chatService;
        this.aiCallExecutor = aiCallExecutor;
    }

    @Override
    public CompletableFuture<String> callPersonaAsync(String userId, String personaId, String message) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            log.debug("Async persona call started: userId={} persona={}", userId, personaId);
            String reply = chatService.chat(userId, personaId, message);
            log.debug("Async persona call completed: userId={} persona={} elapsed={}ms",
                    userId, personaId, System.currentTimeMillis() - start);
            return reply;
        }, aiCallExecutor);
    }
}
