package com.atlasia.ai.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class RateLimitMetricsExporter {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final MeterRegistry meterRegistry;

    public RateLimitMetricsExporter(RateLimiterRegistry rateLimiterRegistry, MeterRegistry meterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void exportMetrics() {
        rateLimiterRegistry.getAllRateLimiters().forEach(rateLimiter -> {
            String name = rateLimiter.getName();
            
            Gauge.builder("resilience4j.ratelimiter.available.permissions", rateLimiter,
                    r -> r.getMetrics().getAvailablePermissions())
                    .tag("name", name)
                    .description("The number of available permissions")
                    .register(meterRegistry);
            
            Gauge.builder("resilience4j.ratelimiter.waiting.threads", rateLimiter,
                    r -> r.getMetrics().getNumberOfWaitingThreads())
                    .tag("name", name)
                    .description("The number of waiting threads")
                    .register(meterRegistry);
        });
    }
}
