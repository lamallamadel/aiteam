package com.atlasia.ai.service;

import com.atlasia.ai.api.adapter.ProviderAdapter;
import com.atlasia.ai.api.dto.AiWireTypes.AiPrompt;
import com.atlasia.ai.api.dto.AiWireTypes.AiResponse;
import com.atlasia.ai.config.AiProviderConfigUtil;
import com.atlasia.ai.config.AiProviderProperties;
import com.atlasia.ai.config.ModelTierProperties;
import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.model.TaskComplexity;
import com.atlasia.ai.service.event.WorkflowEvent;
import com.atlasia.ai.service.event.WorkflowEventBus;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Routes pipeline LLM calls through {@code atlasia.model-tiers} (dual legs, budget, circuit breakers).
 */
@Service
public class TieredLlmExecutor {

    private static final Logger log = LoggerFactory.getLogger(TieredLlmExecutor.class);

    private final ModelTierProperties modelTierProperties;
    private final AiProviderProperties aiProviderProperties;
    private final Map<String, RestClient> restClients;
    private final Semaphore aiCallSemaphore;
    private final BudgetTracker budgetTracker;
    private final LlmProviderCircuitBreakerFactory circuitBreakerFactory;
    private final OrchestratorMetrics metrics;
    private final WorkflowEventBus eventBus;
    private final WebClient webClient;

    /** For {@code availability: sticky-until-failure} — last successful leg per run/tier/op. */
    private final ConcurrentHashMap<String, String> stickyPreferredLegRole = new ConcurrentHashMap<>();

    private final Map<String, ProviderAdapter> adapters = Map.of(
            "ANTHROPIC", new ProviderAdapter.AnthropicAdapter(),
            "OPENAI", new ProviderAdapter.OpenAiAdapter(),
            "OLLAMA", new ProviderAdapter.OllamaAdapter(),
            "LITELLM", new ProviderAdapter.LiteLlmAdapter());

    public TieredLlmExecutor(
            ModelTierProperties modelTierProperties,
            AiProviderProperties aiProviderProperties,
            Map<String, RestClient> aiRestClients,
            Semaphore aiCallSemaphore,
            BudgetTracker budgetTracker,
            LlmProviderCircuitBreakerFactory circuitBreakerFactory,
            OrchestratorMetrics metrics,
            WorkflowEventBus eventBus,
            OrchestratorProperties orchestratorProperties,
            WebClient.Builder webClientBuilder) {
        this.modelTierProperties = modelTierProperties;
        this.aiProviderProperties = aiProviderProperties;
        this.restClients = aiRestClients;
        this.aiCallSemaphore = aiCallSemaphore;
        this.budgetTracker = budgetTracker;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.metrics = metrics;
        this.eventBus = eventBus;

        var llmConfig = orchestratorProperties.llm();
        ConnectionProvider provider = ConnectionProvider.builder("llm-tiered-pool")
                .maxIdleTime(Duration.ofSeconds(20))
                .evictInBackground(Duration.ofSeconds(60))
                .build();
        HttpClient httpClient = HttpClient.create(provider).responseTimeout(Duration.ofMinutes(2));
        if (llmConfig.proxyHost() != null && !llmConfig.proxyHost().isEmpty()) {
            httpClient = httpClient.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP)
                    .host(llmConfig.proxyHost())
                    .port(llmConfig.proxyPort() != null ? llmConfig.proxyPort() : 8080));
        }
        this.webClient = webClientBuilder
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String complete(String systemPrompt, String userPrompt, TaskComplexity complexity, Supplier<String> legacy) {
        if (!tierRoutingActive()) {
            return legacy.get();
        }
        TaskComplexity effective = budgetTracker.adjustForBudget(complexity);

        boolean acquired = false;
        try {
            aiCallSemaphore.acquire();
            acquired = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return legacy.get();
        }

        io.micrometer.core.instrument.Timer.Sample sample = metrics.startLlmTimer();
        long start = System.currentTimeMillis();

        boolean finished = false;
        try {
            TaskComplexity tierAttempt = effective;
            while (tierRoutingActive()) {
                ModelTierProperties.TierDefinition tierDef =
                        modelTierProperties.getTiers().get(tierAttempt.yamlKey());
                if (tierDef == null || tierDef.getDual() == null) {
                    break;
                }
                emitLlmStart(resolveDisplayModel(tierDef, tierAttempt));
                List<LegTry> order = legsInOrder(tierDef, tierAttempt, "complete");
                for (int i = 0; i < order.size(); i++) {
                    LegTry lt = order.get(i);
                    try {
                        AiCallResult r = invokeCompletionLeg(lt, systemPrompt, userPrompt);
                        if (r != null) {
                            if (i > 0) {
                                metrics.recordLlmDualFailover(
                                        tierAttempt.name(), order.get(0).role(), lt.role());
                            }
                            rememberStickyLeg(tierDef, tierAttempt, "complete", lt.role());
                            finishCompletionSuccess(sample, start, r, lt.leg());
                            finished = true;
                            return r.content();
                        }
                    } catch (CallNotPermittedException e) {
                        log.warn("LLM circuit open for provider {}", lt.leg().getProviderId());
                    } catch (Exception e) {
                        log.warn(
                                "Tiered LLM leg failed: tier={} leg={} provider={} msg={}",
                                tierAttempt.yamlKey(),
                                lt.role(),
                                lt.leg().getProviderId(),
                                e.getMessage());
                    }
                }
                if (tierAttempt == TaskComplexity.TRIVIAL) {
                    break;
                }
                TaskComplexity next = tierAttempt.downgrade();
                if (next == tierAttempt) {
                    break;
                }
                log.warn(
                        "Tiered LLM: all legs failed for tier {}, retrying with lower tier {}",
                        tierAttempt.yamlKey(),
                        next.yamlKey());
                tierAttempt = next;
            }
        } finally {
            if (acquired) {
                aiCallSemaphore.release();
            }
            if (!finished) {
                sample.stop(metrics.getLlmDuration());
            }
        }
        return legacy.get();
    }

    public LlmResult structured(
            String systemPrompt,
            String userPrompt,
            Map<String, Object> jsonSchema,
            TaskComplexity complexity,
            Supplier<LlmResult> legacy) {
        if (!tierRoutingActive()) {
            return legacy.get();
        }
        TaskComplexity effective = budgetTracker.adjustForBudget(complexity);

        boolean acquired = false;
        try {
            aiCallSemaphore.acquire();
            acquired = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return legacy.get();
        }

        io.micrometer.core.instrument.Timer.Sample sample = metrics.startLlmTimer();
        long start = System.currentTimeMillis();

        boolean finished = false;
        try {
            TaskComplexity tierAttempt = effective;
            while (tierRoutingActive()) {
                ModelTierProperties.TierDefinition tierDef =
                        modelTierProperties.getTiers().get(tierAttempt.yamlKey());
                if (tierDef == null || tierDef.getDual() == null) {
                    break;
                }
                emitLlmStart(resolveDisplayModel(tierDef, tierAttempt));
                List<LegTry> order = legsInOrder(tierDef, tierAttempt, "structured");
                for (int i = 0; i < order.size(); i++) {
                    LegTry lt = order.get(i);
                    try {
                        StructuredLegOutcome out = invokeStructuredLeg(lt, systemPrompt, userPrompt, jsonSchema);
                        if (out != null) {
                            if (i > 0) {
                                metrics.recordLlmDualFailover(
                                        tierAttempt.name(), order.get(0).role(), lt.role());
                            }
                            rememberStickyLeg(tierDef, tierAttempt, "structured", lt.role());
                            finishStructuredSuccess(sample, start, out, lt.leg());
                            finished = true;
                            return out.result();
                        }
                    } catch (CallNotPermittedException e) {
                        log.warn("LLM circuit open for provider {}", lt.leg().getProviderId());
                    } catch (Exception e) {
                        log.warn(
                                "Tiered structured LLM leg failed: tier={} leg={} provider={} msg={}",
                                tierAttempt.yamlKey(),
                                lt.role(),
                                lt.leg().getProviderId(),
                                e.getMessage());
                    }
                }
                if (tierAttempt == TaskComplexity.TRIVIAL) {
                    break;
                }
                TaskComplexity next = tierAttempt.downgrade();
                if (next == tierAttempt) {
                    break;
                }
                log.warn(
                        "Tiered structured LLM: all legs failed for tier {}, retrying with lower tier {}",
                        tierAttempt.yamlKey(),
                        next.yamlKey());
                tierAttempt = next;
            }
        } finally {
            if (acquired) {
                aiCallSemaphore.release();
            }
            if (!finished) {
                sample.stop(metrics.getLlmDuration());
            }
        }
        return legacy.get();
    }

    private boolean tierRoutingActive() {
        return modelTierProperties.getTiers() != null && !modelTierProperties.getTiers().isEmpty();
    }

    private String resolveDisplayModel(ModelTierProperties.TierDefinition tier, TaskComplexity effective) {
        if (tier.getDual() != null && tier.getDual().getPrimary() != null) {
            String mid = tier.getDual().getPrimary().getModel();
            if (mid != null && !mid.isBlank()) {
                return mid;
            }
        }
        return effective.name();
    }

    private List<LegTry> legsInOrder(
            ModelTierProperties.TierDefinition tier, TaskComplexity effective, String opKind) {
        List<LegTry> legs = new ArrayList<>();
        ModelTierProperties.DualDefinition d = tier.getDual();
        if (d == null) {
            return legs;
        }
        if (d.getPrimary() != null && d.getPrimary().getProviderId() != null) {
            legs.add(new LegTry("primary", d.getPrimary()));
        }
        if (d.getSecondary() != null && d.getSecondary().getProviderId() != null) {
            legs.add(new LegTry("secondary", d.getSecondary()));
        }
        if (!isStickyAvailability(tier) || legs.size() < 2) {
            return legs;
        }
        String pref = stickyPreferredLegRole.get(stickyCacheKey(effective, opKind));
        if (pref == null || pref.isBlank()) {
            return legs;
        }
        List<LegTry> reordered = new ArrayList<>();
        for (LegTry l : legs) {
            if (pref.equals(l.role())) {
                reordered.add(l);
            }
        }
        for (LegTry l : legs) {
            if (!pref.equals(l.role())) {
                reordered.add(l);
            }
        }
        return reordered;
    }

    private static boolean isStickyAvailability(ModelTierProperties.TierDefinition tier) {
        return tier != null && "sticky-until-failure".equalsIgnoreCase(tier.getAvailability());
    }

    private static String stickyCacheKey(TaskComplexity effective, String opKind) {
        String run = CorrelationIdHolder.getRunId();
        return (run != null && !run.isBlank() ? run : "_") + "|" + effective.yamlKey() + "|" + opKind;
    }

    private void rememberStickyLeg(
            ModelTierProperties.TierDefinition tier, TaskComplexity effective, String opKind, String legRole) {
        if (!isStickyAvailability(tier)) {
            return;
        }
        stickyPreferredLegRole.put(stickyCacheKey(effective, opKind), legRole);
    }

    private AiCallResult invokeCompletionLeg(LegTry lt, String system, String user) throws Exception {
        ModelTierProperties.LegDefinition leg = lt.leg();
        String pid = leg.getProviderId();
        if (pid == null || pid.isBlank()) {
            return null;
        }
        AiProviderProperties.ProviderConfig base = providerConfig(pid);
        if (base == null) {
            return null;
        }
        AiProviderProperties.ProviderConfig cfg = AiProviderConfigUtil.withModel(base, leg.getModel());
        RestClient client = restClients.get(pid);
        if (client == null) {
            return null;
        }
        ProviderAdapter adapter = adapters.get(cfg.type().name());
        if (adapter == null) {
            return null;
        }

        CircuitBreaker cb = circuitBreakerFactory.forProvider(pid);
        AiResponse resp = cb.executeCallable(
                () -> adapter.call(pid, new AiPrompt(system, user, List.of()), client, cfg));
        return new AiCallResult(resp.content(), resp.inputTokens(), resp.outputTokens(), cfg.model());
    }

    private StructuredLegOutcome invokeStructuredLeg(
            LegTry lt, String system, String user, Map<String, Object> jsonSchema) throws Exception {
        ModelTierProperties.LegDefinition leg = lt.leg();
        String pid = leg.getProviderId();
        if (pid == null || pid.isBlank()) {
            return null;
        }
        AiProviderProperties.ProviderConfig base = providerConfig(pid);
        if (base == null) {
            return null;
        }
        AiProviderProperties.ProviderConfig cfg = AiProviderConfigUtil.withModel(base, leg.getModel());
        if (cfg.type() != AiProviderProperties.ProviderType.OPENAI
                && cfg.type() != AiProviderProperties.ProviderType.LITELLM) {
            return null;
        }

        CircuitBreaker cb = circuitBreakerFactory.forProvider(pid);
        String endpoint = normalizeEndpoint(cfg.baseUrl());
        String model = cfg.model();
        String apiKey = cfg.apiKey();

        AtomicReference<LlmResultSource> sourceRef = new AtomicReference<>(LlmResultSource.PRIMARY);
        ParameterizedTypeReference<Map<String, Object>> mapType = new ParameterizedTypeReference<>() {};

        List<Map<String, String>> messages = new ArrayList<>();
        if (system != null && !system.isEmpty()) {
            messages.add(Map.of("role", "system", "content", system));
        }
        messages.add(Map.of("role", "user", "content", user));

        Map<String, Object> responseFormatSchema =
                Map.of("type", "json_schema", "json_schema", Map.of("name", "response", "strict", true, "schema", jsonSchema));
        Map<String, Object> requestBodySchema = new HashMap<>();
        requestBodySchema.put("model", model);
        requestBodySchema.put("messages", messages);
        requestBodySchema.put("response_format", responseFormatSchema);

        String jsonOnlyHint =
                "\n\nYou must respond with a single JSON object only (no markdown code fences) matching the expected structure.";
        List<Map<String, String>> messagesJsonObject = new ArrayList<>();
        if (system != null && !system.isEmpty()) {
            messagesJsonObject.add(Map.of("role", "system", "content", system + jsonOnlyHint));
        } else {
            messagesJsonObject.add(Map.of(
                    "role", "system",
                    "content", "You must respond with a single JSON object only (no markdown code fences)."
                            + jsonOnlyHint));
        }
        messagesJsonObject.add(Map.of("role", "user", "content", user));
        Map<String, Object> requestBodyJsonObject = new HashMap<>();
        requestBodyJsonObject.put("model", model);
        requestBodyJsonObject.put("messages", messagesJsonObject);
        requestBodyJsonObject.put("response_format", Map.of("type", "json_object"));

        String correlationId = CorrelationIdHolder.getCorrelationId();

        return cb.executeCallable(() -> {
            Mono<Map<String, Object>> primaryMono = webClient
                    .post()
                    .uri(endpoint + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Correlation-ID", correlationId != null ? correlationId : "")
                    .bodyValue(requestBodySchema)
                    .retrieve()
                    .bodyToMono(mapType)
                    .timeout(Duration.ofMinutes(5))
                    .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)).filter(this::isTransientError))
                    .doOnSuccess(r -> sourceRef.set(LlmResultSource.PRIMARY));

            Map<String, Object> response;
            try {
                response = primaryMono.block();
            } catch (Exception e) {
                log.warn("Structured json_schema on {} failed: {}", pid, e.getMessage());
                response = null;
            }
            if (response == null) {
                try {
                    response = webClient
                            .post()
                            .uri(endpoint + "/chat/completions")
                            .header("Authorization", "Bearer " + apiKey)
                            .header("X-Correlation-ID", correlationId != null ? correlationId : "")
                            .bodyValue(requestBodyJsonObject)
                            .retrieve()
                            .bodyToMono(mapType)
                            .timeout(Duration.ofMinutes(5))
                            .block();
                    sourceRef.set(LlmResultSource.FALLBACK);
                } catch (Exception e2) {
                    throw e2;
                }
            }
            if (response == null) {
                return null;
            }
            StructuredParse sp = parseFromMap(response);
            return new StructuredLegOutcome(new LlmResult(sp.content(), sourceRef.get()), sp);
        });
    }

    private void finishCompletionSuccess(
            io.micrometer.core.instrument.Timer.Sample sample,
            long start,
            AiCallResult r,
            ModelTierProperties.LegDefinition leg) {
        long duration = System.currentTimeMillis() - start;
        sample.stop(metrics.getLlmDuration());
        int total = Math.max(0, r.inputTokens() + r.outputTokens());
        metrics.recordLlmCall(r.modelId(), duration, total > 0 ? total : 0);
        if (total > 0) {
            metrics.recordTokenUsagePerBolt(total);
            double cost =
                    (r.inputTokens() / 1000.0) * leg.getCostPer1kInput()
                            + (r.outputTokens() / 1000.0) * leg.getCostPer1kOutput();
            metrics.recordCostPerBolt(cost);
        }
        budgetTracker.recordUsage(r.inputTokens(), r.outputTokens(), leg, leg.getProviderId());
        emitLlmEnd(r.modelId(), duration, total);
    }

    private void finishStructuredSuccess(
            io.micrometer.core.instrument.Timer.Sample sample,
            long start,
            StructuredLegOutcome out,
            ModelTierProperties.LegDefinition leg) {
        long duration = System.currentTimeMillis() - start;
        StructuredParse sp = out.parse();
        int total = sp.totalTokens() > 0 ? sp.totalTokens() : (sp.in() + sp.out());
        sample.stop(metrics.getLlmDuration());
        String modelLabel = leg.getModel() != null ? leg.getModel() : "structured";
        metrics.recordLlmCall(modelLabel, duration, total);
        if (total > 0) {
            metrics.recordTokenUsagePerBolt(total);
            double cost =
                    (sp.in() / 1000.0) * leg.getCostPer1kInput()
                            + (sp.out() / 1000.0) * leg.getCostPer1kOutput();
            metrics.recordCostPerBolt(cost);
        }
        budgetTracker.recordUsage(sp.in(), sp.out(), leg, leg.getProviderId());
        emitLlmEnd(modelLabel, duration, total);
    }

    @SuppressWarnings("unchecked")
    private StructuredParse parseFromMap(Map<String, Object> response) {
        String content = extractMessageContent(response);
        int in = 0;
        int out = 0;
        int total = 0;
        try {
            if (response.get("usage") instanceof Map<?, ?> u) {
                Object pt = u.get("prompt_tokens");
                Object ct = u.get("completion_tokens");
                Object tt = u.get("total_tokens");
                if (pt instanceof Number) {
                    in = ((Number) pt).intValue();
                }
                if (ct instanceof Number) {
                    out = ((Number) ct).intValue();
                }
                if (tt instanceof Number) {
                    total = ((Number) tt).intValue();
                }
            }
        } catch (Exception ignored) {
        }
        if (total == 0) {
            total = in + out;
        }
        return new StructuredParse(content, in, out, total);
    }

    @SuppressWarnings("unchecked")
    private String extractMessageContent(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("No response from LLM API");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices in LLM API response");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new IllegalStateException("No message in LLM API response");
        }
        String content = (String) message.get("content");
        if (content == null) {
            throw new IllegalStateException("No content in LLM API response message");
        }
        return content;
    }

    private AiProviderProperties.ProviderConfig providerConfig(String providerId) {
        if (aiProviderProperties.providers() == null) {
            return null;
        }
        return aiProviderProperties.providers().get(providerId);
    }

    private static String normalizeEndpoint(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private boolean isTransientError(Throwable throwable) {
        return throwable instanceof WebClientRequestException
                || (throwable instanceof WebClientResponseException wcre
                        && wcre.getStatusCode().is5xxServerError());
    }

    private void emitLlmStart(String model) {
        String runIdStr = CorrelationIdHolder.getRunId();
        String agentName = CorrelationIdHolder.getAgentName();
        if (runIdStr != null) {
            try {
                UUID runId = UUID.fromString(runIdStr);
                eventBus.emit(runId, new WorkflowEvent.LlmCallStart(runId, java.time.Instant.now(), agentName, model));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void emitLlmEnd(String model, long durationMs, int tokensUsed) {
        String runIdStr = CorrelationIdHolder.getRunId();
        String agentName = CorrelationIdHolder.getAgentName();
        if (runIdStr != null) {
            try {
                UUID runId = UUID.fromString(runIdStr);
                eventBus.emit(runId, new WorkflowEvent.LlmCallEnd(
                        runId, java.time.Instant.now(), agentName, model, durationMs, tokensUsed));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private record LegTry(String role, ModelTierProperties.LegDefinition leg) {}

    private record AiCallResult(String content, int inputTokens, int outputTokens, String modelId) {}

    private record StructuredParse(String content, int in, int out, int totalTokens) {}

    private record StructuredLegOutcome(LlmResult result, StructuredParse parse) {}
}
