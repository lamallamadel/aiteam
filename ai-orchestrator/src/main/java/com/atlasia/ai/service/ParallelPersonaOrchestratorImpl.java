package com.atlasia.ai.service;

import com.atlasia.ai.api.dto.ParallelPersonaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * Parallel persona execution using {@link CompletableFuture} on virtual threads.
 *
 * All three strategies delegate individual calls to {@link AsyncPersonaService},
 * which submits each call to the semaphore-bounded {@code aiCallExecutor}.
 * No platform threads are blocked at any point.
 */
@Service
public class ParallelPersonaOrchestratorImpl implements ParallelPersonaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ParallelPersonaOrchestratorImpl.class);

    private final AsyncPersonaService asyncPersonaService;

    public ParallelPersonaOrchestratorImpl(AsyncPersonaService asyncPersonaService) {
        this.asyncPersonaService = asyncPersonaService;
    }

    // -------------------------------------------------------------------------
    // callAllParallel — wait for every persona; record successes and failures
    // -------------------------------------------------------------------------

    @Override
    public ParallelPersonaResult callAllParallel(
            String userId, List<String> personaIds, String message, long timeoutSeconds) {

        long start = System.currentTimeMillis();
        log.debug("callAllParallel: userId={} personas={} timeout={}s", userId, personaIds, timeoutSeconds);

        Map<String, CompletableFuture<String>> futures = new LinkedHashMap<>();
        for (String id : personaIds) {
            futures.put(id, asyncPersonaService.callPersonaAsync(userId, id, message));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.values().toArray(new CompletableFuture[0]));

        try {
            all.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("callAllParallel timed out after {}s — collecting partial results", timeoutSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("callAllParallel interrupted");
        } catch (ExecutionException e) {
            log.debug("callAllParallel: at least one future failed — collecting partial results");
        }

        Map<String, String> successes = new LinkedHashMap<>();
        Map<String, String> failures = new LinkedHashMap<>();

        futures.forEach((id, future) -> {
            if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                successes.put(id, future.join());
            } else if (future.isCompletedExceptionally()) {
                future.exceptionally(ex -> {
                    failures.put(id, ex.getMessage());
                    return null;
                }).join();
            } else {
                future.cancel(true);
                failures.put(id, "timed out after " + timeoutSeconds + "s");
            }
        });

        long elapsed = System.currentTimeMillis() - start;
        log.debug("callAllParallel done: successes={} failures={} elapsed={}ms",
                successes.size(), failures.size(), elapsed);
        return new ParallelPersonaResult(successes, failures, elapsed);
    }

    // -------------------------------------------------------------------------
    // racePersonas — return the first successful reply
    // -------------------------------------------------------------------------

    @Override
    public Optional<String> racePersonas(
            String userId, List<String> personaIds, String message, long timeoutSeconds) {

        log.debug("racePersonas: userId={} personas={} timeout={}s", userId, personaIds, timeoutSeconds);

        List<CompletableFuture<String>> futures = personaIds.stream()
                .map(id -> asyncPersonaService.callPersonaAsync(userId, id, message))
                .toList();

        CompletableFuture<Object> race = CompletableFuture.anyOf(
                futures.toArray(new CompletableFuture[0]));

        try {
            Object winner = race.get(timeoutSeconds, TimeUnit.SECONDS);
            futures.forEach(f -> f.cancel(true));
            return Optional.ofNullable(winner != null ? winner.toString() : null);
        } catch (TimeoutException e) {
            log.warn("racePersonas: no persona responded within {}s", timeoutSeconds);
            futures.forEach(f -> f.cancel(true));
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            futures.forEach(f -> f.cancel(true));
            return Optional.empty();
        } catch (ExecutionException e) {
            futures.forEach(f -> f.cancel(true));
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // callBestEffort — collect everything that arrives before the deadline
    // -------------------------------------------------------------------------

    @Override
    public ParallelPersonaResult callBestEffort(
            String userId, List<String> personaIds, String message, long timeoutSeconds) {

        long start = System.currentTimeMillis();
        long deadline = start + TimeUnit.SECONDS.toMillis(timeoutSeconds);

        log.debug("callBestEffort: userId={} personas={} timeout={}s", userId, personaIds, timeoutSeconds);

        Map<String, CompletableFuture<String>> futures = new LinkedHashMap<>();
        for (String id : personaIds) {
            futures.put(id, asyncPersonaService.callPersonaAsync(userId, id, message));
        }

        Map<String, String> successes = Collections.synchronizedMap(new LinkedHashMap<>());
        Map<String, String> failures = Collections.synchronizedMap(new LinkedHashMap<>());

        List<CompletableFuture<Void>> collectors = futures.entrySet().stream()
                .map(entry -> entry.getValue()
                        .thenAccept(reply -> successes.put(entry.getKey(), reply))
                        .exceptionally(ex -> {
                            failures.put(entry.getKey(), ex.getMessage());
                            return null;
                        }))
                .toList();

        long remaining = deadline - System.currentTimeMillis();
        if (remaining > 0) {
            try {
                CompletableFuture.allOf(collectors.toArray(new CompletableFuture[0]))
                        .get(remaining, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.debug("callBestEffort: deadline reached — partial results collected");
                futures.forEach((id, f) -> {
                    if (!f.isDone()) {
                        f.cancel(true);
                        if (!successes.containsKey(id)) {
                            failures.put(id, "timed out after " + timeoutSeconds + "s");
                        }
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                log.debug("callBestEffort: execution exception during collection", e);
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.debug("callBestEffort done: successes={} failures={} elapsed={}ms",
                successes.size(), failures.size(), elapsed);
        return new ParallelPersonaResult(
                Collections.unmodifiableMap(successes),
                Collections.unmodifiableMap(failures),
                elapsed);
    }
}
