package com.atlasia.ai.api;

import com.atlasia.ai.api.adapter.ProviderAdapter;
import com.atlasia.ai.api.adapter.ProviderAdapter.*;
import com.atlasia.ai.api.dto.AiWireTypes.AiPrompt;
import com.atlasia.ai.api.dto.AiWireTypes.AiResponse;
import com.atlasia.ai.config.AiProviderProperties;
import com.atlasia.ai.config.AiProviderProperties.ProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Routes Chat Mode AI calls to the correct provider based on persona.
 *
 * <p>Routing priority:</p>
 * <ol>
 *   <li>Explicit mapping in {@code persona.ai.persona-provider-map}</li>
 *   <li>Global default: {@code persona.ai.default-provider}</li>
 * </ol>
 *
 * <p>The semaphore caps total concurrent AI calls across all providers.
 * Virtual threads queue cheaply — no OS thread consumed while waiting.</p>
 *
 * <p>This router is only used by Chat Mode services ({@link com.atlasia.ai.service.ChatCodegenService},
 * {@link com.atlasia.ai.service.ChatService}, {@link com.atlasia.ai.service.ParallelPersonaOrchestrator}).
 * The pipeline's {@link com.atlasia.ai.service.LlmService} remains unchanged.</p>
 */
@Service
public class AiProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRouter.class);

    private final AiProviderProperties properties;
    private final Map<String, RestClient> restClients;
    private final Semaphore semaphore;
    private final Map<String, ProviderAdapter> adapters;

    /** Optional per-persona override map; injected from YAML. Empty map if not configured. */
    @Value("#{${persona.ai.persona-provider-map:{}}}")
    private Map<String, String> personaProviderMap;

    public AiProviderRouter(AiProviderProperties properties,
                             Map<String, RestClient> aiRestClients,
                             Semaphore aiCallSemaphore) {
        this.properties  = properties;
        this.restClients = aiRestClients;
        this.semaphore   = aiCallSemaphore;
        this.adapters    = Map.of(
                "ANTHROPIC", new AnthropicAdapter(),
                "OPENAI",    new OpenAiAdapter(),
                "OLLAMA",    new OllamaAdapter(),
                "LITELLM",   new LiteLlmAdapter()
        );
    }

    /**
     * Routes a prompt to the correct provider and executes the AI call.
     *
     * <p>Blocks the calling virtual thread in two places:</p>
     * <ol>
     *   <li>{@code semaphore.acquire()} — parks if max concurrent calls reached</li>
     *   <li>{@code adapter.call(...)}   — parks during HTTP socket read</li>
     * </ol>
     *
     * @param personaId used to look up provider routing
     * @param prompt    system prompt + user message + optional history
     * @return unified {@link AiResponse} — never null
     */
    public AiResponse call(String personaId, AiPrompt prompt) {
        if (restClients.isEmpty()) {
            throw new IllegalStateException(
                    "No AI providers configured under persona.ai.providers. "
                    + "Add at least one provider in application.yml.");
        }

        String providerId = resolveProvider(personaId);
        ProviderConfig config = requireConfig(providerId);
        RestClient client     = requireClient(providerId);
        ProviderAdapter adapter = requireAdapter(config);

        log.debug("Routing: persona={} → provider={} model={}", personaId, providerId, config.model());

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for AI call slot", e);
        }

        try {
            return adapter.call(providerId, prompt, client, config);

        } catch (RateLimitException e) {
            log.warn("Rate limit: provider={} — back off before retrying", providerId);
            throw e;

        } catch (ResourceAccessException e) {
            String hint = config.type() == AiProviderProperties.ProviderType.OLLAMA
                    ? " — is Ollama running? (ollama serve)"
                    : " — check network to " + config.baseUrl();
            throw new AiCallException("Connection failed to " + providerId + hint, providerId);

        } finally {
            semaphore.release();
        }
    }

    // ── Resolution helpers ────────────────────────────────────────────────────

    private String resolveProvider(String personaId) {
        if (personaProviderMap != null && personaProviderMap.containsKey(personaId)) {
            return personaProviderMap.get(personaId);
        }
        return properties.defaultProvider();
    }

    private ProviderConfig requireConfig(String providerId) {
        var cfg = properties.providers().get(providerId);
        if (cfg == null) throw new IllegalArgumentException(
                "No config for provider: " + providerId
                + ". Available: " + properties.providers().keySet());
        return cfg;
    }

    private RestClient requireClient(String providerId) {
        var client = restClients.get(providerId);
        if (client == null) throw new IllegalStateException(
                "No RestClient for provider: " + providerId);
        return client;
    }

    private ProviderAdapter requireAdapter(ProviderConfig config) {
        var adapter = adapters.get(config.type().name());
        if (adapter == null) throw new IllegalStateException(
                "No adapter for type: " + config.type());
        return adapter;
    }
}
