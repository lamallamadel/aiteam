package com.atlasia.ai.service.exception;

public class GitHubApiException extends OrchestratorException {
    private final int statusCode;
    private final String endpoint;
    
    public GitHubApiException(String message, String endpoint, int statusCode) {
        super(message, "GITHUB_API_ERROR", RecoveryStrategy.RETRY_WITH_BACKOFF, true);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }
    
    public GitHubApiException(String message, Throwable cause, String endpoint, int statusCode) {
        super(message, cause, "GITHUB_API_ERROR", RecoveryStrategy.RETRY_WITH_BACKOFF, true);
        this.statusCode = statusCode;
        this.endpoint = endpoint;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public boolean isRateLimitError() {
        return statusCode == 429 || statusCode == 403;
    }
    
    public boolean isServerError() {
        return statusCode >= 500;
    }
}
