package com.atlasia.ai.service;

import com.atlasia.ai.service.exception.OrchestratorException;

public class EscalationException extends Exception {
    private final String escalationJson;

    public EscalationException(String escalationJson) {
        super("Workflow escalated");
        this.escalationJson = escalationJson;
    }

    public String getEscalationJson() {
        return escalationJson;
    }
}
