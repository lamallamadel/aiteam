package com.atlasia.ai.controller;

import com.atlasia.ai.service.CollaborationService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;

@Controller
public class CollaborationController {

    private final CollaborationService collaborationService;

    public CollaborationController(CollaborationService collaborationService) {
        this.collaborationService = collaborationService;
    }

    @MessageMapping("/runs/{runId}/graft")
    public void handleGraft(
            @DestinationVariable UUID runId,
            @Payload Map<String, Object> graftData,
            SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        collaborationService.handleGraftMutation(runId, userId, graftData);
    }

    @MessageMapping("/runs/{runId}/prune")
    public void handlePrune(
            @DestinationVariable UUID runId,
            @Payload Map<String, Object> pruneData,
            SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        collaborationService.handlePruneMutation(runId, userId, pruneData);
    }

    @MessageMapping("/runs/{runId}/flag")
    public void handleFlag(
            @DestinationVariable UUID runId,
            @Payload Map<String, Object> flagData,
            SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        collaborationService.handleFlagMutation(runId, userId, flagData);
    }

    @MessageMapping("/runs/{runId}/join")
    public void handleJoin(
            @DestinationVariable UUID runId,
            SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        collaborationService.handleUserJoin(runId, userId);
    }

    @MessageMapping("/runs/{runId}/leave")
    public void handleLeave(
            @DestinationVariable UUID runId,
            SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        collaborationService.handleUserLeave(runId, userId);
    }

    @MessageMapping("/runs/{runId}/cursor")
    public void handleCursor(
            @DestinationVariable UUID runId,
            @Payload Map<String, String> cursorData,
            SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        String nodeId = cursorData.get("nodeId");
        collaborationService.handleCursorMove(runId, userId, nodeId);
    }

    private String extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        // Extract from session attributes or headers
        // In production, this would come from authenticated session
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs != null && sessionAttrs.containsKey("userId")) {
            return (String) sessionAttrs.get("userId");
        }
        
        // Fallback to session ID
        return headerAccessor.getSessionId();
    }
}
