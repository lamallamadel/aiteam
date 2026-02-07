package com.atlasia.ai.service.exception;

public abstract class OrchestratorException extends RuntimeException {
    private final RecoveryStrategy recoveryStrategy;
    private final boolean retryable;
    private final String errorCode;
    
    protected OrchestratorException(String message, String errorCode, RecoveryStrategy recoveryStrategy, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.recoveryStrategy = recoveryStrategy;
        this.retryable = retryable;
    }
    
    protected OrchestratorException(String message, Throwable cause, String errorCode, RecoveryStrategy recoveryStrategy, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.recoveryStrategy = recoveryStrategy;
        this.retryable = retryable;
    }
    
    public RecoveryStrategy getRecoveryStrategy() {
        return recoveryStrategy;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public enum RecoveryStrategy {
        RETRY_WITH_BACKOFF("Retry the operation with exponential backoff"),
        RETRY_IMMEDIATE("Retry the operation immediately"),
        ESCALATE_TO_HUMAN("Escalate to human for manual intervention"),
        FALLBACK_TO_DEFAULT("Use fallback/default behavior"),
        SKIP_AND_CONTINUE("Skip this step and continue workflow"),
        FAIL_FAST("Fail immediately without retry");
        
        private final String description;
        
        RecoveryStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}
