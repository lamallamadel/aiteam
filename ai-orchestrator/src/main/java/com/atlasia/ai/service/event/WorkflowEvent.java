package com.atlasia.ai.service.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface WorkflowEvent permits
        WorkflowEvent.StepStart,
        WorkflowEvent.StepComplete,
        WorkflowEvent.ToolCallStart,
        WorkflowEvent.ToolCallEnd,
        WorkflowEvent.WorkflowStatusUpdate,
        WorkflowEvent.LlmCallStart,
        WorkflowEvent.LlmCallEnd,
        WorkflowEvent.SchemaValidation,
        WorkflowEvent.WorkflowError,
        WorkflowEvent.EscalationRaised {

    UUID runId();

    Instant timestamp();

    String eventType();

    record StepStart(UUID runId, Instant timestamp, String agentName, String stepPhase) implements WorkflowEvent {
        @Override
        public String eventType() { return "STEP_START"; }
    }

    record StepComplete(UUID runId, Instant timestamp, String agentName, long durationMs,
            String artifactType) implements WorkflowEvent {
        @Override
        public String eventType() { return "STEP_COMPLETE"; }
    }

    record ToolCallStart(UUID runId, Instant timestamp, String agentName, String toolName,
            String description) implements WorkflowEvent {
        @Override
        public String eventType() { return "TOOL_CALL_START"; }
    }

    record ToolCallEnd(UUID runId, Instant timestamp, String agentName, String toolName,
            long durationMs) implements WorkflowEvent {
        @Override
        public String eventType() { return "TOOL_CALL_END"; }
    }

    record WorkflowStatusUpdate(UUID runId, Instant timestamp, String status, String currentAgent,
            double progressPercent) implements WorkflowEvent {
        @Override
        public String eventType() { return "WORKFLOW_STATUS"; }
    }

    record LlmCallStart(UUID runId, Instant timestamp, String agentName,
            String model) implements WorkflowEvent {
        @Override
        public String eventType() { return "LLM_CALL_START"; }
    }

    record LlmCallEnd(UUID runId, Instant timestamp, String agentName, String model,
            long durationMs, int tokensUsed) implements WorkflowEvent {
        @Override
        public String eventType() { return "LLM_CALL_END"; }
    }

    record SchemaValidation(UUID runId, Instant timestamp, String agentName, String schemaName,
            boolean passed) implements WorkflowEvent {
        @Override
        public String eventType() { return "SCHEMA_VALIDATION"; }
    }

    record WorkflowError(UUID runId, Instant timestamp, String agentName, String errorType,
            String message) implements WorkflowEvent {
        @Override
        public String eventType() { return "WORKFLOW_ERROR"; }
    }

    record EscalationRaised(UUID runId, Instant timestamp, String agentName,
            String reason) implements WorkflowEvent {
        @Override
        public String eventType() { return "ESCALATION_RAISED"; }
    }
}
