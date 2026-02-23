package com.atlasia.ai.api.dto;

public class OAuth2LinkResponse {
    
    private String message;
    private String provider;
    private boolean success;

    public OAuth2LinkResponse() {}

    public OAuth2LinkResponse(String message, String provider, boolean success) {
        this.message = message;
        this.provider = provider;
        this.success = success;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
