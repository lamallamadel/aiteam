package com.atlasia.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

public class OAuth2LinkRequest {
    
    @NotBlank(message = "Provider is required")
    private String provider;
    
    @NotBlank(message = "Provider user ID is required")
    private String providerUserId;
    
    private String accessToken;
    private String refreshToken;

    public OAuth2LinkRequest() {}

    public OAuth2LinkRequest(String provider, String providerUserId, String accessToken, String refreshToken) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderUserId() { return providerUserId; }
    public void setProviderUserId(String providerUserId) { this.providerUserId = providerUserId; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
