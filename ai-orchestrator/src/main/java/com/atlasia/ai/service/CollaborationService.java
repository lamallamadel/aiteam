package com.atlasia.ai.service;

import com.atlasia.ai.model.CollaborationEventEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.CollaborationEventRepository;
import com.atlasia.ai.persistence.RunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CollaborationService {

    private final CollaborationEventRepository eventRepository;
    private final RunRepository runRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    // Track active users per run for presence
    private final Map<UUID, Set<String>> activeUsers = new ConcurrentHashMap<>();
    
    // Track cursor positions: runId -> userId -> nodeId
    private final Map<UUID, Map<String, String>> cursorPositions = new ConcurrentHashMap<>();

    public CollaborationService(
            CollaborationEventRepository eventRepository,
            RunRepository runRepository,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.runRepository = runRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void handleGraftMutation(UUID runId, String userId, Map<String, Object> graftData) {
        String eventData = serializeEventData(graftData);
        CollaborationEventEntity event = new CollaborationEventEntity(
                runId, userId, "GRAFT", eventData, Instant.now());
        eventRepository.save(event);

        // Apply operational transformation and update run entity
        applyGraftToRun(runId, graftData);

        // Broadcast to all connected clients
        broadcastEvent(runId, "GRAFT", userId, graftData);
    }

    @Transactional
    public void handlePruneMutation(UUID runId, String userId, Map<String, Object> pruneData) {
        String eventData = serializeEventData(pruneData);
        CollaborationEventEntity event = new CollaborationEventEntity(
                runId, userId, "PRUNE", eventData, Instant.now());
        eventRepository.save(event);

        // Apply operational transformation and update run entity
        applyPruneToRun(runId, pruneData);

        // Broadcast to all connected clients
        broadcastEvent(runId, "PRUNE", userId, pruneData);
    }

    @Transactional
    public void handleFlagMutation(UUID runId, String userId, Map<String, Object> flagData) {
        String eventData = serializeEventData(flagData);
        CollaborationEventEntity event = new CollaborationEventEntity(
                runId, userId, "FLAG", eventData, Instant.now());
        eventRepository.save(event);

        // Broadcast to all connected clients
        broadcastEvent(runId, "FLAG", userId, flagData);
    }

    public void handleUserJoin(UUID runId, String userId) {
        activeUsers.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        cursorPositions.computeIfAbsent(runId, k -> new ConcurrentHashMap<>());

        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("activeUsers", new ArrayList<>(activeUsers.get(runId)));
        
        broadcastEvent(runId, "USER_JOIN", userId, presenceData);
    }

    public void handleUserLeave(UUID runId, String userId) {
        Set<String> users = activeUsers.get(runId);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                activeUsers.remove(runId);
                cursorPositions.remove(runId);
            }
        }

        Map<String, String> cursors = cursorPositions.get(runId);
        if (cursors != null) {
            cursors.remove(userId);
        }

        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("activeUsers", users != null ? new ArrayList<>(users) : Collections.emptyList());
        
        broadcastEvent(runId, "USER_LEAVE", userId, presenceData);
    }

    public void handleCursorMove(UUID runId, String userId, String nodeId) {
        Map<String, String> cursors = cursorPositions.computeIfAbsent(runId, k -> new ConcurrentHashMap<>());
        cursors.put(userId, nodeId);

        Map<String, Object> cursorData = new HashMap<>();
        cursorData.put("nodeId", nodeId);
        cursorData.put("cursors", new HashMap<>(cursors));

        broadcastEvent(runId, "CURSOR_MOVE", userId, cursorData);
    }

    public List<CollaborationEventEntity> getRecentEvents(UUID runId, int limit) {
        return eventRepository.findTop100ByRunIdOrderByTimestampDesc(runId)
                .stream()
                .limit(limit)
                .toList();
    }

    public Set<String> getActiveUsers(UUID runId) {
        return activeUsers.getOrDefault(runId, Collections.emptySet());
    }

    public Map<String, String> getCursorPositions(UUID runId) {
        return cursorPositions.getOrDefault(runId, Collections.emptyMap());
    }

    private void broadcastEvent(UUID runId, String eventType, String userId, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", eventType);
        message.put("userId", userId);
        message.put("timestamp", Instant.now().toEpochMilli());
        message.put("data", data);

        messagingTemplate.convertAndSend("/topic/runs/" + runId + "/collaboration", message);
    }

    private void applyGraftToRun(UUID runId, Map<String, Object> graftData) {
        runRepository.findById(runId).ifPresent(run -> {
            String after = (String) graftData.get("after");
            String agentName = (String) graftData.get("agentName");
            
            // Apply operational transformation for concurrent grafts
            String existing = run.getPendingGrafts();
            String entry = String.format("{\"after\":\"%s\",\"agentName\":\"%s\"}", after, agentName);
            
            String updated;
            if (existing == null || existing.isBlank() || existing.equals("[]")) {
                updated = "[" + entry + "]";
            } else {
                // Check for conflicts and resolve with timestamp-based ordering
                updated = mergeGrafts(existing, entry, graftData);
            }
            
            run.setPendingGrafts(updated);
            runRepository.save(run);
        });
    }

    private void applyPruneToRun(UUID runId, Map<String, Object> pruneData) {
        runRepository.findById(runId).ifPresent(run -> {
            String stepId = (String) pruneData.get("stepId");
            Boolean isPruned = (Boolean) pruneData.getOrDefault("isPruned", true);
            
            // Apply operational transformation for concurrent prunes
            String existing = run.getPrunedSteps();
            Set<String> prunedSet = new HashSet<>();
            
            if (existing != null && !existing.isBlank()) {
                prunedSet.addAll(Arrays.asList(existing.split(",")));
            }
            
            if (isPruned) {
                prunedSet.add(stepId);
            } else {
                prunedSet.remove(stepId);
            }
            
            String updated = String.join(",", prunedSet);
            run.setPrunedSteps(updated);
            runRepository.save(run);
        });
    }

    private String mergeGrafts(String existing, String newEntry, Map<String, Object> graftData) {
        // Simple operational transformation: append if no conflict at same position
        // In production, this would use vector clocks or CRDTs
        try {
            List<Map<String, String>> existingGrafts = objectMapper.readValue(
                    existing, objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            
            String after = (String) graftData.get("after");
            
            // Check if there's already a graft at this position
            boolean hasConflict = existingGrafts.stream()
                    .anyMatch(g -> g.get("after").equals(after));
            
            if (hasConflict) {
                // Resolve conflict by inserting after the last graft at this position
                // This is a simple LWW (Last Write Wins) strategy
                existingGrafts.removeIf(g -> g.get("after").equals(after));
            }
            
            Map<String, String> newGraft = new HashMap<>();
            newGraft.put("after", after);
            newGraft.put("agentName", (String) graftData.get("agentName"));
            existingGrafts.add(newGraft);
            
            return objectMapper.writeValueAsString(existingGrafts);
        } catch (JsonProcessingException e) {
            // Fallback: simple append
            return existing.substring(0, existing.lastIndexOf(']')) + "," + newEntry + "]";
        }
    }

    private String serializeEventData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
