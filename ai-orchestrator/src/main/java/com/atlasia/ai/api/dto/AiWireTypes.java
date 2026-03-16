package com.atlasia.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Provider wire-format DTOs + unified internal model.
 *
 * Each provider speaks a different JSON dialect. These records capture
 * exactly what each provider expects and returns, keeping adapters clean.
 * The rest of the application only ever sees {@link AiResponse} and {@link AiPrompt}.
 */
public final class AiWireTypes {

    private AiWireTypes() {}

    // =========================================================================
    // Anthropic — POST /v1/messages
    // =========================================================================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnthropicRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature,
            List<AnthropicMessage> messages,
            @JsonProperty("system") String systemPrompt
    ) {}

    public record AnthropicMessage(String role, String content) {
        public static AnthropicMessage user(String content)      { return new AnthropicMessage("user", content); }
        public static AnthropicMessage assistant(String content) { return new AnthropicMessage("assistant", content); }
    }

    public record AnthropicResponse(
            String id,
            String type,
            String role,
            List<AnthropicContent> content,
            @JsonProperty("stop_reason") String stopReason,
            AnthropicUsage usage
    ) {
        public String firstTextContent() {
            if (content == null || content.isEmpty()) return "";
            return content.stream()
                    .filter(c -> "text".equals(c.type()))
                    .findFirst()
                    .map(AnthropicContent::text)
                    .orElse("");
        }
    }

    public record AnthropicContent(String type, String text) {}

    public record AnthropicUsage(
            @JsonProperty("input_tokens")  int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}

    // =========================================================================
    // OpenAI / compatible (LiteLLM, vLLM) — POST /v1/chat/completions
    // =========================================================================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OpenAiRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature,
            List<OpenAiMessage> messages
    ) {}

    public record OpenAiMessage(String role, String content) {
        public static OpenAiMessage system(String content)    { return new OpenAiMessage("system", content); }
        public static OpenAiMessage user(String content)      { return new OpenAiMessage("user", content); }
        public static OpenAiMessage assistant(String content) { return new OpenAiMessage("assistant", content); }
    }

    public record OpenAiResponse(
            String id,
            String object,
            List<OpenAiChoice> choices,
            OpenAiUsage usage
    ) {
        public String firstContent() {
            if (choices == null || choices.isEmpty()) return "";
            return choices.getFirst().message().content();
        }
    }

    public record OpenAiChoice(
            int index,
            OpenAiMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    public record OpenAiUsage(
            @JsonProperty("prompt_tokens")     int promptTokens,
            @JsonProperty("completion_tokens") int completionTokens,
            @JsonProperty("total_tokens")      int totalTokens
    ) {}

    // =========================================================================
    // Ollama — POST /api/chat (local)
    // =========================================================================

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OllamaRequest(
            String model,
            List<OllamaMessage> messages,
            boolean stream,
            OllamaOptions options
    ) {
        public static OllamaRequest of(String model, List<OllamaMessage> messages,
                                       int maxTokens, double temperature) {
            return new OllamaRequest(model, messages, false,
                    new OllamaOptions(temperature, maxTokens));
        }
    }

    public record OllamaMessage(String role, String content) {
        public static OllamaMessage system(String content) { return new OllamaMessage("system", content); }
        public static OllamaMessage user(String content)   { return new OllamaMessage("user", content); }
    }

    public record OllamaOptions(double temperature, @JsonProperty("num_predict") int numPredict) {}

    public record OllamaResponse(
            String model,
            OllamaMessage message,
            boolean done,
            @JsonProperty("total_duration") Long totalDuration,
            @JsonProperty("eval_count")     Integer evalCount
    ) {
        public String content() { return message != null ? message.content() : ""; }
    }

    // =========================================================================
    // Unified internal model — used by the whole application
    // =========================================================================

    /**
     * Provider-agnostic prompt. Every adapter translates this into its wire format.
     */
    public record AiPrompt(
            String systemPrompt,
            String userMessage,
            List<Map<String, String>> history   // [{role, content}] — optional conversation history
    ) {
        /** Convenience constructor — no history. */
        public AiPrompt(String systemPrompt, String userMessage) {
            this(systemPrompt, userMessage, List.of());
        }
    }

    /**
     * Provider-agnostic response. The rest of the application never sees raw provider types.
     */
    public record AiResponse(
            String content,
            int inputTokens,
            int outputTokens,
            String providerId,
            String modelId,
            long latencyMillis
    ) {
        public int totalTokens() { return inputTokens + outputTokens; }
    }
}
