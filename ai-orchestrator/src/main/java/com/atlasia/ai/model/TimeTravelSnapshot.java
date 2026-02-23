package com.atlasia.ai.model;

import java.time.Instant;
import java.util.*;

public class TimeTravelSnapshot {
    private UUID eventId;
    private String userId;
    private String eventType;
    private Instant timestamp;
    private Map<String, Object> stateBefore;
    private Map<String, Object> stateAfter;
    private Map<String, Object> diff;
    private String description;
    
    public TimeTravelSnapshot() {}
    
    public TimeTravelSnapshot(UUID eventId, String userId, String eventType, Instant timestamp,
                             Map<String, Object> stateBefore, Map<String, Object> stateAfter,
                             Map<String, Object> diff, String description) {
        this.eventId = eventId;
        this.userId = userId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.stateBefore = stateBefore;
        this.stateAfter = stateAfter;
        this.diff = diff;
        this.description = description;
    }
    
    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public Map<String, Object> getStateBefore() { return stateBefore; }
    public void setStateBefore(Map<String, Object> stateBefore) { this.stateBefore = stateBefore; }
    
    public Map<String, Object> getStateAfter() { return stateAfter; }
    public void setStateAfter(Map<String, Object> stateAfter) { this.stateAfter = stateAfter; }
    
    public Map<String, Object> getDiff() { return diff; }
    public void setDiff(Map<String, Object> diff) { this.diff = diff; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
