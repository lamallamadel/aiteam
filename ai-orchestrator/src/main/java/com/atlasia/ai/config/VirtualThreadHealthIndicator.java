package com.atlasia.ai.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health indicator that verifies virtual threads are active on the JVM.
 * Exposed via GET /actuator/health as the "virtualThreads" component.
 */
@Component("virtualThreads")
public class VirtualThreadHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            boolean[] isVirtual = {false};
            Thread vt = Thread.ofVirtual().name("vt-health-check").start(
                    () -> isVirtual[0] = Thread.currentThread().isVirtual());
            vt.join(1000);

            if (isVirtual[0]) {
                return Health.up()
                        .withDetail("virtualThreads", "active")
                        .withDetail("javaVersion", System.getProperty("java.version"))
                        .build();
            }
            return Health.down()
                    .withDetail("reason", "virtual thread reported isVirtual=false")
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down(e).withDetail("reason", "health check interrupted").build();
        }
    }
}
