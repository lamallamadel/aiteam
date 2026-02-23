package com.atlasia.ai.service;

import com.atlasia.ai.model.CollaborationEventEntity;
import com.atlasia.ai.model.PersistedCollaborationMessage;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.CollaborationEventRepository;
import com.atlasia.ai.persistence.PersistedCollaborationMessageRepository;
import com.atlasia.ai.persistence.RunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CollaborationService {

    private final CollaborationEventRepository eventRepository;
    private final PersistedCollaborationMessageRepository messageRepository;
    private final RunRepository runRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final WebSocketConnectionMonitor connectionMonitor;
    
    private final Map<UUID, Set<String>> activeUsers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> cursorPositions = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> sequenceCounters = new ConcurrentHashMap<>();
    
    private static final int MAX_PERSISTED_MESSAGES_PER_RUN = 1000;
    private static final Set<String> CRITICAL_EVENT_TYPES = Set.of("GRAFT", "PRUNE", "FLAG");

    public CollaborationService(
            CollaborationEventRepository eventRepository,
            PersistedCollaborationMessageRepository messageRepository,
            RunRepository runRepository,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            WebSocketConnectionMonitor connectionMonitor) {
        this.eventRepository = eventRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.connectionMonitor = connectionMonitor;
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

    public void handlePing(UUID runId, String userId, String sessionId, Long clientTimestamp) {
        long serverTimestamp = System.currentTimeMillis();
        
        Map<String, Object> pongData = new HashMap<>();
        pongData.put("clientTimestamp", clientTimestamp);
        pongData.put("serverTimestamp", serverTimestamp);
        
        messagingTemplate.convertAndSend(
            "/topic/runs/" + runId + "/collaboration", 
            createPongMessage(userId, clientTimestamp, serverTimestamp)
        );
        
        if (clientTimestamp != null) {
            long latency = serverTimestamp - clientTimestamp;
            connectionMonitor.recordMessageLatency(sessionId, latency);
        }
    }

    private Map<String, Object> createPongMessage(String userId, Long clientTimestamp, long serverTimestamp) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", "PONG");
        message.put("userId", userId);
        message.put("timestamp", serverTimestamp);
        
        Map<String, Object> data = new HashMap<>();
        data.put("clientTimestamp", clientTimestamp);
        data.put("serverTimestamp", serverTimestamp);
        message.put("data", data);
        
        return message;
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
        Instant now = Instant.now();
        message.put("timestamp", now.toEpochMilli());
        message.put("data", data);

        long sequenceNumber = getNextSequenceNumber(runId);
        message.put("sequenceNumber", sequenceNumber);

        boolean isCritical = CRITICAL_EVENT_TYPES.contains(eventType);
        if (isCritical) {
            persistMessage(runId, userId, eventType, data, now, sequenceNumber, true);
        }

        messagingTemplate.convertAndSend("/topic/runs/" + runId + "/collaboration", message);
    }
    
    private long getNextSequenceNumber(UUID runId) {
        return sequenceCounters.computeIfAbsent(runId, k -> {
            Long maxSeq = messageRepository.findMaxSequenceNumberByRunId(runId);
            return new AtomicLong(maxSeq != null ? maxSeq : 0L);
        }).incrementAndGet();
    }
    
    private void persistMessage(UUID runId, String userId, String eventType, 
                               Map<String, Object> data, Instant timestamp, 
                               long sequenceNumber, boolean isCritical) {
        String messageData = serializeEventData(data);
        PersistedCollaborationMessage message = new PersistedCollaborationMessage(
            runId, userId, eventType, messageData, timestamp, sequenceNumber, isCritical
        );
        messageRepository.save(message);
        
        cleanupOldMessages(runId);
    }
    
    private void cleanupOldMessages(UUID runId) {
        List<PersistedCollaborationMessage> messages = 
            messageRepository.findByRunIdOrderBySequenceNumberDesc(runId);
        if (messages.size() > MAX_PERSISTED_MESSAGES_PER_RUN) {
            messageRepository.deleteOldMessagesKeepingLatest(runId, MAX_PERSISTED_MESSAGES_PER_RUN);
        }
    }
    
    public List<PersistedCollaborationMessage> getPersistedMessages(UUID runId, Long afterSequence) {
        if (afterSequence == null) {
            return messageRepository.findByRunIdOrderBySequenceNumberDesc(runId);
        }
        return messageRepository.findByRunIdAfterSequence(runId, afterSequence);
    }
    
    public List<PersistedCollaborationMessage> getCriticalMessages(UUID runId) {
        return messageRepository.findCriticalMessagesByRunId(runId);
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
