package com.atlasia.ai.service;

import com.atlasia.ai.config.ModelTierProperties;
import com.atlasia.ai.model.TaskComplexity;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks estimated LLM spend per workflow run and per UTC day; downgrades tier when soft caps are hit.
 */
@Service
public class BudgetTracker {

    private final ModelTierProperties modelTierProperties;
    private final OrchestratorMetrics metrics;

    private final ConcurrentHashMap<UUID, Double> spendByRunUsd = new ConcurrentHashMap<>();
    private final AtomicReference<LocalDate> utcDay = new AtomicReference<>(LocalDate.now(ZoneOffset.UTC));
    private final AtomicReference<Double> spendDayUsd = new AtomicReference<>(0.0);

    public BudgetTracker(ModelTierProperties modelTierProperties, OrchestratorMetrics metrics) {
        this.modelTierProperties = modelTierProperties;
        this.metrics = metrics;
    }

    public TaskComplexity adjustForBudget(TaskComplexity requested) {
        rotateDayIfNeeded();
        ModelTierProperties.Budget b = modelTierProperties.getBudget();
        double maxRun = b.getMaxPerRunUsd();
        double maxDay = b.getMaxDailyUsd();
        double thr = b.getDowngradeThreshold();
        if (thr <= 0 || thr > 1) {
            thr = 0.80;
        }

        TaskComplexity c = requested;
        while (c != TaskComplexity.TRIVIAL) {
            UUID runId = parseRunId();
            double runSpent = runId != null ? spendByRunUsd.getOrDefault(runId, 0.0) : 0.0;
            double daySpent = spendDayUsd.get();

            boolean runPressure = runId != null && maxRun > 0 && runSpent >= maxRun * thr;
            boolean dayPressure = maxDay > 0 && daySpent >= maxDay * thr;
            if (runPressure || dayPressure) {
                c = c.downgrade();
            } else {
                break;
            }
        }
        return c;
    }

    public void recordUsage(
            int inputTokens,
            int outputTokens,
            ModelTierProperties.LegDefinition leg,
            String providerId) {
        rotateDayIfNeeded();
        double costUsd = 0.0;
        if (leg != null && (inputTokens > 0 || outputTokens > 0)) {
            costUsd =
                    (inputTokens / 1000.0) * leg.getCostPer1kInput()
                            + (outputTokens / 1000.0) * leg.getCostPer1kOutput();
        }
        if (costUsd <= 0) {
            return;
        }

        final double addUsd = costUsd;
        UUID runId = parseRunId();
        if (runId != null) {
            spendByRunUsd.merge(runId, addUsd, Double::sum);
        }
        spendDayUsd.updateAndGet(v -> v + addUsd);

        String repoRaw = CorrelationIdHolder.getRepository();
        String repo = repoRaw != null ? repoRaw : "unknown";
        String userId = CorrelationIdHolder.getUserId();
        metrics.recordCostAttribution(repo, userId != null ? userId : "unknown", addUsd);
    }

    /** Snapshot for {@code GET /api/budget} (MDC {@code runId} when no override). */
    public BudgetSnapshot snapshot() {
        return snapshot(null);
    }

    /**
     * @param runIdOverride optional run UUID (e.g. {@code ?runId=} from the dashboard); otherwise MDC {@code runId}.
     */
    public BudgetSnapshot snapshot(UUID runIdOverride) {
        rotateDayIfNeeded();
        UUID runId = runIdOverride != null ? runIdOverride : parseRunId();
        ModelTierProperties.Budget b = modelTierProperties.getBudget();
        return new BudgetSnapshot(
                runId,
                runId != null ? spendByRunUsd.getOrDefault(runId, 0.0) : 0.0,
                b.getMaxPerRunUsd(),
                spendDayUsd.get(),
                b.getMaxDailyUsd(),
                b.getDowngradeThreshold(),
                Map.copyOf(modelTierProperties.getAgentComplexity()));
    }

    private void rotateDayIfNeeded() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate prev = utcDay.get();
        if (!today.equals(prev) && utcDay.compareAndSet(prev, today)) {
            spendDayUsd.set(0.0);
        }
    }

    private static UUID parseRunId() {
        String s = CorrelationIdHolder.getRunId();
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record BudgetSnapshot(
            UUID currentRunId,
            double spentRunUsd,
            double maxRunUsd,
            double spentDayUsd,
            double maxDayUsd,
            double downgradeThreshold,
            Map<String, String> agentComplexityKeys) {}
}
