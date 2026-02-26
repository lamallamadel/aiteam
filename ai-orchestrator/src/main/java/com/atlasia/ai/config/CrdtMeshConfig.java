package com.atlasia.ai.config;

import com.atlasia.ai.service.CrdtSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableScheduling
public class CrdtMeshConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(CrdtMeshConfig.class);
    
    private final CrdtSyncService crdtSyncService;
    private final WebSocketStompClient stompClient;
    
    @Value("${crdt.mesh.peers:}")
    private String meshPeersConfig;
    
    private final Map<String, StompSession> peerSessions = new ConcurrentHashMap<>();
    
    public CrdtMeshConfig(CrdtSyncService crdtSyncService, WebSocketStompClient stompClient) {
        this.crdtSyncService = crdtSyncService;
        this.stompClient = stompClient;
    }
    
    @PostConstruct
    public void initializeMesh() {
        if (meshPeersConfig == null || meshPeersConfig.isBlank()) {
            logger.info("No CRDT mesh peers configured, running in single-region mode");
            return;
        }
        
        String[] peers = meshPeersConfig.split(",");
        for (String peer : peers) {
            String trimmedPeer = peer.trim();
            if (!trimmedPeer.isEmpty()) {
                connectToPeer(trimmedPeer);
            }
        }
    }
    
    private void connectToPeer(String peerUrl) {
        try {
            StompSession session = stompClient.connectAsync(
                peerUrl,
                new CrdtMeshSessionHandler(peerUrl)
            ).get();
            
            peerSessions.put(peerUrl, session);
            
            String localRegion = crdtSyncService.getLocalRegion();
            session.subscribe("/topic/crdt/sync/" + localRegion, new CrdtMeshSessionHandler(peerUrl));
            
            logger.info("Connected to CRDT mesh peer: {}", peerUrl);
        } catch (Exception e) {
            logger.error("Failed to connect to CRDT mesh peer {}: {}", peerUrl, e.getMessage());
        }
    }
    
    @Scheduled(fixedDelay = 30000)
    public void monitorMeshConnections() {
        peerSessions.forEach((url, session) -> {
            if (!session.isConnected()) {
                logger.warn("Lost connection to peer {}, reconnecting...", url);
                peerSessions.remove(url);
                connectToPeer(url);
            }
        });
    }
    
    private class CrdtMeshSessionHandler extends StompSessionHandlerAdapter {
        
        private final String peerUrl;
        
        public CrdtMeshSessionHandler(String peerUrl) {
            this.peerUrl = peerUrl;
        }
        
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            logger.info("CRDT mesh session established with {}", peerUrl);
        }
        
        @Override
        public void handleException(StompSession session, StompCommand command, 
                                   StompHeaders headers, byte[] payload, Throwable exception) {
            logger.error("CRDT mesh error with {}: {}", peerUrl, exception.getMessage());
        }
        
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            logger.error("CRDT mesh transport error with {}: {}", peerUrl, exception.getMessage());
        }
        
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }
        
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) payload;
                
                String type = (String) message.get("type");
                String runIdStr = (String) message.get("runId");
                String sourceRegion = (String) message.get("sourceRegion");
                
                if (runIdStr == null || sourceRegion == null) {
                    return;
                }
                
                UUID runId = UUID.fromString(runIdStr);
                
                if ("CRDT_SYNC".equals(type)) {
                    String changesBase64 = (String) message.get("changes");
                    if (changesBase64 != null) {
                        byte[] changes = Base64.getDecoder().decode(changesBase64);
                        crdtSyncService.handleIncomingSync(runId, sourceRegion, changes);
                    }
                } else if ("CRDT_FULL_SYNC".equals(type)) {
                    String stateBase64 = (String) message.get("state");
                    if (stateBase64 != null) {
                        byte[] state = Base64.getDecoder().decode(stateBase64);
                        crdtSyncService.handleFullSync(runId, sourceRegion, state);
                    }
                }
            }
        }
    }
    
    public Map<String, StompSession> getPeerSessions() {
        return Collections.unmodifiableMap(peerSessions);
    }
}
