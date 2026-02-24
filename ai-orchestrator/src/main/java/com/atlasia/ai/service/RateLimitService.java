package com.atlasia.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);
    
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final long WINDOW_SIZE_SECONDS = 60;
    
    private final Map<String, Queue<Instant>> requestTimestamps = new ConcurrentHashMap<>();

    public boolean allowRequest(UUID userId, String operation) {
        String key = userId.toString() + ":" + operation;
        
        Queue<Instant> timestamps = requestTimestamps.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SIZE_SECONDS);
        
        timestamps.removeIf(timestamp -> timestamp.isBefore(windowStart));
        
        if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            logger.warn("Rate limit exceeded for user {} on operation {}: {} requests in last {} seconds", 
                userId, operation, timestamps.size(), WINDOW_SIZE_SECONDS);
            return false;
        }
        
        timestamps.add(now);
        return true;
    }

    public int getRemainingRequests(UUID userId, String operation) {
        String key = userId.toString() + ":" + operation;
        Queue<Instant> timestamps = requestTimestamps.get(key);
        
        if (timestamps == null) {
            return MAX_REQUESTS_PER_MINUTE;
        }
        
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(WINDOW_SIZE_SECONDS);
        
        long recentRequests = timestamps.stream()
            .filter(timestamp -> timestamp.isAfter(windowStart))
            .count();
        
        return Math.max(0, MAX_REQUESTS_PER_MINUTE - (int) recentRequests);
    }

    public void cleanup() {
        requestTimestamps.entrySet().removeIf(entry -> {
            Queue<Instant> timestamps = entry.getValue();
            Instant now = Instant.now();
            Instant windowStart = now.minusSeconds(WINDOW_SIZE_SECONDS);
            timestamps.removeIf(timestamp -> timestamp.isBefore(windowStart));
            return timestamps.isEmpty();
        });
    }
}
