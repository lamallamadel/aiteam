package com.atlasia.ai.service.exception;

import java.util.UUID;

public class WorkflowException extends OrchestratorException {
    private final UUID runId;
    private final String currentStep;
    
    public WorkflowException(String message, UUID runId, String currentStep, RecoveryStrategy recoveryStrategy) {
        super(message, "WORKFLOW_ERROR", recoveryStrategy, false);
        this.runId = runId;
        this.currentStep = currentStep;
    }
    
    public WorkflowException(String message, Throwable cause, UUID runId, String currentStep, RecoveryStrategy recoveryStrategy) {
        super(message, cause, "WORKFLOW_ERROR", recoveryStrategy, false);
        this.runId = runId;
        this.currentStep = currentStep;
    }
    
    public UUID getRunId() {
        return runId;
    }
    
    public String getCurrentStep() {
        return currentStep;
    }
}
