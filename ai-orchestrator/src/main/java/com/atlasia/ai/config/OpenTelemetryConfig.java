package com.atlasia.ai.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OpenTelemetryConfig {

    @Value("${otel.service.name:ai-orchestrator}")
    private String serviceName;

    @Value("${otel.exporter.otlp.endpoint:http://jaeger:4317}")
    private String otlpEndpoint;

    @Value("${management.tracing.sampling.probability:1.0}")
    private double samplingProbability;

    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put(AttributeKey.stringKey("service.name"), serviceName)
                        .put(AttributeKey.stringKey("service.version"), "0.1.0")
                        .put(AttributeKey.stringKey("deployment.environment"), System.getenv().getOrDefault("ENV", "development"))
                        .build()));

        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(otlpEndpoint)
                .setTimeout(Duration.ofSeconds(10))
                .build();

        SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter)
                        .setScheduleDelay(Duration.ofSeconds(5))
                        .setMaxQueueSize(2048)
                        .setMaxExportBatchSize(512)
                        .setExporterTimeout(Duration.ofSeconds(30))
                        .build())
                .setResource(resource)
                .setSampler(Sampler.traceIdRatioBased(samplingProbability))
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .buildAndRegisterGlobal();

        Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));

        return openTelemetry;
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.atlasia.ai", "0.1.0");
    }
}
