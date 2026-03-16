package com.atlasia.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * Typed configuration for all AI providers.
 * Bound from application.yml under the {@code persona.ai} prefix.
 *
 * <pre>
 * persona:
 *   ai:
 *     default-provider: openai
 *     max-concurrent-calls: 20
 *     persona-provider-map:
 *       architect: anthropic
 *       backend-developer: openai
 *     providers:
 *       anthropic:
 *         base-url: https://api.anthropic.com
 *         api-key: ${ANTHROPIC_API_KEY:}
 *         model: claude-opus-4-6
 *         type: ANTHROPIC
 *       openai:
 *         base-url: ${LLM_ENDPOINT:https://api.openai.com/v1}
 *         api-key: ${LLM_API_KEY:}
 *         model: ${LLM_MODEL:gpt-4o-mini}
 *         type: OPENAI
 * </pre>
 */
@ConfigurationProperties(prefix = "persona.ai")
public record AiProviderProperties(

        /** All configured providers, keyed by arbitrary provider ID */
        Map<String, ProviderConfig> providers,

        /** Provider ID used when a persona has no explicit mapping */
        @DefaultValue("openai") String defaultProvider,

        /** Global semaphore — caps concurrent AI calls across all providers */
        @DefaultValue("20") int maxConcurrentCalls,

        /** Explicit persona → provider ID routing (overrides default) */
        Map<String, String> personaProviderMap

) {
    public record ProviderConfig(
            String baseUrl,
            String apiKey,
            String model,
            ProviderType type,
            @DefaultValue("PT5S")  Duration connectTimeout,
            @DefaultValue("PT60S") Duration readTimeout,
            @DefaultValue("4096")  int maxTokens,
            @DefaultValue("0.7")   double temperature,
            Map<String, String> extraHeaders
    ) {}

    public enum ProviderType {
        ANTHROPIC,  // Claude — /v1/messages, x-api-key header, distinct message format
        OPENAI,     // OpenAI + compatible endpoints (vLLM, LM Studio, Together AI…)
        OLLAMA,     // Local Ollama — /api/chat, no auth required
        LITELLM     // LiteLLM proxy — OpenAI-compatible, routes to any backend
    }
}
