package com.atlasia.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "atlasia.oauth2")
public class OAuth2Properties {
    
    private Map<String, ClientRegistration> clients = new HashMap<>();
    
    public Map<String, ClientRegistration> getClients() {
        return clients;
    }
    
    public void setClients(Map<String, ClientRegistration> clients) {
        this.clients = clients;
    }
    
    public static class ClientRegistration {
        private String clientId;
        private String clientSecret;
        
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }
}
