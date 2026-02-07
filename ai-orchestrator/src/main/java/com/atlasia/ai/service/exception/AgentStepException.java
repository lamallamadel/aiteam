package com.atlasia.ai.service.exception;

public class AgentStepException extends OrchestratorException {
    private final String agentName;
    private final String stepPhase;
    
    public AgentStepException(String message, String agentName, String stepPhase, RecoveryStrategy recoveryStrategy) {
        super(message, "AGENT_STEP_ERROR", recoveryStrategy, false);
        this.agentName = agentName;
        this.stepPhase = stepPhase;
    }
    
    public AgentStepException(String message, Throwable cause, String agentName, String stepPhase, RecoveryStrategy recoveryStrategy) {
        super(message, cause, "AGENT_STEP_ERROR", recoveryStrategy, false);
        this.agentName = agentName;
        this.stepPhase = stepPhase;
    }
    
    public String getAgentName() {
        return agentName;
    }
    
    public String getStepPhase() {
        return stepPhase;
    }
}
