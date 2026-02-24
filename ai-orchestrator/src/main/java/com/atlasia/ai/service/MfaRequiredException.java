package com.atlasia.ai.service;

import java.util.UUID;

public class MfaRequiredException extends RuntimeException {
    private final UUID userId;
    private final String username;

    public MfaRequiredException(UUID userId, String username) {
        super("MFA verification required");
        this.userId = userId;
        this.username = username;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }
}
