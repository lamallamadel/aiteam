package com.atlasia.ai.model;

import java.util.*;

public class CrdtDocumentState {
    
    private final List<GraftOperation> grafts;
    private final Set<String> prunedSteps;
    private final Map<String, Object> flags;
    
    public CrdtDocumentState() {
        this.grafts = new ArrayList<>();
        this.prunedSteps = new HashSet<>();
        this.flags = new HashMap<>();
    }
    
    public CrdtDocumentState(List<GraftOperation> grafts, Set<String> prunedSteps, Map<String, Object> flags) {
        this.grafts = new ArrayList<>(grafts);
        this.prunedSteps = new HashSet<>(prunedSteps);
        this.flags = new HashMap<>(flags);
    }
    
    public List<GraftOperation> getGrafts() {
        return Collections.unmodifiableList(grafts);
    }
    
    public Set<String> getPrunedSteps() {
        return Collections.unmodifiableSet(prunedSteps);
    }
    
    public Map<String, Object> getFlags() {
        return Collections.unmodifiableMap(flags);
    }
    
    public static class GraftOperation {
        private final String id;
        private final String after;
        private final String agentName;
        private final long timestamp;
        private final String userId;
        
        public GraftOperation(String id, String after, String agentName, long timestamp, String userId) {
            this.id = id;
            this.after = after;
            this.agentName = agentName;
            this.timestamp = timestamp;
            this.userId = userId;
        }
        
        public String getId() { return id; }
        public String getAfter() { return after; }
        public String getAgentName() { return agentName; }
        public long getTimestamp() { return timestamp; }
        public String getUserId() { return userId; }
    }
}
