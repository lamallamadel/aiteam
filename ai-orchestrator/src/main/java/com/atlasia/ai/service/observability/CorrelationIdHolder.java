package com.atlasia.ai.service.observability;

import org.slf4j.MDC;

import java.util.UUID;

public class CorrelationIdHolder {
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String RUN_ID_KEY = "runId";
    private static final String AGENT_NAME_KEY = "agentName";
    
    private CorrelationIdHolder() {
    }
    
    public static void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID_KEY, correlationId);
    }
    
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }
    
    public static void setRunId(UUID runId) {
        if (runId != null) {
            MDC.put(RUN_ID_KEY, runId.toString());
        }
    }
    
    public static String getRunId() {
        return MDC.get(RUN_ID_KEY);
    }
    
    public static void setAgentName(String agentName) {
        if (agentName != null) {
            MDC.put(AGENT_NAME_KEY, agentName);
        }
    }
    
    public static String getAgentName() {
        return MDC.get(AGENT_NAME_KEY);
    }
    
    public static void clear() {
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(RUN_ID_KEY);
        MDC.remove(AGENT_NAME_KEY);
    }
    
    public static void clearAll() {
        MDC.clear();
    }
    
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
