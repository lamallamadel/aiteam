package com.atlasia.ai.service;

import com.atlasia.ai.model.*;
import com.atlasia.ai.persistence.CollaborationEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimeTravelService {
    
    private final CollaborationEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    
    public TimeTravelService(CollaborationEventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }
    
    public List<TimeTravelSnapshot> getEventHistory(UUID runId) {
        List<CollaborationEventEntity> events = eventRepository.findByRunIdOrderByTimestampAsc(runId);
        
        return events.stream()
            .map(this::toTimeTravelSnapshot)
            .collect(Collectors.toList());
    }
    
    public List<TimeTravelSnapshot> getEventHistoryInRange(UUID runId, Instant start, Instant end) {
        List<CollaborationEventEntity> events = 
            eventRepository.findByRunIdAndTimestampBetweenOrderByTimestampAsc(runId, start, end);
        
        return events.stream()
            .map(this::toTimeTravelSnapshot)
            .collect(Collectors.toList());
    }
    
    public CollaborationAnalytics getAnalytics(UUID runId) {
        List<CollaborationEventEntity> events = eventRepository.findByRunIdOrderByTimestampAsc(runId);
        
        if (events.isEmpty()) {
            return new CollaborationAnalytics();
        }
        
        CollaborationAnalytics analytics = new CollaborationAnalytics();
        analytics.setRunId(runId);
        analytics.setTotalEvents(events.size());
        
        Set<String> uniqueUsers = new HashSet<>();
        Map<String, Long> eventTypeCounts = new HashMap<>();
        Map<String, Long> userActivityCounts = new HashMap<>();
        Map<String, List<String>> graftCheckpoints = new HashMap<>();
        Map<String, Map<Integer, Long>> userHourlyActivity = new HashMap<>();
        
        Instant firstEvent = events.get(0).getTimestamp();
        Instant lastEvent = events.get(events.size() - 1).getTimestamp();
        
        analytics.setFirstEventTime(firstEvent);
        analytics.setLastEventTime(lastEvent);
        
        Duration sessionDuration = Duration.between(firstEvent, lastEvent);
        analytics.setSessionDuration(sessionDuration);
        analytics.setAverageSessionDurationMinutes(sessionDuration.toMinutes());
        
        if (sessionDuration.toMinutes() > 0) {
            analytics.setEventsPerMinute((double) events.size() / sessionDuration.toMinutes());
        }
        
        for (CollaborationEventEntity event : events) {
            uniqueUsers.add(event.getUserId());
            
            eventTypeCounts.merge(event.getEventType(), 1L, Long::sum);
            userActivityCounts.merge(event.getUserId(), 1L, Long::sum);
            
            if ("GRAFT".equals(event.getEventType())) {
                try {
                    Map<String, Object> data = objectMapper.readValue(
                        event.getEventData(), new TypeReference<Map<String, Object>>() {});
                    String checkpoint = (String) data.get("after");
                    String agentName = (String) data.get("agentName");
                    
                    graftCheckpoints.computeIfAbsent(checkpoint, k -> new ArrayList<>()).add(agentName);
                } catch (Exception e) {
                }
            }
            
            int hour = event.getTimestamp().atZone(ZoneId.systemDefault()).getHour();
            userHourlyActivity
                .computeIfAbsent(event.getUserId(), k -> new HashMap<>())
                .merge(hour, 1L, Long::sum);
        }
        
        analytics.setUniqueUsers(uniqueUsers.size());
        analytics.setEventTypeCounts(eventTypeCounts);
        analytics.setUserActivityCounts(userActivityCounts);
        
        List<CollaborationAnalytics.GraftCheckpoint> mostGrafted = graftCheckpoints.entrySet().stream()
            .map(e -> new CollaborationAnalytics.GraftCheckpoint(
                e.getKey(), e.getValue().size(), e.getValue()))
            .sorted((a, b) -> Long.compare(b.getGraftCount(), a.getGraftCount()))
            .limit(10)
            .collect(Collectors.toList());
        analytics.setMostGraftedCheckpoints(mostGrafted);
        
        Map<String, CollaborationAnalytics.UserActivityHeatmap> heatmaps = new HashMap<>();
        for (Map.Entry<String, Map<Integer, Long>> entry : userHourlyActivity.entrySet()) {
            String userId = entry.getKey();
            long totalActions = userActivityCounts.getOrDefault(userId, 0L);
            heatmaps.put(userId, new CollaborationAnalytics.UserActivityHeatmap(
                userId, entry.getValue(), totalActions));
        }
        analytics.setUserActivityHeatmaps(heatmaps);
        
        return analytics;
    }
    
    public String exportEventsAsJson(UUID runId) {
        List<CollaborationEventEntity> events = eventRepository.findByRunIdOrderByTimestampAsc(runId);
        
        try {
            Map<String, Object> export = new HashMap<>();
            export.put("runId", runId);
            export.put("exportedAt", Instant.now());
            export.put("totalEvents", events.size());
            export.put("events", events.stream()
                .map(this::eventToMap)
                .collect(Collectors.toList()));
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export as JSON", e);
        }
    }
    
    public String exportEventsAsCsv(UUID runId) {
        List<CollaborationEventEntity> events = eventRepository.findByRunIdOrderByTimestampAsc(runId);
        
        StringBuilder csv = new StringBuilder();
        csv.append("Event ID,Run ID,User ID,Event Type,Timestamp,Event Data\n");
        
        for (CollaborationEventEntity event : events) {
            csv.append(escapeCsv(event.getId().toString())).append(",");
            csv.append(escapeCsv(event.getRunId().toString())).append(",");
            csv.append(escapeCsv(event.getUserId())).append(",");
            csv.append(escapeCsv(event.getEventType())).append(",");
            csv.append(escapeCsv(event.getTimestamp().toString())).append(",");
            csv.append(escapeCsv(event.getEventData())).append("\n");
        }
        
        return csv.toString();
    }
    
    private TimeTravelSnapshot toTimeTravelSnapshot(CollaborationEventEntity event) {
        Map<String, Object> stateBefore = parseJson(event.getStateBefore());
        Map<String, Object> stateAfter = parseJson(event.getStateAfter());
        Map<String, Object> diff = computeDiff(stateBefore, stateAfter);
        String description = generateDescription(event);
        
        return new TimeTravelSnapshot(
            event.getId(),
            event.getUserId(),
            event.getEventType(),
            event.getTimestamp(),
            stateBefore,
            stateAfter,
            diff,
            description
        );
    }
    
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
    
    private Map<String, Object> computeDiff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> diff = new HashMap<>();
        
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(before.keySet());
        allKeys.addAll(after.keySet());
        
        for (String key : allKeys) {
            Object beforeValue = before.get(key);
            Object afterValue = after.get(key);
            
            if (!Objects.equals(beforeValue, afterValue)) {
                Map<String, Object> change = new HashMap<>();
                change.put("before", beforeValue);
                change.put("after", afterValue);
                diff.put(key, change);
            }
        }
        
        return diff;
    }
    
    private String generateDescription(CollaborationEventEntity event) {
        try {
            Map<String, Object> data = objectMapper.readValue(
                event.getEventData(), new TypeReference<Map<String, Object>>() {});
            
            switch (event.getEventType()) {
                case "GRAFT":
                    return String.format("User %s grafted %s after %s", 
                        event.getUserId(), data.get("agentName"), data.get("after"));
                case "PRUNE":
                    return String.format("User %s pruned step %s", 
                        event.getUserId(), data.get("stepId"));
                case "FLAG":
                    return String.format("User %s flagged step %s", 
                        event.getUserId(), data.get("stepId"));
                case "USER_JOIN":
                    return String.format("User %s joined the session", event.getUserId());
                case "USER_LEAVE":
                    return String.format("User %s left the session", event.getUserId());
                case "CURSOR_MOVE":
                    return String.format("User %s moved cursor to %s", 
                        event.getUserId(), data.get("nodeId"));
                default:
                    return event.getEventType();
            }
        } catch (Exception e) {
            return event.getEventType();
        }
    }
    
    private Map<String, Object> eventToMap(CollaborationEventEntity event) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", event.getId());
        map.put("userId", event.getUserId());
        map.put("eventType", event.getEventType());
        map.put("timestamp", event.getTimestamp());
        map.put("eventData", parseJson(event.getEventData()));
        map.put("stateBefore", parseJson(event.getStateBefore()));
        map.put("stateAfter", parseJson(event.getStateAfter()));
        return map;
    }
    
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
