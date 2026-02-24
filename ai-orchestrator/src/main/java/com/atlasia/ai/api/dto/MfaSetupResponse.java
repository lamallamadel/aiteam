package com.atlasia.ai.api.dto;

public class MfaSetupResponse {
    private String secret;
    private String otpAuthUrl;
    private String qrCodeDataUri;

    public MfaSetupResponse() {}

    public MfaSetupResponse(String secret, String otpAuthUrl, String qrCodeDataUri) {
        this.secret = secret;
        this.otpAuthUrl = otpAuthUrl;
        this.qrCodeDataUri = qrCodeDataUri;
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public String getOtpAuthUrl() { return otpAuthUrl; }
    public void setOtpAuthUrl(String otpAuthUrl) { this.otpAuthUrl = otpAuthUrl; }

    public String getQrCodeDataUri() { return qrCodeDataUri; }
    public void setQrCodeDataUri(String qrCodeDataUri) { this.qrCodeDataUri = qrCodeDataUri; }
}
