package com.atlasia.ai.service.exception;

public class LlmServiceException extends OrchestratorException {
    private final String llmModel;
    private final LlmErrorType errorType;
    
    public LlmServiceException(String message, String llmModel, LlmErrorType errorType) {
        super(message, "LLM_SERVICE_ERROR", determineRecoveryStrategy(errorType), errorType.isRetryable());
        this.llmModel = llmModel;
        this.errorType = errorType;
    }
    
    public LlmServiceException(String message, Throwable cause, String llmModel, LlmErrorType errorType) {
        super(message, cause, "LLM_SERVICE_ERROR", determineRecoveryStrategy(errorType), errorType.isRetryable());
        this.llmModel = llmModel;
        this.errorType = errorType;
    }
    
    public String getLlmModel() {
        return llmModel;
    }
    
    public LlmErrorType getErrorType() {
        return errorType;
    }
    
    private static RecoveryStrategy determineRecoveryStrategy(LlmErrorType errorType) {
        return switch (errorType) {
            case RATE_LIMIT -> RecoveryStrategy.RETRY_WITH_BACKOFF;
            case TIMEOUT -> RecoveryStrategy.RETRY_WITH_BACKOFF;
            case INVALID_RESPONSE -> RecoveryStrategy.RETRY_IMMEDIATE;
            case CONTEXT_LENGTH_EXCEEDED -> RecoveryStrategy.FALLBACK_TO_DEFAULT;
            case NETWORK_ERROR -> RecoveryStrategy.RETRY_WITH_BACKOFF;
            case AUTHENTICATION_ERROR -> RecoveryStrategy.FAIL_FAST;
        };
    }
    
    public enum LlmErrorType {
        RATE_LIMIT(true),
        TIMEOUT(true),
        INVALID_RESPONSE(true),
        CONTEXT_LENGTH_EXCEEDED(false),
        NETWORK_ERROR(true),
        AUTHENTICATION_ERROR(false);
        
        private final boolean retryable;
        
        LlmErrorType(boolean retryable) {
            this.retryable = retryable;
        }
        
        public boolean isRetryable() {
            return retryable;
        }
    }
}
