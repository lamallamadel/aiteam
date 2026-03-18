package com.atlasia.ai.api.adapter;

import com.atlasia.ai.api.dto.AiWireTypes.*;
import com.atlasia.ai.config.AiProviderProperties.ProviderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;

/**
 * Sealed interface — one implementation per provider type.
 *
 * <p>Each adapter translates {@link AiPrompt} → provider wire format → {@link AiResponse}.
 * All adapters are stateless; the {@link RestClient} is injected per call.</p>
 *
 * <p>Virtual thread behaviour: {@code restClient.retrieve().body(Class)} is a blocking
 * socket read. On a virtual thread, the JVM parks the thread at that point, releases
 * the carrier OS thread, and resumes when the response arrives. No busy-waiting.</p>
 */
public sealed interface ProviderAdapter
        permits ProviderAdapter.AnthropicAdapter,
                ProviderAdapter.OpenAiAdapter,
                ProviderAdapter.OllamaAdapter,
                ProviderAdapter.LiteLlmAdapter {

    /** Executes the AI call. Blocks the calling (virtual) thread. Never returns null. */
    AiResponse call(String providerId, AiPrompt prompt, RestClient client, ProviderConfig config);

    // =========================================================================
    // Anthropic — POST /v1/messages
    // =========================================================================

    final class AnthropicAdapter implements ProviderAdapter {
        private static final Logger log = LoggerFactory.getLogger(AnthropicAdapter.class);

        @Override
        public AiResponse call(String providerId, AiPrompt prompt,
                               RestClient client, ProviderConfig config) {
            long start = System.currentTimeMillis();

            var messages = new ArrayList<AnthropicMessage>();
            if (prompt.history() != null) {
                prompt.history().forEach(h ->
                        messages.add(new AnthropicMessage(h.get("role"), h.get("content"))));
            }
            messages.add(AnthropicMessage.user(prompt.userMessage()));

            var request = new AnthropicRequest(
                    config.model(), config.maxTokens(), config.temperature(),
                    messages, prompt.systemPrompt());

            log.debug("Anthropic call: model={} messages={}", config.model(), messages.size());

            AnthropicResponse response = client.post()
                    .uri("/v1/messages")
                    .body(request)
                    .retrieve()
                    .onStatus(s -> s.value() == 429, (req, res) -> {
                        throw new RateLimitException("Anthropic rate limit hit", providerId);
                    })
                    .onStatus(s -> s.is4xxClientError(), (req, res) -> {
                        throw new AiCallException("Anthropic 4xx: " + res.getStatusCode(), providerId);
                    })
                    .onStatus(s -> s.is5xxServerError(), (req, res) -> {
                        throw new AiCallException("Anthropic 5xx: " + res.getStatusCode(), providerId);
                    })
                    .body(AnthropicResponse.class);

            if (response == null) throw new AiCallException("Anthropic returned null body", providerId);

            long elapsed = System.currentTimeMillis() - start;
            log.debug("Anthropic ok: stopReason={} outputTokens={} elapsed={}ms",
                    response.stopReason(), response.usage().outputTokens(), elapsed);

            return new AiResponse(
                    response.firstTextContent(),
                    response.usage().inputTokens(),
                    response.usage().outputTokens(),
                    providerId, config.model(), elapsed);
        }
    }

    // =========================================================================
    // OpenAI — POST /v1/chat/completions
    // =========================================================================

    final class OpenAiAdapter implements ProviderAdapter {
        private static final Logger log = LoggerFactory.getLogger(OpenAiAdapter.class);

        @Override
        public AiResponse call(String providerId, AiPrompt prompt,
                               RestClient client, ProviderConfig config) {
            long start = System.currentTimeMillis();

            var messages = new ArrayList<OpenAiMessage>();
            messages.add(OpenAiMessage.system(prompt.systemPrompt()));
            if (prompt.history() != null) {
                prompt.history().forEach(h ->
                        messages.add(new OpenAiMessage(h.get("role"), h.get("content"))));
            }
            messages.add(OpenAiMessage.user(prompt.userMessage()));

            var request = new OpenAiRequest(
                    config.model(), config.maxTokens(), config.temperature(), messages);

            log.debug("OpenAI call: model={} messages={}", config.model(), messages.size());

            OpenAiResponse response = client.post()
                    .uri("/v1/chat/completions")
                    .body(request)
                    .retrieve()
                    .onStatus(s -> s.value() == 429, (req, res) -> {
                        throw new RateLimitException("OpenAI rate limit hit", providerId);
                    })
                    .onStatus(s -> s.is4xxClientError() || s.is5xxServerError(), (req, res) -> {
                        throw new AiCallException("OpenAI error: " + res.getStatusCode(), providerId);
                    })
                    .body(OpenAiResponse.class);

            if (response == null) throw new AiCallException("OpenAI returned null body", providerId);

            long elapsed = System.currentTimeMillis() - start;
            var usage = response.usage();
            return new AiResponse(
                    response.firstContent(),
                    usage != null ? usage.promptTokens() : 0,
                    usage != null ? usage.completionTokens() : 0,
                    providerId, config.model(), elapsed);
        }
    }

    // =========================================================================
    // Ollama — POST /api/chat (local)
    // =========================================================================

    final class OllamaAdapter implements ProviderAdapter {
        private static final Logger log = LoggerFactory.getLogger(OllamaAdapter.class);

        @Override
        public AiResponse call(String providerId, AiPrompt prompt,
                               RestClient client, ProviderConfig config) {
            long start = System.currentTimeMillis();

            var messages = new ArrayList<OllamaMessage>();
            messages.add(OllamaMessage.system(prompt.systemPrompt()));
            if (prompt.history() != null) {
                prompt.history().forEach(h ->
                        messages.add(new OllamaMessage(h.get("role"), h.get("content"))));
            }
            messages.add(OllamaMessage.user(prompt.userMessage()));

            var request = OllamaRequest.of(
                    config.model(), messages, config.maxTokens(), config.temperature());

            log.debug("Ollama call: model={}", config.model());

            OllamaResponse response = client.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .onStatus(s -> !s.is2xxSuccessful(), (req, res) -> {
                        throw new AiCallException("Ollama error: " + res.getStatusCode()
                                + " — is Ollama running on " + config.baseUrl() + "?", providerId);
                    })
                    .body(OllamaResponse.class);

            if (response == null) throw new AiCallException("Ollama returned null body", providerId);

            long elapsed = System.currentTimeMillis() - start;
            int tokens = response.evalCount() != null ? response.evalCount() : 0;
            return new AiResponse(
                    response.content(), 0, tokens, providerId, config.model(), elapsed);
        }
    }

    // =========================================================================
    // LiteLLM — OpenAI-compatible proxy, delegate to OpenAiAdapter
    // =========================================================================

    final class LiteLlmAdapter implements ProviderAdapter {
        private static final Logger log = LoggerFactory.getLogger(LiteLlmAdapter.class);
        private final OpenAiAdapter delegate = new OpenAiAdapter();

        @Override
        public AiResponse call(String providerId, AiPrompt prompt,
                               RestClient client, ProviderConfig config) {
            log.debug("LiteLLM call (via OpenAI adapter): model={}", config.model());
            return delegate.call(providerId, prompt, client, config);
        }
    }

    // =========================================================================
    // Typed exceptions — never raw RuntimeException
    // =========================================================================

    class AiCallException extends RuntimeException {
        private final String providerId;
        public AiCallException(String message, String providerId) {
            super("[" + providerId + "] " + message);
            this.providerId = providerId;
        }
        public String providerId() { return providerId; }
    }

    class RateLimitException extends AiCallException {
        public RateLimitException(String message, String providerId) {
            super(message, providerId);
        }
    }
}
