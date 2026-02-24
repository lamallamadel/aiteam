package com.atlasia.ai.controller;

import com.atlasia.ai.model.*;
import com.atlasia.ai.service.CollaborationService;
import com.atlasia.ai.service.TimeTravelService;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@Validated
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final TimeTravelService timeTravelService;
    private final OrchestratorMetrics metrics;

    public CollaborationController(CollaborationService collaborationService,
                                  TimeTravelService timeTravelService,
                                  OrchestratorMetrics metrics) {
        this.collaborationService = collaborationService;
        this.timeTravelService = timeTravelService;
        this.metrics = metrics;
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

    @MessageMapping("/runs/{runId}/ping")
    public void handlePing(
            @DestinationVariable UUID runId,
            @Payload Map<String, Object> pingData,
            SimpMessageHeaderAccessor headerAccessor) {
        String userId = extractUserId(headerAccessor);
        String sessionId = headerAccessor.getSessionId();
        
        Long clientTimestamp = pingData.get("timestamp") != null ? 
            ((Number) pingData.get("timestamp")).longValue() : System.currentTimeMillis();
        
        collaborationService.handlePing(runId, userId, sessionId, clientTimestamp);
    }

    private String extractUserId(SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs != null && sessionAttrs.containsKey("userId")) {
            return (String) sessionAttrs.get("userId");
        }
        return headerAccessor.getSessionId();
    }

    @GetMapping("/api/runs/{runId}/collaboration/poll")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pollCollaborationEvents(
            @PathVariable UUID runId,
            @RequestParam(required = false) Long afterSequence) {
        
        metrics.recordWebSocketFallbackToHttp();
        
        List<PersistedCollaborationMessage> messages = 
            collaborationService.getPersistedMessages(runId, afterSequence);
        
        List<Map<String, Object>> eventsList = messages.stream()
            .map(msg -> {
                Map<String, Object> event = new HashMap<>();
                event.put("eventType", msg.getEventType());
                event.put("userId", msg.getUserId());
                event.put("timestamp", msg.getTimestamp().toEpochMilli());
                event.put("sequenceNumber", msg.getSequenceNumber());
                event.put("data", msg.getMessageData());
                return event;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("runId", runId);
        response.put("events", eventsList);
        response.put("activeUsers", new ArrayList<>(collaborationService.getActiveUsers(runId)));
        response.put("cursorPositions", collaborationService.getCursorPositions(runId));
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/api/runs/{runId}/collaboration/replay")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> replayMessages(
            @PathVariable UUID runId,
            @RequestParam(required = false) Long fromSequence,
            @RequestParam(defaultValue = "100") int limit) {
        
        List<PersistedCollaborationMessage> messages;
        if (fromSequence != null) {
            messages = collaborationService.getPersistedMessages(runId, fromSequence);
        } else {
            messages = collaborationService.getCriticalMessages(runId);
        }
        
        List<Map<String, Object>> eventsList = messages.stream()
            .limit(limit)
            .map(msg -> {
                Map<String, Object> event = new HashMap<>();
                event.put("eventType", msg.getEventType());
                event.put("userId", msg.getUserId());
                event.put("timestamp", msg.getTimestamp().toEpochMilli());
                event.put("sequenceNumber", msg.getSequenceNumber());
                event.put("data", msg.getMessageData());
                event.put("isCritical", msg.getIsCritical());
                return event;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("runId", runId);
        response.put("events", eventsList);
        response.put("totalEvents", eventsList.size());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/api/runs/{runId}/collaboration/history")
    @ResponseBody
    public ResponseEntity<List<TimeTravelSnapshot>> getEventHistory(
            @PathVariable UUID runId,
            @RequestParam(required = false) Long startTimestamp,
            @RequestParam(required = false) Long endTimestamp) {
        
        List<TimeTravelSnapshot> history;
        
        if (startTimestamp != null && endTimestamp != null) {
            Instant start = Instant.ofEpochMilli(startTimestamp);
            Instant end = Instant.ofEpochMilli(endTimestamp);
            history = timeTravelService.getEventHistoryInRange(runId, start, end);
        } else {
            history = timeTravelService.getEventHistory(runId);
        }
        
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/api/runs/{runId}/collaboration/analytics")
    @ResponseBody
    public ResponseEntity<CollaborationAnalytics> getAnalytics(@PathVariable UUID runId) {
        CollaborationAnalytics analytics = timeTravelService.getAnalytics(runId);
        return ResponseEntity.ok(analytics);
    }
    
    @GetMapping("/api/runs/{runId}/collaboration/export/json")
    @ResponseBody
    public ResponseEntity<String> exportAsJson(@PathVariable UUID runId) {
        String json = timeTravelService.exportEventsAsJson(runId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "collaboration-" + runId + ".json");
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(json);
    }
    
    @GetMapping("/api/runs/{runId}/collaboration/export/csv")
    @ResponseBody
    public ResponseEntity<String> exportAsCsv(@PathVariable UUID runId) {
        String csv = timeTravelService.exportEventsAsCsv(runId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "collaboration-" + runId + ".csv");
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv);
    }
}
