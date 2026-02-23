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

    public WebSocketAuthInterceptor(WebSocketConnectionMonitor connectionMonitor) {
        this.connectionMonitor = connectionMonitor;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            String sessionId = accessor.getSessionId();
            
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String userId = accessor.getFirstNativeHeader("X-User-Id");
                if (userId == null || userId.isEmpty()) {
                    userId = sessionId;
                }
                
                Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                if (sessionAttrs != null) {
                    sessionAttrs.put("userId", userId);
                }
                
                String destination = accessor.getDestination();
                if (destination != null) {
                    UUID runId = extractRunId(destination);
                    if (runId != null) {
                        sessionAttrs.put("runId", runId);
                        connectionMonitor.recordConnection(runId, sessionId, userId);
                    }
                }
            } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                if (sessionAttrs != null) {
                    UUID runId = (UUID) sessionAttrs.get("runId");
                    if (runId != null && sessionId != null) {
                        connectionMonitor.recordDisconnection(runId, sessionId);
                    }
                }
            } else if (StompCommand.SEND.equals(accessor.getCommand())) {
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
    
    private UUID extractRunId(String destination) {
        if (destination == null) return null;
        Matcher matcher = RUN_ID_PATTERN.matcher(destination);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group(1));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
