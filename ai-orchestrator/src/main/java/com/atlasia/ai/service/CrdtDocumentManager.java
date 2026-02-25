package com.atlasia.ai.service;

import com.atlasia.ai.model.CrdtDocumentState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CrdtDocumentManager {
    
    private final Map<UUID, CrdtDocument> documents = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    
    public CrdtDocumentManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public CrdtDocument getOrCreateDocument(UUID runId) {
        return documents.computeIfAbsent(runId, id -> new CrdtDocument());
    }
    
    public CrdtDocument getDocument(UUID runId) {
        return documents.get(runId);
    }
    
    public void removeDocument(UUID runId) {
        documents.remove(runId);
    }
    
    public byte[] applyGraftMutation(UUID runId, String userId, Map<String, Object> graftData) {
        CrdtDocument doc = getOrCreateDocument(runId);
        
        String graftId = UUID.randomUUID().toString();
        String after = (String) graftData.get("after");
        String agentName = (String) graftData.get("agentName");
        long timestamp = System.currentTimeMillis();
        
        CrdtChange change = new CrdtChange(
            CrdtChange.ChangeType.GRAFT_ADD,
            graftId,
            Map.of(
                "id", graftId,
                "after", after,
                "agentName", agentName,
                "timestamp", timestamp,
                "userId", userId
            )
        );
        
        doc.applyChange(change);
        
        return serializeChange(change);
    }
    
    public byte[] applyPruneMutation(UUID runId, String userId, Map<String, Object> pruneData) {
        CrdtDocument doc = getOrCreateDocument(runId);
        
        String stepId = (String) pruneData.get("stepId");
        Boolean isPruned = (Boolean) pruneData.getOrDefault("isPruned", true);
        
        CrdtChange change = new CrdtChange(
            isPruned ? CrdtChange.ChangeType.PRUNE_ADD : CrdtChange.ChangeType.PRUNE_REMOVE,
            stepId,
            Map.of("stepId", stepId, "isPruned", isPruned)
        );
        
        doc.applyChange(change);
        
        return serializeChange(change);
    }
    
    public byte[] applyFlagMutation(UUID runId, String userId, Map<String, Object> flagData) {
        CrdtDocument doc = getOrCreateDocument(runId);
        
        String flagKey = (String) flagData.get("key");
        Object flagValue = flagData.get("value");
        
        CrdtChange change = new CrdtChange(
            flagValue != null ? CrdtChange.ChangeType.FLAG_SET : CrdtChange.ChangeType.FLAG_REMOVE,
            flagKey,
            Map.of("key", flagKey, "value", flagValue != null ? flagValue : "")
        );
        
        doc.applyChange(change);
        
        return serializeChange(change);
    }
    
    public void applyChanges(UUID runId, byte[] changes) {
        CrdtDocument doc = getOrCreateDocument(runId);
        CrdtChange change = deserializeChange(changes);
        if (change != null) {
            doc.applyChange(change);
        }
    }
    
    public byte[] getChanges(UUID runId) {
        CrdtDocument doc = getDocument(runId);
        if (doc == null) {
            return new byte[0];
        }
        return serializeDocument(doc);
    }
    
    public CrdtDocumentState getState(UUID runId) {
        CrdtDocument doc = getDocument(runId);
        if (doc == null) {
            return new CrdtDocumentState();
        }
        
        List<CrdtDocumentState.GraftOperation> grafts = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : doc.grafts.entrySet()) {
            Map<String, Object> graftData = entry.getValue();
            grafts.add(new CrdtDocumentState.GraftOperation(
                (String) graftData.get("id"),
                (String) graftData.get("after"),
                (String) graftData.get("agentName"),
                ((Number) graftData.get("timestamp")).longValue(),
                (String) graftData.get("userId")
            ));
        }
        
        return new CrdtDocumentState(grafts, new HashSet<>(doc.prunedSteps), new HashMap<>(doc.flags));
    }
    
    public String serializeState(CrdtDocumentState state) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "grafts", state.getGrafts(),
                "prunedSteps", state.getPrunedSteps(),
                "flags", state.getFlags()
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
    
    public byte[] mergeDocuments(UUID runId, byte[] remoteChanges) {
        CrdtDocument doc = getOrCreateDocument(runId);
        CrdtDocument remoteDoc = deserializeDocument(remoteChanges);
        if (remoteDoc != null) {
            doc.merge(remoteDoc);
        }
        return serializeDocument(doc);
    }
    
    private byte[] serializeChange(CrdtChange change) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(change);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
    
    private CrdtChange deserializeChange(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (CrdtChange) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
    
    private byte[] serializeDocument(CrdtDocument doc) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(doc);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
    
    private CrdtDocument deserializeDocument(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (CrdtDocument) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
    
    static class CrdtDocument implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private final Map<String, Map<String, Object>> grafts = new ConcurrentHashMap<>();
        private final Set<String> prunedSteps = ConcurrentHashMap.newKeySet();
        private final Map<String, Object> flags = new ConcurrentHashMap<>();
        
        public void applyChange(CrdtChange change) {
            switch (change.type) {
                case GRAFT_ADD:
                    grafts.put(change.key, change.data);
                    break;
                case PRUNE_ADD:
                    prunedSteps.add(change.key);
                    break;
                case PRUNE_REMOVE:
                    prunedSteps.remove(change.key);
                    break;
                case FLAG_SET:
                    flags.put(change.key, change.data.get("value"));
                    break;
                case FLAG_REMOVE:
                    flags.remove(change.key);
                    break;
            }
        }
        
        public void merge(CrdtDocument other) {
            grafts.putAll(other.grafts);
            prunedSteps.addAll(other.prunedSteps);
            flags.putAll(other.flags);
        }
    }
    
    static class CrdtChange implements Serializable {
        private static final long serialVersionUID = 1L;
        
        enum ChangeType {
            GRAFT_ADD, PRUNE_ADD, PRUNE_REMOVE, FLAG_SET, FLAG_REMOVE
        }
        
        final ChangeType type;
        final String key;
        final Map<String, Object> data;
        
        CrdtChange(ChangeType type, String key, Map<String, Object> data) {
            this.type = type;
            this.key = key;
            this.data = new HashMap<>(data);
        }
    }
}
