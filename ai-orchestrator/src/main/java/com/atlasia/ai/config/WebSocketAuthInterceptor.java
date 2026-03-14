package com.atlasia.ai.config;

import com.atlasia.ai.service.WebSocketConnectionMonitor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final WebSocketConnectionMonitor connectionMonitor;
    private static final Pattern RUN_ID_PATTERN = Pattern.compile("/ws/runs/([^/]+)/collaboration");
    private static final Pattern RUNS_PATH_PATTERN = Pattern.compile("/runs/([^/]+)/");

    public WebSocketAuthInterceptor(WebSocketConnectionMonitor connectionMonitor) {
        this.connectionMonitor = connectionMonitor;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            String sessionId = accessor.getSessionId();
            
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                String userId = resolveUserId(accessor, sessionId, sessionAttrs);
                if (sessionAttrs != null) {
                    sessionAttrs.put("userId", userId);
                }
                
                UUID runId = null;
                String runIdHeader = accessor.getFirstNativeHeader("X-Run-Id");
                if (runIdHeader != null && !runIdHeader.isEmpty()) {
                    try {
                        runId = UUID.fromString(runIdHeader);
                    } catch (IllegalArgumentException ignored) { }
                }
                if (runId == null) {
                    String destination = accessor.getDestination();
                    if (destination != null) {
                        runId = extractRunIdFromDestination(destination);
                    }
                }
                if (runId != null && sessionAttrs != null) {
                    sessionAttrs.put("runId", runId);
                    connectionMonitor.recordConnection(runId, sessionId, userId);
                }
            } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                if (sessionAttrs != null) {
                    UUID runId = (UUID) sessionAttrs.get("runId");
                    if (runId == null && accessor.getDestination() != null) {
                        runId = extractRunIdFromDestination(accessor.getDestination());
                    }
                    if (runId != null && sessionId != null) {
                        connectionMonitor.recordDisconnection(runId, sessionId);
                    }
                }
            } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                // Ensure userId and runId are set for SEND (session attrs may not persist from CONNECT in some setups)
                Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                if (sessionAttrs != null) {
                    resolveUserId(accessor, sessionId, sessionAttrs);
                    UUID runId = (UUID) sessionAttrs.get("runId");
                    if (runId == null && accessor.getDestination() != null) {
                        runId = extractRunIdFromDestination(accessor.getDestination());
                        if (runId != null) {
                            sessionAttrs.put("runId", runId);
                        }
                    }
                }
                if (sessionId != null) {
                    connectionMonitor.recordMessageReceived(sessionId);
                }
            }
        }
        
        return message;
    }
    
    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.MESSAGE.equals(accessor.getCommand())) {
            String sessionId = accessor.getSessionId();
            if (sessionId != null) {
                if (sent) {
                    connectionMonitor.recordMessageSent(sessionId);
                } else {
                    connectionMonitor.recordMessageFailure(sessionId);
                }
            }
        }
    }
    
    /** Resolves userId from session attrs (if already set), STOMP header, principal, or sessionId fallback.
     *  Stores the resolved value back into sessionAttrs when provided. */
    private String resolveUserId(StompHeaderAccessor accessor, String sessionId, Map<String, Object> sessionAttrs) {
        if (sessionAttrs != null) {
            String stored = (String) sessionAttrs.get("userId");
            if (stored != null && !stored.isEmpty()) return stored;
        }
        String userId = accessor.getFirstNativeHeader("X-User-Id");
        if ((userId == null || userId.isEmpty()) && accessor.getUser() != null) {
            userId = accessor.getUser().getName();
        }
        if (userId == null || userId.isEmpty()) {
            userId = sessionId;
        }
        if (sessionAttrs != null && userId != null) {
            sessionAttrs.put("userId", userId);
        }
        return userId;
    }

    private UUID extractRunIdFromDestination(String destination) {
        if (destination == null) return null;
        Matcher matcher = RUN_ID_PATTERN.matcher(destination);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        // Also match /topic/runs/{runId}/... and /app/runs/{runId}/...
        Matcher runsMatcher = RUNS_PATH_PATTERN.matcher(destination);
        if (runsMatcher.find()) {
            try {
                return UUID.fromString(runsMatcher.group(1));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
