package com.atlasia.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class CrdtSyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(CrdtSyncService.class);
    
    private final CrdtDocumentManager documentManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<UUID, Set<String>> peerConnections = new ConcurrentHashMap<>();
    private final String localRegion;
    
    public CrdtSyncService(CrdtDocumentManager documentManager, 
                          SimpMessagingTemplate messagingTemplate) {
        this.documentManager = documentManager;
        this.messagingTemplate = messagingTemplate;
        this.localRegion = System.getenv().getOrDefault("REGION", "us-east-1");
    }
    
    public void registerPeer(UUID runId, String peerRegion) {
        peerConnections.computeIfAbsent(runId, k -> new CopyOnWriteArraySet<>())
                      .add(peerRegion);
        logger.info("Registered peer {} for run {}", peerRegion, runId);
        
        syncWithPeer(runId, peerRegion);
    }
    
    public void unregisterPeer(UUID runId, String peerRegion) {
        Set<String> peers = peerConnections.get(runId);
        if (peers != null) {
            peers.remove(peerRegion);
            logger.info("Unregistered peer {} for run {}", peerRegion, runId);
        }
    }
    
    public void broadcastChanges(UUID runId, byte[] changes) {
        Set<String> peers = peerConnections.get(runId);
        if (peers == null || peers.isEmpty()) {
            return;
        }
        
        Map<String, Object> syncMessage = new HashMap<>();
        syncMessage.put("type", "CRDT_SYNC");
        syncMessage.put("runId", runId.toString());
        syncMessage.put("sourceRegion", localRegion);
        syncMessage.put("changes", Base64.getEncoder().encodeToString(changes));
        syncMessage.put("timestamp", System.currentTimeMillis());
        
        for (String peer : peers) {
            try {
                messagingTemplate.convertAndSend(
                    "/topic/crdt/sync/" + peer,
                    syncMessage
                );
                logger.debug("Broadcasted changes to peer {} for run {}", peer, runId);
            } catch (Exception e) {
                logger.error("Failed to broadcast to peer {}: {}", peer, e.getMessage());
            }
        }
        
        messagingTemplate.convertAndSend(
            "/topic/runs/" + runId + "/collaboration",
            syncMessage
        );
    }
    
    public void handleIncomingSync(UUID runId, String sourceRegion, byte[] changes) {
        if (sourceRegion.equals(localRegion)) {
            logger.debug("Ignoring sync from same region");
            return;
        }
        
        try {
            documentManager.applyChanges(runId, changes);
            logger.info("Applied changes from region {} for run {}", sourceRegion, runId);
            
            broadcastToLocalClients(runId, sourceRegion, changes);
        } catch (Exception e) {
            logger.error("Failed to apply changes from {}: {}", sourceRegion, e.getMessage());
        }
    }
    
    private void broadcastToLocalClients(UUID runId, String sourceRegion, byte[] changes) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "CRDT_UPDATE");
        message.put("runId", runId.toString());
        message.put("sourceRegion", sourceRegion);
        message.put("changes", Base64.getEncoder().encodeToString(changes));
        message.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend(
            "/topic/runs/" + runId + "/collaboration",
            message
        );
    }
    
    private void syncWithPeer(UUID runId, String peerRegion) {
        byte[] currentState = documentManager.getChanges(runId);
        if (currentState.length == 0) {
            return;
        }
        
        Map<String, Object> fullSyncMessage = new HashMap<>();
        fullSyncMessage.put("type", "CRDT_FULL_SYNC");
        fullSyncMessage.put("runId", runId.toString());
        fullSyncMessage.put("sourceRegion", localRegion);
        fullSyncMessage.put("state", Base64.getEncoder().encodeToString(currentState));
        fullSyncMessage.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend(
            "/topic/crdt/sync/" + peerRegion,
            fullSyncMessage
        );
        
        logger.info("Sent full sync to peer {} for run {}", peerRegion, runId);
    }
    
    public void handleFullSync(UUID runId, String sourceRegion, byte[] state) {
        if (sourceRegion.equals(localRegion)) {
            return;
        }
        
        try {
            byte[] mergedState = documentManager.mergeDocuments(runId, state);
            logger.info("Merged full sync from region {} for run {}", sourceRegion, runId);
            
            broadcastToLocalClients(runId, sourceRegion, mergedState);
        } catch (Exception e) {
            logger.error("Failed to merge full sync from {}: {}", sourceRegion, e.getMessage());
        }
    }
    
    public Set<String> getPeers(UUID runId) {
        return peerConnections.getOrDefault(runId, Collections.emptySet());
    }
    
    public String getLocalRegion() {
        return localRegion;
    }
    
    public void cleanupRun(UUID runId) {
        peerConnections.remove(runId);
        documentManager.removeDocument(runId);
        logger.info("Cleaned up CRDT state for run {}", runId);
    }
}
