package com.atlasia.ai.service;

import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.service.exception.LlmServiceException;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final WebClient webClient;
    private final OrchestratorProperties.Llm llmConfig;
    private final ObjectMapper objectMapper;
    private final OrchestratorMetrics metrics;

    public LlmService(OrchestratorProperties properties, WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper, OrchestratorMetrics metrics) {
        this.llmConfig = properties.llm();
        this.objectMapper = objectMapper;
        this.metrics = metrics;

        ConnectionProvider provider = ConnectionProvider.builder("llm-pool")
                .maxIdleTime(Duration.ofSeconds(20))
                .evictInBackground(Duration.ofSeconds(60))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .responseTimeout(Duration.ofMinutes(2));

        this.webClient = webClientBuilder
                .baseUrl(llmConfig.endpoint())
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + llmConfig.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String generateCompletion(String systemPrompt, String userPrompt) {
        Timer.Sample sample = metrics.startLlmTimer();
        long startTime = System.currentTimeMillis();

        try {
            log.debug("LLM API call: generateCompletion, model={}, correlationId={}",
                    llmConfig.model(), CorrelationIdHolder.getCorrelationId());

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> requestBody = Map.of(
                    "model", llmConfig.model(),
                    "messages", messages);

            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(5))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError)
                            .doBeforeRetry(retrySignal -> log.warn(
                                    "Retrying LLM API call due to transient error: {}, attempt={}",
                                    retrySignal.failure().getMessage(), retrySignal.totalRetries() + 1)))
                    .block();

            String content = extractMessageContent(response);
            int tokensUsed = extractTokenUsage(response);

            long duration = System.currentTimeMillis() - startTime;
            sample.stop(metrics.getLlmDuration());
            metrics.recordLlmCall(llmConfig.model(), duration, tokensUsed);

            log.debug("LLM API call succeeded: duration={}ms, tokens={}, correlationId={}",
                    duration, tokensUsed, CorrelationIdHolder.getCorrelationId());

            return content;
        } catch (WebClientResponseException e) {
            handleLlmError(e, sample, startTime);
            throw e;
        } catch (WebClientRequestException e) {
            handleNetworkError(e, sample, startTime);
            throw e;
        } catch (Exception e) {
            handleGenericError(e, sample, startTime);
            throw e;
        }
    }

    public String generateStructuredOutput(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        Timer.Sample sample = metrics.startLlmTimer();
        long startTime = System.currentTimeMillis();

        try {
            log.debug("LLM API call: generateStructuredOutput, model={}, correlationId={}",
                    llmConfig.model(), CorrelationIdHolder.getCorrelationId());

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt));

            Map<String, Object> responseFormat = Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", "response",
                            "strict", true,
                            "schema", jsonSchema));

            Map<String, Object> requestBody = Map.of(
                    "model", llmConfig.model(),
                    "messages", messages,
                    "response_format", responseFormat);

            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(5))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isTransientError)
                            .doBeforeRetry(retrySignal -> log.warn(
                                    "Retrying LLM API call due to transient error: {}, attempt={}",
                                    retrySignal.failure().getMessage(), retrySignal.totalRetries() + 1)))
                    .block();

            String content = extractMessageContent(response);
            int tokensUsed = extractTokenUsage(response);

            long duration = System.currentTimeMillis() - startTime;
            sample.stop(metrics.getLlmDuration());
            metrics.recordLlmCall(llmConfig.model(), duration, tokensUsed);

            log.debug("LLM API call succeeded: duration={}ms, tokens={}, correlationId={}",
                    duration, tokensUsed, CorrelationIdHolder.getCorrelationId());

            return content;
        } catch (WebClientResponseException e) {
            handleLlmError(e, sample, startTime);
            throw e;
        } catch (WebClientRequestException e) {
            handleNetworkError(e, sample, startTime);
            throw e;
        } catch (Exception e) {
            handleGenericError(e, sample, startTime);
            throw e;
        }
    }

    public String generateStructuredOutput(String systemPrompt, String userPrompt, String jsonSchemaString) {
        try {
            JsonNode schemaNode = objectMapper.readTree(jsonSchemaString);
            Map<String, Object> jsonSchema = objectMapper.convertValue(schemaNode, Map.class);
            return generateStructuredOutput(systemPrompt, userPrompt, jsonSchema);
        } catch (Exception e) {
            log.error("Failed to parse JSON schema, correlationId={}", CorrelationIdHolder.getCorrelationId(), e);
            throw new LlmServiceException("Failed to parse JSON schema: " + e.getMessage(), e,
                    llmConfig.model(), LlmServiceException.LlmErrorType.INVALID_RESPONSE);
        }
    }

    private String extractMessageContent(Map<String, Object> response) {
        if (response == null) {
            throw new LlmServiceException("No response from LLM API", llmConfig.model(),
                    LlmServiceException.LlmErrorType.INVALID_RESPONSE);
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmServiceException("No choices in LLM API response", llmConfig.model(),
                    LlmServiceException.LlmErrorType.INVALID_RESPONSE);
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new LlmServiceException("No message in LLM API response", llmConfig.model(),
                    LlmServiceException.LlmErrorType.INVALID_RESPONSE);
        }

        String content = (String) message.get("content");
        if (content == null) {
            throw new LlmServiceException("No content in LLM API response message", llmConfig.model(),
                    LlmServiceException.LlmErrorType.INVALID_RESPONSE);
        }

        return content;
    }

    private int extractTokenUsage(Map<String, Object> response) {
        try {
            if (response != null && response.containsKey("usage")) {
                Map<String, Object> usage = (Map<String, Object>) response.get("usage");
                if (usage != null && usage.containsKey("total_tokens")) {
                    Object tokens = usage.get("total_tokens");
                    if (tokens instanceof Number) {
                        return ((Number) tokens).intValue();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract token usage: {}", e.getMessage());
        }
        return 0;
    }

    private void handleLlmError(WebClientResponseException e, Timer.Sample sample, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        sample.stop(metrics.getLlmDuration());

        int statusCode = e.getStatusCode().value();
        LlmServiceException.LlmErrorType errorType;

        if (statusCode == 429) {
            errorType = LlmServiceException.LlmErrorType.RATE_LIMIT;
            log.warn("LLM API rate limit hit: statusCode={}, duration={}ms, correlationId={}",
                    statusCode, duration, CorrelationIdHolder.getCorrelationId());
        } else if (statusCode == 401 || statusCode == 403) {
            errorType = LlmServiceException.LlmErrorType.AUTHENTICATION_ERROR;
            log.error("LLM API authentication error: statusCode={}, duration={}ms, correlationId={}",
                    statusCode, duration, CorrelationIdHolder.getCorrelationId());
        } else if (statusCode >= 500) {
            errorType = LlmServiceException.LlmErrorType.NETWORK_ERROR;
            log.error("LLM API server error: statusCode={}, duration={}ms, correlationId={}",
                    statusCode, duration, CorrelationIdHolder.getCorrelationId());
        } else if (statusCode == 400 && e.getResponseBodyAsString().contains("context_length_exceeded")) {
            errorType = LlmServiceException.LlmErrorType.CONTEXT_LENGTH_EXCEEDED;
            log.error("LLM API context length exceeded: duration={}ms, correlationId={}",
                    duration, CorrelationIdHolder.getCorrelationId());
        } else {
            errorType = LlmServiceException.LlmErrorType.INVALID_RESPONSE;
            log.error("LLM API error: statusCode={}, duration={}ms, correlationId={}",
                    statusCode, duration, CorrelationIdHolder.getCorrelationId());
        }

        metrics.recordLlmError(llmConfig.model(), errorType.name());

        throw new LlmServiceException(
                "LLM API error: " + e.getMessage(),
                e,
                llmConfig.model(),
                errorType);
    }

    private void handleNetworkError(WebClientRequestException e, Timer.Sample sample, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        sample.stop(metrics.getLlmDuration());

        log.error("LLM API network error: duration={}ms, correlationId={}",
                duration, CorrelationIdHolder.getCorrelationId(), e);

        metrics.recordLlmError(llmConfig.model(), "NETWORK_ERROR");

        throw new LlmServiceException(
                "LLM API network error: " + e.getMessage(),
                e,
                llmConfig.model(),
                LlmServiceException.LlmErrorType.NETWORK_ERROR);
    }

    private void handleGenericError(Exception e, Timer.Sample sample, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        sample.stop(metrics.getLlmDuration());

        log.error("LLM API unexpected error: duration={}ms, correlationId={}",
                duration, CorrelationIdHolder.getCorrelationId(), e);

        metrics.recordLlmError(llmConfig.model(), "UNKNOWN_ERROR");

        if (e.getMessage() != null && e.getMessage().contains("timeout")) {
            throw new LlmServiceException(
                    "LLM API timeout: " + e.getMessage(),
                    e,
                    llmConfig.model(),
                    LlmServiceException.LlmErrorType.TIMEOUT);
        }

        throw new LlmServiceException(
                "LLM API unexpected error: " + e.getMessage(),
                e,
                llmConfig.model(),
                LlmServiceException.LlmErrorType.INVALID_RESPONSE);
    }

    private boolean isTransientError(Throwable throwable) {
        return throwable instanceof WebClientRequestException ||
                (throwable instanceof WebClientResponseException &&
                        ((WebClientResponseException) throwable).getStatusCode().is5xxServerError());
    }
}
