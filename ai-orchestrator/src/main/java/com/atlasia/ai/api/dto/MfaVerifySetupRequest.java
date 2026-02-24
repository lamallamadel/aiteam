package com.atlasia.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MfaVerifySetupRequest {
    
    @NotBlank(message = "Secret is required")
    private String secret;
    
    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Code must be exactly 6 digits")
    private String code;

    public MfaVerifySetupRequest() {}

    public MfaVerifySetupRequest(String secret, String code) {
        this.secret = secret;
        this.code = code;
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
