package com.atlasia.ai.service.trace;

import com.atlasia.ai.model.TraceEventEntity;
import com.atlasia.ai.persistence.TraceEventRepository;
import com.atlasia.ai.service.event.WorkflowEvent;
import com.atlasia.ai.service.event.WorkflowEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to WorkflowEventBus events and persists trace spans
 * with parent stack tracking per run.
 */
@Service
public class TraceEventService {

    private static final Logger log = LoggerFactory.getLogger(TraceEventService.class);

    private final TraceEventRepository traceEventRepository;

    // Tracks the current parent span per run for nesting
    private final ConcurrentHashMap<UUID, UUID> currentStepSpan = new ConcurrentHashMap<>();

    public TraceEventService(TraceEventRepository traceEventRepository) {
        this.traceEventRepository = traceEventRepository;
    }

    /**
     * Called by WorkflowEngine/LlmService to record trace spans.
     * Translates WorkflowEvents into persisted trace spans.
     */
    public void recordEvent(WorkflowEvent event) {
        try {
            switch (event) {
                case WorkflowEvent.StepStart e -> handleStepStart(e);
                case WorkflowEvent.StepComplete e -> handleStepComplete(e);
                case WorkflowEvent.LlmCallStart e -> handleLlmCallStart(e);
                case WorkflowEvent.LlmCallEnd e -> handleLlmCallEnd(e);
                case WorkflowEvent.SchemaValidation e -> handleSchemaValidation(e);
                case WorkflowEvent.WorkflowError e -> handleError(e);
                case WorkflowEvent.EscalationRaised e -> handleEscalation(e);
                case WorkflowEvent.WorkflowStatusUpdate e -> handleStatusUpdate(e);
                default -> { /* ToolCall events are not persisted as trace spans */ }
            }
        } catch (Exception ex) {
            log.warn("Failed to record trace event for run {}: {}", event.runId(), ex.getMessage());
        }
    }

    private void handleStepStart(WorkflowEvent.StepStart event) {
        UUID spanId = UUID.randomUUID();
        TraceEventEntity span = new TraceEventEntity(
                spanId, event.runId(), null, "STEP",
                event.agentName(), event.agentName() + " — " + event.stepPhase(),
                event.timestamp());
        traceEventRepository.save(span);
        currentStepSpan.put(event.runId(), spanId);
    }

    private void handleStepComplete(WorkflowEvent.StepComplete event) {
        UUID parentSpanId = currentStepSpan.get(event.runId());
        if (parentSpanId != null) {
            traceEventRepository.findById(parentSpanId).ifPresent(span -> {
                span.setEndTime(event.timestamp());
                span.setDurationMs(event.durationMs());
                traceEventRepository.save(span);
            });
        }
    }

    private void handleLlmCallStart(WorkflowEvent.LlmCallStart event) {
        UUID parentSpanId = currentStepSpan.get(event.runId());
        UUID spanId = UUID.randomUUID();
        TraceEventEntity span = new TraceEventEntity(
                spanId, event.runId(), parentSpanId, "LLM_CALL",
                event.agentName(), "LLM call — " + event.model(),
                event.timestamp());
        traceEventRepository.save(span);
    }

    private void handleLlmCallEnd(WorkflowEvent.LlmCallEnd event) {
        // Find the most recent LLM_CALL span without end_time for this run
        traceEventRepository.findByRunIdOrderByStartTimeAsc(event.runId()).stream()
                .filter(e -> "LLM_CALL".equals(e.getEventType()) && e.getEndTime() == null)
                .reduce((a, b) -> b) // get last
                .ifPresent(span -> {
                    span.setEndTime(event.timestamp());
                    span.setDurationMs(event.durationMs());
                    span.setTokensUsed(event.tokensUsed());
                    traceEventRepository.save(span);
                });
    }

    private void handleSchemaValidation(WorkflowEvent.SchemaValidation event) {
        UUID parentSpanId = currentStepSpan.get(event.runId());
        TraceEventEntity span = new TraceEventEntity(
                UUID.randomUUID(), event.runId(), parentSpanId, "SCHEMA_VALIDATION",
                event.agentName(), "Schema: " + event.schemaName() + (event.passed() ? " PASS" : " FAIL"),
                event.timestamp());
        span.setEndTime(event.timestamp());
        span.setDurationMs(0L);
        span.setMetadata(String.format("{\"schemaName\":\"%s\",\"passed\":%s}", event.schemaName(), event.passed()));
        traceEventRepository.save(span);
    }

    private void handleError(WorkflowEvent.WorkflowError event) {
        UUID parentSpanId = currentStepSpan.get(event.runId());
        TraceEventEntity span = new TraceEventEntity(
                UUID.randomUUID(), event.runId(), parentSpanId, "ERROR",
                event.agentName(), "Error: " + event.errorType(),
                event.timestamp());
        span.setEndTime(event.timestamp());
        span.setDurationMs(0L);
        span.setMetadata(String.format("{\"errorType\":\"%s\",\"message\":\"%s\"}",
                event.errorType(), event.message().replace("\"", "\\\"")));
        traceEventRepository.save(span);
    }

    private void handleEscalation(WorkflowEvent.EscalationRaised event) {
        UUID parentSpanId = currentStepSpan.get(event.runId());
        TraceEventEntity span = new TraceEventEntity(
                UUID.randomUUID(), event.runId(), parentSpanId, "ESCALATION",
                event.agentName(), "Escalation: " + event.reason(),
                event.timestamp());
        span.setEndTime(event.timestamp());
        span.setDurationMs(0L);
        traceEventRepository.save(span);
    }

    private void handleStatusUpdate(WorkflowEvent.WorkflowStatusUpdate event) {
        TraceEventEntity span = new TraceEventEntity(
                UUID.randomUUID(), event.runId(), null, "WORKFLOW_STATUS",
                event.currentAgent(), "Workflow → " + event.status(),
                event.timestamp());
        span.setEndTime(event.timestamp());
        span.setDurationMs(0L);
        span.setMetadata(String.format("{\"status\":\"%s\",\"progressPercent\":%d}",
                event.status(), event.progressPercent()));
        traceEventRepository.save(span);
    }

    public void cleanup(UUID runId) {
        currentStepSpan.remove(runId);
    }
}
