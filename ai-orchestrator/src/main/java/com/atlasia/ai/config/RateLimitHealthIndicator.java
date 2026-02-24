package com.atlasia.ai.config;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RateLimitHealthIndicator implements HealthIndicator {

    private final RateLimiterRegistry rateLimiterRegistry;

    public RateLimitHealthIndicator(RateLimiterRegistry rateLimiterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @Override
    public Health health() {
        try {
            long rateLimiterCount = rateLimiterRegistry.getAllRateLimiters().size();
            
            return Health.up()
                    .withDetail("rateLimiters", rateLimiterCount)
                    .withDetail("status", "Rate limiting is operational")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
