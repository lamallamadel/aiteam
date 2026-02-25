package com.atlasia.ai.service;

import com.atlasia.ai.model.CollaborationEventEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.CollaborationEventRepository;
import com.atlasia.ai.persistence.PersistedCollaborationMessageRepository;
import com.atlasia.ai.persistence.RunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceTest {

    @Mock
    private CollaborationEventRepository eventRepository;

    @Mock
    private PersistedCollaborationMessageRepository messageRepository;

    @Mock
    private RunRepository runRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private WebSocketConnectionMonitor connectionMonitor;

    @Mock
    private AuditTrailService auditTrailService;

    @Mock
    private CrdtDocumentManager crdtDocumentManager;

    @Mock
    private CrdtSyncService crdtSyncService;

    @Mock
    private CrdtSnapshotService crdtSnapshotService;

    private CollaborationService collaborationService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(messageRepository.findMaxSequenceNumberByRunId(any())).thenReturn(0L);
        lenient().when(crdtSyncService.getLocalRegion()).thenReturn("us-east-1");
        lenient().when(crdtDocumentManager.applyGraftMutation(any(), any(), any())).thenReturn(new byte[0]);
        lenient().when(crdtDocumentManager.applyPruneMutation(any(), any(), any())).thenReturn(new byte[0]);
        lenient().when(crdtDocumentManager.applyFlagMutation(any(), any(), any())).thenReturn(new byte[0]);
        lenient().when(crdtDocumentManager.getState(any())).thenReturn(new com.atlasia.ai.model.CrdtDocumentState());
        lenient().when(crdtDocumentManager.serializeState(any())).thenReturn("{}");
        collaborationService = new CollaborationService(
                eventRepository, messageRepository, runRepository, 
                messagingTemplate, objectMapper, connectionMonitor, auditTrailService,
                crdtDocumentManager, crdtSyncService, crdtSnapshotService);
    }

    @Test
    void testHandleGraftMutation() {
        UUID runId = UUID.randomUUID();
        String userId = "user1";
        Map<String, Object> graftData = new HashMap<>();
        graftData.put("after", "ARCHITECT");
        graftData.put("agentName", "security-scanner-v1");

        RunEntity run = new RunEntity(runId, "test/repo", 1, "code", RunStatus.RECEIVED, Instant.now());
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        collaborationService.handleGraftMutation(runId, userId, graftData);

        // Verify event saved
        ArgumentCaptor<CollaborationEventEntity> eventCaptor = ArgumentCaptor.forClass(CollaborationEventEntity.class);
        verify(eventRepository).save(eventCaptor.capture());
        CollaborationEventEntity savedEvent = eventCaptor.getValue();
        assertEquals(runId, savedEvent.getRunId());
        assertEquals(userId, savedEvent.getUserId());
        assertEquals("GRAFT", savedEvent.getEventType());

        // Verify run updated
        verify(runRepository).save(any(RunEntity.class));

        // Verify broadcast
        verify(messagingTemplate).convertAndSend(
                eq("/topic/runs/" + runId + "/collaboration"),
                any(Map.class));
    }

    @Test
    void testHandlePruneMutation() {
        UUID runId = UUID.randomUUID();
        String userId = "user2";
        Map<String, Object> pruneData = new HashMap<>();
        pruneData.put("stepId", "QUALIFIER");
        pruneData.put("isPruned", true);

        RunEntity run = new RunEntity(runId, "test/repo", 1, "code", RunStatus.RECEIVED, Instant.now());
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        collaborationService.handlePruneMutation(runId, userId, pruneData);

        // Verify event saved with CRDT changes
        ArgumentCaptor<CollaborationEventEntity> eventCaptor = ArgumentCaptor.forClass(CollaborationEventEntity.class);
        verify(eventRepository).save(eventCaptor.capture());
        CollaborationEventEntity savedEvent = eventCaptor.getValue();
        assertNotNull(savedEvent.getCrdtChanges());
        assertEquals("PRUNE", savedEvent.getEventType());

        // Verify CRDT mutation applied
        verify(crdtDocumentManager).applyPruneMutation(eq(runId), eq(userId), eq(pruneData));
        
        // Verify CRDT sync broadcast
        verify(crdtSyncService).broadcastChanges(eq(runId), any(byte[].class));

        // Verify broadcast
        verify(messagingTemplate).convertAndSend(
                eq("/topic/runs/" + runId + "/collaboration"),
                any(Map.class));
    }

    @Test
    void testHandleUserJoinAndLeave() {
        UUID runId = UUID.randomUUID();
        String userId = "user3";

        // Join
        collaborationService.handleUserJoin(runId, userId);
        Set<String> activeUsers = collaborationService.getActiveUsers(runId);
        assertTrue(activeUsers.contains(userId));
        verify(messagingTemplate).convertAndSend(
                eq("/topic/runs/" + runId + "/collaboration"),
                any(Map.class));

        // Leave
        collaborationService.handleUserLeave(runId, userId);
        activeUsers = collaborationService.getActiveUsers(runId);
        assertFalse(activeUsers.contains(userId));
    }

    @Test
    void testHandleCursorMove() {
        UUID runId = UUID.randomUUID();
        String userId = "user4";
        String nodeId = "DEVELOPER";

        collaborationService.handleUserJoin(runId, userId);
        collaborationService.handleCursorMove(runId, userId, nodeId);

        Map<String, String> cursors = collaborationService.getCursorPositions(runId);
        assertEquals(nodeId, cursors.get(userId));
    }

    @Test
    void testConcurrentPruneMutations() {
        UUID runId = UUID.randomUUID();
        RunEntity run = new RunEntity(runId, "test/repo", 1, "code", RunStatus.RECEIVED, Instant.now());
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));

        // User 1 prunes QUALIFIER
        Map<String, Object> prune1 = new HashMap<>();
        prune1.put("stepId", "QUALIFIER");
        prune1.put("isPruned", true);
        collaborationService.handlePruneMutation(runId, "user1", prune1);

        // User 2 prunes WRITER
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        Map<String, Object> prune2 = new HashMap<>();
        prune2.put("stepId", "WRITER");
        prune2.put("isPruned", true);
        collaborationService.handlePruneMutation(runId, "user2", prune2);

        // Both mutations should be applied to CRDT (conflict-free)
        verify(crdtDocumentManager).applyPruneMutation(eq(runId), eq("user1"), eq(prune1));
        verify(crdtDocumentManager).applyPruneMutation(eq(runId), eq("user2"), eq(prune2));
        
        // Both should be broadcast via CRDT sync
        verify(crdtSyncService, times(2)).broadcastChanges(eq(runId), any(byte[].class));
    }

    @Test
    void testGetRecentEvents() {
        UUID runId = UUID.randomUUID();
        List<CollaborationEventEntity> mockEvents = Arrays.asList(
                new CollaborationEventEntity(runId, "user1", "GRAFT", "{}", Instant.now()),
                new CollaborationEventEntity(runId, "user2", "PRUNE", "{}", Instant.now())
        );
        when(eventRepository.findTop100ByRunIdOrderByTimestampDesc(runId)).thenReturn(mockEvents);

        List<CollaborationEventEntity> events = collaborationService.getRecentEvents(runId, 50);
        assertEquals(2, events.size());
    }
}
