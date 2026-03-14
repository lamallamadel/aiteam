package com.atlasia.ai.config;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the OTLP span exporter with an in-memory one so Spring Boot's OpenTelemetry
 * auto-configuration boots without a live Jaeger/OTLP endpoint.
 * <p>
 * Import into any {@code @SpringBootTest} that loads the full application context:
 * <pre>{@code @Import(OpenTelemetryTestConfig.class)}</pre>
 * <p>
 * Virtual-threads note: if {@code spring.threads.virtual.enabled=true}, OTel context
 * propagation via {@code ThreadLocal} does not cross virtual-thread boundaries automatically.
 * Spring Boot 3.2+ handles {@code @Async} tasks via {@code ContextPropagatingTaskDecorator},
 * but manual {@code Thread.ofVirtual().start(...)} calls must wrap the runnable with
 * {@code Context.current().wrap(...)} to preserve the active span.
 */
@TestConfiguration
public class OpenTelemetryTestConfig {

    @Bean
    public InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

    @Bean
    @Primary
    public SpanExporter spanExporter(InMemorySpanExporter inMemorySpanExporter) {
        return inMemorySpanExporter;
    }
}
