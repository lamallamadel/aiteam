package com.atlasia.ai.api.dto;

public class MfaLoginResponse {
    private boolean mfaRequired;
    private String mfaToken;

    public MfaLoginResponse() {}

    public MfaLoginResponse(boolean mfaRequired, String mfaToken) {
        this.mfaRequired = mfaRequired;
        this.mfaToken = mfaToken;
    }

    public boolean isMfaRequired() { return mfaRequired; }
    public void setMfaRequired(boolean mfaRequired) { this.mfaRequired = mfaRequired; }

    public String getMfaToken() { return mfaToken; }
    public void setMfaToken(String mfaToken) { this.mfaToken = mfaToken; }
}
