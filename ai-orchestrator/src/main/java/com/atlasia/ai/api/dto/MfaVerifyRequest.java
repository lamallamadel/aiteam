package com.atlasia.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class MfaVerifyRequest {
    
    @NotBlank(message = "MFA token is required")
    private String mfaToken;
    
    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Code must be exactly 6 digits")
    private String code;

    public MfaVerifyRequest() {}

    public MfaVerifyRequest(String mfaToken, String code) {
        this.mfaToken = mfaToken;
        this.code = code;
    }

    public String getMfaToken() { return mfaToken; }
    public void setMfaToken(String mfaToken) { this.mfaToken = mfaToken; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
