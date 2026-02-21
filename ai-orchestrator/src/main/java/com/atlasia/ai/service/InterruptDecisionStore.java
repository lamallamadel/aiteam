package com.atlasia.ai.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared store for pending interrupt decisions.
 *
 * WorkflowEngine parks a CompletableFuture here before blocking on human input.
 * OversightController calls complete() when the human submits their decision.
 * The separation keeps controller and service layers independent.
 */
@Service
public class InterruptDecisionStore {

    public record PendingApproval(
            UUID runId,
            String agentName,
            String ruleName,
            String tier,
            String message,
            Instant createdAt) {}

    private final ConcurrentHashMap<UUID, CompletableFuture<String>> futures  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PendingApproval>           metadata = new ConcurrentHashMap<>();

    /**
     * Register a pending approval request and return a future the caller can block on.
     * The future completes with the human's decision string ("approve" / "deny").
     */
    public CompletableFuture<String> park(UUID runId, String agentName,
                                          String ruleName, String tier, String message) {
        CompletableFuture<String> future = new CompletableFuture<>();
        futures.put(runId, future);
        metadata.put(runId, new PendingApproval(runId, agentName, ruleName, tier, message, Instant.now()));
        return future;
    }

    /**
     * Complete a parked future with the given decision.
     *
     * @return true if a future was found and completed, false if no pending request exists
     */
    public boolean complete(UUID runId, String decision) {
        metadata.remove(runId);
        CompletableFuture<String> f = futures.remove(runId);
        if (f != null) {
            f.complete(decision);
            return true;
        }
        return false;
    }

    /** Cancel a parked future (called on timeout or workflow abort). */
    public void cancel(UUID runId) {
        metadata.remove(runId);
        CompletableFuture<String> f = futures.remove(runId);
        if (f != null) f.cancel(true);
    }

    /** Read-only view of all pending approvals (for oversight UI). */
    public Collection<PendingApproval> getPending() {
        return Collections.unmodifiableCollection(metadata.values());
    }
}
