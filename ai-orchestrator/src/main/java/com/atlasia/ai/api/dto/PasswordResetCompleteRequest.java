package com.atlasia.ai.api.dto;

import jakarta.validation.constraints.NotBlank;

public class PasswordResetCompleteRequest {
    
    @NotBlank(message = "Token is required")
    private String token;
    
    @NotBlank(message = "New password is required")
    private String newPassword;

    public PasswordResetCompleteRequest() {}

    public PasswordResetCompleteRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
