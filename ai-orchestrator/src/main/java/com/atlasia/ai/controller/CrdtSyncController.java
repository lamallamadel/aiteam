package com.atlasia.ai.controller;

import com.atlasia.ai.model.CrdtDocumentState;
import com.atlasia.ai.service.CollaborationService;
import com.atlasia.ai.service.CrdtDocumentManager;
import com.atlasia.ai.service.CrdtSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/crdt")
public class CrdtSyncController {
    
    private final CrdtDocumentManager documentManager;
    private final CrdtSyncService syncService;
    private final CollaborationService collaborationService;
    
    public CrdtSyncController(CrdtDocumentManager documentManager,
                             CrdtSyncService syncService,
                             CollaborationService collaborationService) {
        this.documentManager = documentManager;
        this.syncService = syncService;
        this.collaborationService = collaborationService;
    }
    
    @GetMapping("/runs/{runId}/state")
    public ResponseEntity<Map<String, Object>> getState(@PathVariable UUID runId) {
        CrdtDocumentState state = documentManager.getState(runId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("runId", runId);
        response.put("grafts", state.getGrafts());
        response.put("prunedSteps", state.getPrunedSteps());
        response.put("flags", state.getFlags());
        response.put("region", syncService.getLocalRegion());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/runs/{runId}/changes")
    public ResponseEntity<Map<String, Object>> getChanges(@PathVariable UUID runId) {
        byte[] changes = documentManager.getChanges(runId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("runId", runId);
        response.put("changes", Base64.getEncoder().encodeToString(changes));
        response.put("region", syncService.getLocalRegion());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/runs/{runId}/sync")
    public ResponseEntity<Map<String, Object>> syncChanges(
            @PathVariable UUID runId,
            @RequestBody Map<String, Object> syncRequest) {
        
        String changesBase64 = (String) syncRequest.get("changes");
        String sourceRegion = (String) syncRequest.get("sourceRegion");
        Long lamportTimestamp = ((Number) syncRequest.get("lamportTimestamp")).longValue();
        
        if (changesBase64 != null && sourceRegion != null) {
            byte[] changes = Base64.getDecoder().decode(changesBase64);
            collaborationService.handleRemoteSync(runId, sourceRegion, changes, lamportTimestamp);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("region", syncService.getLocalRegion());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/runs/{runId}/register-peer")
    public ResponseEntity<Map<String, Object>> registerPeer(
            @PathVariable UUID runId,
            @RequestBody Map<String, String> peerRequest) {
        
        String peerRegion = peerRequest.get("peerRegion");
        if (peerRegion != null) {
            syncService.registerPeer(runId, peerRegion);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("peers", syncService.getPeers(runId));
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/runs/{runId}/peers")
    public ResponseEntity<Map<String, Object>> getPeers(@PathVariable UUID runId) {
        Map<String, Object> response = new HashMap<>();
        response.put("runId", runId);
        response.put("peers", syncService.getPeers(runId));
        response.put("region", syncService.getLocalRegion());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/mesh/status")
    public ResponseEntity<Map<String, Object>> getMeshStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("region", syncService.getLocalRegion());
        response.put("status", "active");
        
        return ResponseEntity.ok(response);
    }
}
