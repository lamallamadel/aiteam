package com.atlasia.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Creates one {@link RestClient} per configured AI provider.
 *
 * <p>WHY RestClient + virtual threads:</p>
 * <ul>
 *   <li>{@code SimpleClientHttpRequestFactory} uses standard Java socket IO — virtual
 *       threads park at the socket read, freeing the carrier OS thread instantly.</li>
 *   <li>No reactive stack (WebClient) needed — synchronous code is simpler, easier
 *       to debug, and works correctly with {@code @Transactional} service methods.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class MultiProviderRestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(MultiProviderRestClientConfig.class);

    /**
     * Map of provider ID → pre-configured {@link RestClient}.
     * Injected into {@link com.atlasia.ai.api.AiProviderRouter} for per-call routing.
     */
    @Bean
    public Map<String, RestClient> aiRestClients(AiProviderProperties props,
                                                  RestClient.Builder builder) {
        Map<String, RestClient> clients = new HashMap<>();
        if (props.providers() == null || props.providers().isEmpty()) {
            log.info("No AI providers configured under persona.ai.providers — AiProviderRouter disabled");
            return clients;
        }
        props.providers().forEach((id, cfg) -> {
            clients.put(id, buildClient(builder, id, cfg));
            log.info("AI RestClient: id={} type={} url={} model={}", id, cfg.type(), cfg.baseUrl(), cfg.model());
        });
        return clients;
    }

    /**
     * Semaphore bounding total concurrent AI calls across all providers.
     * Virtual threads queue cheaply here — no OS thread consumed while waiting.
     */
    @Bean
    public Semaphore aiCallSemaphore(AiProviderProperties props) {
        return new Semaphore(props.maxConcurrentCalls());
    }

    // ── Internal builder ──────────────────────────────────────────────────────

    private RestClient buildClient(RestClient.Builder builder, String id,
                                   AiProviderProperties.ProviderConfig cfg) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) cfg.connectTimeout().toMillis());
        factory.setReadTimeout((int) cfg.readTimeout().toMillis());

        var b = builder.clone()
                .baseUrl(cfg.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT,       MediaType.APPLICATION_JSON_VALUE);

        b = switch (cfg.type()) {
            case ANTHROPIC -> b
                    .defaultHeader("x-api-key", cfg.apiKey())
                    .defaultHeader("anthropic-version",
                            cfg.extraHeaders() != null
                                    ? cfg.extraHeaders().getOrDefault("anthropic-version", "2023-06-01")
                                    : "2023-06-01");
            case OPENAI, LITELLM -> b
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.apiKey());
            case OLLAMA -> b;   // no auth for local Ollama
        };

        if (cfg.extraHeaders() != null) {
            for (var entry : cfg.extraHeaders().entrySet()) {
                if (!"anthropic-version".equals(entry.getKey())) {
                    b = b.defaultHeader(entry.getKey(), entry.getValue());
                }
            }
        }

        return b.build();
    }
}
