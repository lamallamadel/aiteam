package com.atlasia.ai.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Resilience4jConfiguration {
    private static final Logger log = LoggerFactory.getLogger(Resilience4jConfiguration.class);

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        
        registry.circuitBreaker("githubApi").getEventPublisher()
                .onStateTransition(event -> 
                        log.info("Circuit breaker state transition: {} -> {}, name={}", 
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState(),
                                event.getCircuitBreakerName()))
                .onError(event -> 
                        log.warn("Circuit breaker error: name={}, duration={}ms, throwable={}", 
                                event.getCircuitBreakerName(),
                                event.getElapsedDuration().toMillis(),
                                event.getThrowable().getClass().getSimpleName()))
                .onSuccess(event -> 
                        log.debug("Circuit breaker success: name={}, duration={}ms", 
                                event.getCircuitBreakerName(),
                                event.getElapsedDuration().toMillis()));
        
        return registry;
    }
}
