package com.atlasia.ai.service.trace;

import com.atlasia.ai.model.TraceEventEntity;
import com.atlasia.ai.persistence.TraceEventRepository;
import com.atlasia.ai.service.event.WorkflowEvent;
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
            if (event instanceof WorkflowEvent.StepStart e) {
                handleStepStart(e);
            } else if (event instanceof WorkflowEvent.StepComplete e) {
                handleStepComplete(e);
            } else if (event instanceof WorkflowEvent.LlmCallStart e) {
                handleLlmCallStart(e);
            } else if (event instanceof WorkflowEvent.LlmCallEnd e) {
                handleLlmCallEnd(e);
            } else if (event instanceof WorkflowEvent.SchemaValidation e) {
                handleSchemaValidation(e);
            } else if (event instanceof WorkflowEvent.WorkflowError e) {
                handleError(e);
            } else if (event instanceof WorkflowEvent.EscalationRaised e) {
                handleEscalation(e);
            } else if (event instanceof WorkflowEvent.WorkflowStatusUpdate e) {
                handleStatusUpdate(e);
            } else if (event instanceof WorkflowEvent.GraftStart e) {
                handleGraftStart(e);
            } else if (event instanceof WorkflowEvent.GraftComplete e) {
                handleGraftComplete(e);
            } else if (event instanceof WorkflowEvent.GraftFailed e) {
                handleGraftFailed(e);
            }
            // ToolCall events are not persisted as trace spans
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

    private void handleGraftStart(WorkflowEvent.GraftStart event) {
        UUID parentSpanId = currentStepSpan.get(event.runId());
        UUID spanId = UUID.randomUUID();
        TraceEventEntity span = new TraceEventEntity(
                spanId, event.runId(), parentSpanId, "GRAFT",
                event.agentName(), "Graft " + event.graftId() + " — " + event.agentName() + " (after " + event.checkpointAfter() + ")",
                event.timestamp());
        traceEventRepository.save(span);
    }

    private void handleGraftComplete(WorkflowEvent.GraftComplete event) {
        traceEventRepository.findByRunIdOrderByStartTimeAsc(event.runId()).stream()
                .filter(e -> "GRAFT".equals(e.getEventType()) && 
                             e.getLabel().contains(event.graftId()) && 
                             e.getEndTime() == null)
                .reduce((a, b) -> b)
                .ifPresent(span -> {
                    span.setEndTime(event.timestamp());
                    span.setDurationMs(event.durationMs());
                    span.setMetadata(String.format("{\"graftId\":\"%s\",\"artifactId\":\"%s\"}",
                            event.graftId(), event.artifactId()));
                    traceEventRepository.save(span);
                });
    }

    private void handleGraftFailed(WorkflowEvent.GraftFailed event) {
        traceEventRepository.findByRunIdOrderByStartTimeAsc(event.runId()).stream()
                .filter(e -> "GRAFT".equals(e.getEventType()) && 
                             e.getLabel().contains(event.graftId()) && 
                             e.getEndTime() == null)
                .reduce((a, b) -> b)
                .ifPresent(span -> {
                    span.setEndTime(event.timestamp());
                    span.setDurationMs(0L);
                    span.setMetadata(String.format("{\"graftId\":\"%s\",\"errorType\":\"%s\",\"message\":\"%s\"}",
                            event.graftId(), event.errorType(), event.message().replace("\"", "\\\"")));
                    traceEventRepository.save(span);
                });
    }

    public void cleanup(UUID runId) {
        currentStepSpan.remove(runId);
    }
}
