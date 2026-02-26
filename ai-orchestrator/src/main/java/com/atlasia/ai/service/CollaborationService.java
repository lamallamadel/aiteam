package com.atlasia.ai.service;

import com.atlasia.ai.model.CollaborationEventEntity;
import com.atlasia.ai.model.CrdtDocumentState;
import com.atlasia.ai.model.PersistedCollaborationMessage;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.persistence.CollaborationEventRepository;
import com.atlasia.ai.persistence.PersistedCollaborationMessageRepository;
import com.atlasia.ai.persistence.RunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
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
    private final AuditTrailService auditTrailService;
    private final CrdtDocumentManager crdtDocumentManager;
    private final CrdtSyncService crdtSyncService;
    private final CrdtSnapshotService crdtSnapshotService;
    private final Tracer tracer;
    
    private final Map<UUID, Set<String>> activeUsers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> cursorPositions = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> sequenceCounters = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> lamportClocks = new ConcurrentHashMap<>();
    
    private static final int MAX_PERSISTED_MESSAGES_PER_RUN = 1000;
    private static final Set<String> CRITICAL_EVENT_TYPES = Set.of("GRAFT", "PRUNE", "FLAG");

    public CollaborationService(
            CollaborationEventRepository eventRepository,
            PersistedCollaborationMessageRepository messageRepository,
            RunRepository runRepository,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            WebSocketConnectionMonitor connectionMonitor,
            AuditTrailService auditTrailService,
            CrdtDocumentManager crdtDocumentManager,
            CrdtSyncService crdtSyncService,
            CrdtSnapshotService crdtSnapshotService,
            Tracer tracer) {
        this.eventRepository = eventRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.connectionMonitor = connectionMonitor;
        this.auditTrailService = auditTrailService;
        this.crdtDocumentManager = crdtDocumentManager;
        this.crdtSyncService = crdtSyncService;
        this.crdtSnapshotService = crdtSnapshotService;
        this.tracer = tracer;
    }

    @Transactional
    public void handleGraftMutation(UUID runId, String userId, Map<String, Object> graftData) {
        Span span = tracer.spanBuilder("collaboration.graft")
                .setAttribute("run.id", runId.toString())
                .setAttribute("user.id", userId)
                .setAttribute("event.type", "GRAFT")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            CrdtDocumentState stateBefore = crdtDocumentManager.getState(runId);
            
            byte[] crdtChanges = crdtDocumentManager.applyGraftMutation(runId, userId, graftData);
            
            CrdtDocumentState stateAfter = crdtDocumentManager.getState(runId);
            
            long lamportTimestamp = incrementLamportClock(runId);
            String eventData = serializeEventData(graftData);
            
            CollaborationEventEntity event = new CollaborationEventEntity(
                    runId, userId, "GRAFT", eventData, Instant.now(), 
                    crdtChanges, crdtSyncService.getLocalRegion(), lamportTimestamp);
            
            event.setStateBefore(crdtDocumentManager.serializeState(stateBefore));
            event.setStateAfter(crdtDocumentManager.serializeState(stateAfter));
            
            auditTrailService.updateCollaborationEventHash(event);
            eventRepository.save(event);

            applyGraftToRun(runId, stateAfter);
            
            crdtSnapshotService.incrementEventCount(runId);
            crdtSyncService.broadcastChanges(runId, crdtChanges);
            broadcastEvent(runId, "GRAFT", userId, graftData, lamportTimestamp);

            span.setStatus(StatusCode.OK);
            span.setAttribute("lamport.timestamp", lamportTimestamp);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Transactional
    public void handlePruneMutation(UUID runId, String userId, Map<String, Object> pruneData) {
        CrdtDocumentState stateBefore = crdtDocumentManager.getState(runId);
        
        byte[] crdtChanges = crdtDocumentManager.applyPruneMutation(runId, userId, pruneData);
        
        CrdtDocumentState stateAfter = crdtDocumentManager.getState(runId);
        
        long lamportTimestamp = incrementLamportClock(runId);
        String eventData = serializeEventData(pruneData);
        
        CollaborationEventEntity event = new CollaborationEventEntity(
                runId, userId, "PRUNE", eventData, Instant.now(),
                crdtChanges, crdtSyncService.getLocalRegion(), lamportTimestamp);
        
        event.setStateBefore(crdtDocumentManager.serializeState(stateBefore));
        event.setStateAfter(crdtDocumentManager.serializeState(stateAfter));
        
        auditTrailService.updateCollaborationEventHash(event);
        eventRepository.save(event);

        applyPruneToRun(runId, stateAfter);
        
        crdtSnapshotService.incrementEventCount(runId);
        crdtSyncService.broadcastChanges(runId, crdtChanges);
        broadcastEvent(runId, "PRUNE", userId, pruneData, lamportTimestamp);
    }

    @Transactional
    public void handleFlagMutation(UUID runId, String userId, Map<String, Object> flagData) {
        CrdtDocumentState stateBefore = crdtDocumentManager.getState(runId);
        
        byte[] crdtChanges = crdtDocumentManager.applyFlagMutation(runId, userId, flagData);
        
        CrdtDocumentState stateAfter = crdtDocumentManager.getState(runId);
        
        long lamportTimestamp = incrementLamportClock(runId);
        String eventData = serializeEventData(flagData);
        
        CollaborationEventEntity event = new CollaborationEventEntity(
                runId, userId, "FLAG", eventData, Instant.now(),
                crdtChanges, crdtSyncService.getLocalRegion(), lamportTimestamp);
        
        event.setStateBefore(crdtDocumentManager.serializeState(stateBefore));
        event.setStateAfter(crdtDocumentManager.serializeState(stateAfter));
        
        auditTrailService.updateCollaborationEventHash(event);
        eventRepository.save(event);

        crdtSnapshotService.incrementEventCount(runId);
        crdtSyncService.broadcastChanges(runId, crdtChanges);
        broadcastEvent(runId, "FLAG", userId, flagData, lamportTimestamp);
    }

    public void handleUserJoin(UUID runId, String userId) {
        Span span = tracer.spanBuilder("collaboration.user_join")
                .setAttribute("run.id", runId.toString())
                .setAttribute("user.id", userId)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            activeUsers.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet()).add(userId);
            cursorPositions.computeIfAbsent(runId, k -> new ConcurrentHashMap<>());

            crdtSnapshotService.restoreFromSnapshot(runId);

            Map<String, Object> presenceData = new HashMap<>();
            presenceData.put("activeUsers", new ArrayList<>(activeUsers.get(runId)));
            
            CrdtDocumentState currentState = crdtDocumentManager.getState(runId);
            presenceData.put("crdtState", currentState);
            
            broadcastEvent(runId, "USER_JOIN", userId, presenceData, incrementLamportClock(runId));

            span.setStatus(StatusCode.OK);
            span.setAttribute("active_users.count", activeUsers.get(runId).size());
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    public void handleUserLeave(UUID runId, String userId) {
        Set<String> users = activeUsers.get(runId);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                activeUsers.remove(runId);
                cursorPositions.remove(runId);
                crdtSnapshotService.createSnapshot(runId);
                crdtSyncService.cleanupRun(runId);
                crdtSnapshotService.cleanupRun(runId);
            }
        }

        Map<String, String> cursors = cursorPositions.get(runId);
        if (cursors != null) {
            cursors.remove(userId);
        }

        Map<String, Object> presenceData = new HashMap<>();
        presenceData.put("activeUsers", users != null ? new ArrayList<>(users) : Collections.emptyList());
        
        broadcastEvent(runId, "USER_LEAVE", userId, presenceData, incrementLamportClock(runId));
    }

    public void handleCursorMove(UUID runId, String userId, String nodeId) {
        Map<String, String> cursors = cursorPositions.computeIfAbsent(runId, k -> new ConcurrentHashMap<>());
        cursors.put(userId, nodeId);

        Map<String, Object> cursorData = new HashMap<>();
        cursorData.put("nodeId", nodeId);
        cursorData.put("cursors", new HashMap<>(cursors));

        broadcastEvent(runId, "CURSOR_MOVE", userId, cursorData, incrementLamportClock(runId));
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

    private void broadcastEvent(UUID runId, String eventType, String userId, 
                               Map<String, Object> data, long lamportTimestamp) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventType", eventType);
        message.put("userId", userId);
        Instant now = Instant.now();
        message.put("timestamp", now.toEpochMilli());
        message.put("lamportTimestamp", lamportTimestamp);
        message.put("data", data);

        long sequenceNumber = getNextSequenceNumber(runId);
        message.put("sequenceNumber", sequenceNumber);

        boolean isCritical = CRITICAL_EVENT_TYPES.contains(eventType);
        if (isCritical) {
            persistMessage(runId, userId, eventType, data, now, sequenceNumber, true, lamportTimestamp);
        }

        messagingTemplate.convertAndSend("/topic/runs/" + runId + "/collaboration", message);
    }
    
    private long getNextSequenceNumber(UUID runId) {
        return sequenceCounters.computeIfAbsent(runId, k -> {
            Long maxSeq = messageRepository.findMaxSequenceNumberByRunId(runId);
            return new AtomicLong(maxSeq != null ? maxSeq : 0L);
        }).incrementAndGet();
    }
    
    private long incrementLamportClock(UUID runId) {
        return lamportClocks.computeIfAbsent(runId, k -> new AtomicLong(0L))
                           .incrementAndGet();
    }
    
    public void updateLamportClock(UUID runId, long remoteTimestamp) {
        AtomicLong clock = lamportClocks.computeIfAbsent(runId, k -> new AtomicLong(0L));
        long currentTimestamp = clock.get();
        long newTimestamp = Math.max(currentTimestamp, remoteTimestamp) + 1;
        clock.set(newTimestamp);
    }
    
    private void persistMessage(UUID runId, String userId, String eventType, 
                               Map<String, Object> data, Instant timestamp, 
                               long sequenceNumber, boolean isCritical, long lamportTimestamp) {
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

    private void applyGraftToRun(UUID runId, CrdtDocumentState state) {
        runRepository.findById(runId).ifPresent(run -> {
            try {
                List<Map<String, Object>> graftsList = new ArrayList<>();
                for (CrdtDocumentState.GraftOperation graft : state.getGrafts()) {
                    Map<String, Object> graftMap = new HashMap<>();
                    graftMap.put("id", graft.getId());
                    graftMap.put("after", graft.getAfter());
                    graftMap.put("agentName", graft.getAgentName());
                    graftMap.put("timestamp", graft.getTimestamp());
                    graftMap.put("userId", graft.getUserId());
                    graftsList.add(graftMap);
                }
                
                String updated = objectMapper.writeValueAsString(graftsList);
                run.setPendingGrafts(updated);
                runRepository.save(run);
            } catch (JsonProcessingException e) {
                // Log error
            }
        });
    }

    private void applyPruneToRun(UUID runId, CrdtDocumentState state) {
        runRepository.findById(runId).ifPresent(run -> {
            String updated = String.join(",", state.getPrunedSteps());
            run.setPrunedSteps(updated);
            runRepository.save(run);
        });
    }

    private String serializeEventData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
    
    public void handleRemoteSync(UUID runId, String sourceRegion, byte[] changes, long lamportTimestamp) {
        updateLamportClock(runId, lamportTimestamp);
        crdtSyncService.handleIncomingSync(runId, sourceRegion, changes);
        
        CrdtDocumentState state = crdtDocumentManager.getState(runId);
        applyGraftToRun(runId, state);
        applyPruneToRun(runId, state);
    }
    
    public CrdtDocumentState getCurrentState(UUID runId) {
        return crdtDocumentManager.getState(runId);
    }
}
