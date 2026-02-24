package com.atlasia.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class OAuth2LinkRequest {
    
    @NotBlank(message = "Provider is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Provider must contain only letters, numbers, underscores, and hyphens")
    @Size(max = 50, message = "Provider must not exceed 50 characters")
    private String provider;
    
    @NotBlank(message = "Provider user ID is required")
    @Size(max = 255, message = "Provider user ID must not exceed 255 characters")
    private String providerUserId;
    
    @Size(max = 2000, message = "Access token must not exceed 2000 characters")
    private String accessToken;
    
    @Size(max = 2000, message = "Refresh token must not exceed 2000 characters")
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
