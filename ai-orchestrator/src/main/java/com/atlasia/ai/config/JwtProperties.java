package com.atlasia.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "atlasia.jwt")
public class JwtProperties {
    
    private String secretKey;
    private long accessTokenExpirationMinutes = 15;
    private long refreshTokenExpirationDays = 7;
    private String issuer = "atlasia-ai-orchestrator";
    
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    
    public long getAccessTokenExpirationMinutes() { return accessTokenExpirationMinutes; }
    public void setAccessTokenExpirationMinutes(long accessTokenExpirationMinutes) { 
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes; 
    }
    
    public long getRefreshTokenExpirationDays() { return refreshTokenExpirationDays; }
    public void setRefreshTokenExpirationDays(long refreshTokenExpirationDays) { 
        this.refreshTokenExpirationDays = refreshTokenExpirationDays; 
    }
    
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
