package com.atlasia.ai.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One circuit breaker per {@code persona.ai.providers} id for tiered LLM calls.
 */
@Component
public class LlmProviderCircuitBreakerFactory {

    private static final String NAME_PREFIX = "llmProvider-";

    private final CircuitBreakerRegistry registry;
    private final ConcurrentHashMap<String, CircuitBreaker> cache = new ConcurrentHashMap<>();

    public LlmProviderCircuitBreakerFactory(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    public CircuitBreaker forProvider(String providerId) {
        String name = NAME_PREFIX + providerId;
        return cache.computeIfAbsent(name, n -> {
            Optional<CircuitBreaker> existing = registry.find(n);
            if (existing.isPresent()) {
                return existing.get();
            }
            CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(4)
                    .failureRateThreshold(50f)
                    .waitDurationInOpenState(Duration.ofSeconds(25))
                    .automaticTransitionFromOpenToHalfOpenEnabled(true)
                    .build();
            return registry.circuitBreaker(n, cfg);
        });
    }
}
