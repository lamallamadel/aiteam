package com.atlasia.ai.model;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class CollaborationAnalytics {
    private UUID runId;
    private long totalEvents;
    private long uniqueUsers;
    private Duration sessionDuration;
    private Instant firstEventTime;
    private Instant lastEventTime;
    private Map<String, Long> eventTypeCounts;
    private Map<String, Long> userActivityCounts;
    private List<GraftCheckpoint> mostGraftedCheckpoints;
    private Map<String, UserActivityHeatmap> userActivityHeatmaps;
    private List<ConflictResolution> conflictResolutions;
    private double averageSessionDurationMinutes;
    private double eventsPerMinute;
    
    public CollaborationAnalytics() {
        this.eventTypeCounts = new HashMap<>();
        this.userActivityCounts = new HashMap<>();
        this.mostGraftedCheckpoints = new ArrayList<>();
        this.userActivityHeatmaps = new HashMap<>();
        this.conflictResolutions = new ArrayList<>();
    }
    
    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }
    
    public long getTotalEvents() { return totalEvents; }
    public void setTotalEvents(long totalEvents) { this.totalEvents = totalEvents; }
    
    public long getUniqueUsers() { return uniqueUsers; }
    public void setUniqueUsers(long uniqueUsers) { this.uniqueUsers = uniqueUsers; }
    
    public Duration getSessionDuration() { return sessionDuration; }
    public void setSessionDuration(Duration sessionDuration) { this.sessionDuration = sessionDuration; }
    
    public Instant getFirstEventTime() { return firstEventTime; }
    public void setFirstEventTime(Instant firstEventTime) { this.firstEventTime = firstEventTime; }
    
    public Instant getLastEventTime() { return lastEventTime; }
    public void setLastEventTime(Instant lastEventTime) { this.lastEventTime = lastEventTime; }
    
    public Map<String, Long> getEventTypeCounts() { return eventTypeCounts; }
    public void setEventTypeCounts(Map<String, Long> eventTypeCounts) { this.eventTypeCounts = eventTypeCounts; }
    
    public Map<String, Long> getUserActivityCounts() { return userActivityCounts; }
    public void setUserActivityCounts(Map<String, Long> userActivityCounts) { this.userActivityCounts = userActivityCounts; }
    
    public List<GraftCheckpoint> getMostGraftedCheckpoints() { return mostGraftedCheckpoints; }
    public void setMostGraftedCheckpoints(List<GraftCheckpoint> mostGraftedCheckpoints) { this.mostGraftedCheckpoints = mostGraftedCheckpoints; }
    
    public Map<String, UserActivityHeatmap> getUserActivityHeatmaps() { return userActivityHeatmaps; }
    public void setUserActivityHeatmaps(Map<String, UserActivityHeatmap> userActivityHeatmaps) { this.userActivityHeatmaps = userActivityHeatmaps; }
    
    public List<ConflictResolution> getConflictResolutions() { return conflictResolutions; }
    public void setConflictResolutions(List<ConflictResolution> conflictResolutions) { this.conflictResolutions = conflictResolutions; }
    
    public double getAverageSessionDurationMinutes() { return averageSessionDurationMinutes; }
    public void setAverageSessionDurationMinutes(double averageSessionDurationMinutes) { this.averageSessionDurationMinutes = averageSessionDurationMinutes; }
    
    public double getEventsPerMinute() { return eventsPerMinute; }
    public void setEventsPerMinute(double eventsPerMinute) { this.eventsPerMinute = eventsPerMinute; }
    
    public static class GraftCheckpoint {
        private String checkpointName;
        private long graftCount;
        private List<String> agentNames;
        
        public GraftCheckpoint() {}
        
        public GraftCheckpoint(String checkpointName, long graftCount, List<String> agentNames) {
            this.checkpointName = checkpointName;
            this.graftCount = graftCount;
            this.agentNames = agentNames;
        }
        
        public String getCheckpointName() { return checkpointName; }
        public void setCheckpointName(String checkpointName) { this.checkpointName = checkpointName; }
        
        public long getGraftCount() { return graftCount; }
        public void setGraftCount(long graftCount) { this.graftCount = graftCount; }
        
        public List<String> getAgentNames() { return agentNames; }
        public void setAgentNames(List<String> agentNames) { this.agentNames = agentNames; }
    }
    
    public static class UserActivityHeatmap {
        private String userId;
        private Map<Integer, Long> hourlyActivity;
        private long totalActions;
        
        public UserActivityHeatmap() {
            this.hourlyActivity = new HashMap<>();
        }
        
        public UserActivityHeatmap(String userId, Map<Integer, Long> hourlyActivity, long totalActions) {
            this.userId = userId;
            this.hourlyActivity = hourlyActivity;
            this.totalActions = totalActions;
        }
        
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public Map<Integer, Long> getHourlyActivity() { return hourlyActivity; }
        public void setHourlyActivity(Map<Integer, Long> hourlyActivity) { this.hourlyActivity = hourlyActivity; }
        
        public long getTotalActions() { return totalActions; }
        public void setTotalActions(long totalActions) { this.totalActions = totalActions; }
    }
    
    public static class ConflictResolution {
        private Instant timestamp;
        private String userId1;
        private String userId2;
        private String conflictType;
        private String resolution;
        private String targetNode;
        
        public ConflictResolution() {}
        
        public ConflictResolution(Instant timestamp, String userId1, String userId2, 
                                 String conflictType, String resolution, String targetNode) {
            this.timestamp = timestamp;
            this.userId1 = userId1;
            this.userId2 = userId2;
            this.conflictType = conflictType;
            this.resolution = resolution;
            this.targetNode = targetNode;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        
        public String getUserId1() { return userId1; }
        public void setUserId1(String userId1) { this.userId1 = userId1; }
        
        public String getUserId2() { return userId2; }
        public void setUserId2(String userId2) { this.userId2 = userId2; }
        
        public String getConflictType() { return conflictType; }
        public void setConflictType(String conflictType) { this.conflictType = conflictType; }
        
        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }
        
        public String getTargetNode() { return targetNode; }
        public void setTargetNode(String targetNode) { this.targetNode = targetNode; }
    }
}
