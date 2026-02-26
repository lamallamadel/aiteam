package com.atlasia.ai.service;

import com.atlasia.ai.service.observability.OrchestratorMetrics;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketConnectionMonitor {

    private final OrchestratorMetrics metrics;
    
    private final Map<UUID, Set<ConnectionInfo>> activeConnectionsByRun = new ConcurrentHashMap<>();
    private final Map<String, ConnectionMetrics> connectionMetrics = new ConcurrentHashMap<>();

    public WebSocketConnectionMonitor(OrchestratorMetrics metrics) {
        this.metrics = metrics;
        metrics.registerWebSocketConnectionPoolGauges(this);
    }

    public void recordConnection(UUID runId, String sessionId, String userId) {
        ConnectionInfo info = new ConnectionInfo(sessionId, userId, Instant.now());
        activeConnectionsByRun.computeIfAbsent(runId, k -> ConcurrentHashMap.newKeySet()).add(info);
        
        ConnectionMetrics connMetrics = connectionMetrics.computeIfAbsent(sessionId, 
            k -> new ConnectionMetrics(sessionId, userId, runId));
        connMetrics.recordConnect(Instant.now());
        
        metrics.recordWebSocketConnection();
    }

    public void recordDisconnection(UUID runId, String sessionId) {
        Set<ConnectionInfo> connections = activeConnectionsByRun.get(runId);
        if (connections != null) {
            connections.removeIf(c -> c.sessionId.equals(sessionId));
            if (connections.isEmpty()) {
                activeConnectionsByRun.remove(runId);
            }
        }
        
        ConnectionMetrics connMetrics = connectionMetrics.get(sessionId);
        if (connMetrics != null) {
            connMetrics.recordDisconnect(Instant.now());
        }
        
        metrics.recordWebSocketDisconnection();
    }

    public void recordReconnection(String sessionId) {
        ConnectionMetrics connMetrics = connectionMetrics.get(sessionId);
        if (connMetrics != null) {
            connMetrics.incrementReconnections();
        }
        metrics.recordWebSocketReconnection();
    }

    public void recordMessageSent(String sessionId) {
        ConnectionMetrics connMetrics = connectionMetrics.get(sessionId);
        if (connMetrics != null) {
            connMetrics.incrementMessagesSent();
        }
        metrics.recordWebSocketMessageOut();
    }

    public void recordMessageReceived(String sessionId) {
        ConnectionMetrics connMetrics = connectionMetrics.get(sessionId);
        if (connMetrics != null) {
            connMetrics.incrementMessagesReceived();
        }
        metrics.recordWebSocketMessageIn();
    }

    public void recordMessageFailure(String sessionId) {
        ConnectionMetrics connMetrics = connectionMetrics.get(sessionId);
        if (connMetrics != null) {
            connMetrics.incrementMessageFailures();
        }
        metrics.recordWebSocketMessageFailure();
    }

    public void recordMessageLatency(String sessionId, long latencyMs) {
        ConnectionMetrics connMetrics = connectionMetrics.get(sessionId);
        if (connMetrics != null) {
            connMetrics.recordLatency(latencyMs);
        }
        metrics.recordWebSocketMessageLatency(latencyMs);
    }

    public Set<ConnectionInfo> getActiveConnections(UUID runId) {
        return activeConnectionsByRun.getOrDefault(runId, Collections.emptySet());
    }

    public int getConnectionCount(UUID runId) {
        return activeConnectionsByRun.getOrDefault(runId, Collections.emptySet()).size();
    }

    public Map<UUID, Integer> getAllActiveConnectionCounts() {
        Map<UUID, Integer> counts = new HashMap<>();
        activeConnectionsByRun.forEach((runId, connections) -> 
            counts.put(runId, connections.size())
        );
        return counts;
    }

    public ConnectionMetrics getConnectionMetrics(String sessionId) {
        return connectionMetrics.get(sessionId);
    }

    public List<ConnectionMetrics> getAllConnectionMetrics() {
        return new ArrayList<>(connectionMetrics.values());
    }

    public void cleanupStaleMetrics(long maxAgeMs) {
        Instant cutoff = Instant.now().minusMillis(maxAgeMs);
        connectionMetrics.entrySet().removeIf(entry -> 
            entry.getValue().getLastActivity().isBefore(cutoff)
        );
    }

    @Scheduled(fixedRate = 30000)
    public void updateConnectionQualityMetrics() {
        connectionMetrics.values().forEach(connMetrics -> {
            double quality = calculateConnectionQuality(connMetrics);
            metrics.recordWebSocketConnectionQuality(quality);
            
            double deliveryRate = connMetrics.getMessageDeliveryRate();
            metrics.recordWebSocketMessageDeliveryRate(deliveryRate);
        });
    }

    private double calculateConnectionQuality(ConnectionMetrics metrics) {
        double latencyScore = calculateLatencyScore(metrics.getAverageLatency());
        double reconnectionScore = calculateReconnectionScore(metrics.getReconnectionCount());
        double deliveryScore = metrics.getMessageDeliveryRate() * 100.0;
        
        return (latencyScore * 0.4) + (reconnectionScore * 0.3) + (deliveryScore * 0.3);
    }

    private double calculateLatencyScore(double avgLatency) {
        if (avgLatency == 0) return 100.0;
        if (avgLatency < 50) return 100.0;
        if (avgLatency < 100) return 90.0;
        if (avgLatency < 200) return 75.0;
        if (avgLatency < 500) return 50.0;
        if (avgLatency < 1000) return 25.0;
        return 10.0;
    }

    private double calculateReconnectionScore(long reconnectionCount) {
        if (reconnectionCount == 0) return 100.0;
        if (reconnectionCount == 1) return 80.0;
        if (reconnectionCount == 2) return 60.0;
        if (reconnectionCount <= 5) return 40.0;
        if (reconnectionCount <= 10) return 20.0;
        return 5.0;
    }

    public static class ConnectionInfo {
        private final String sessionId;
        private final String userId;
        private final Instant connectedAt;

        public ConnectionInfo(String sessionId, String userId, Instant connectedAt) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.connectedAt = connectedAt;
        }

        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public Instant getConnectedAt() { return connectedAt; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConnectionInfo that = (ConnectionInfo) o;
            return Objects.equals(sessionId, that.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId);
        }
    }

    public static class ConnectionMetrics {
        private final String sessionId;
        private final String userId;
        private final UUID runId;
        private Instant connectedAt;
        private Instant disconnectedAt;
        private long reconnectionCount = 0;
        private long messagesSent = 0;
        private long messagesReceived = 0;
        private long messageFailures = 0;
        private final List<Long> latencies = new ArrayList<>();
        private Instant lastActivity;

        public ConnectionMetrics(String sessionId, String userId, UUID runId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.runId = runId;
            this.lastActivity = Instant.now();
        }

        public void recordConnect(Instant timestamp) {
            this.connectedAt = timestamp;
            this.lastActivity = timestamp;
        }

        public void recordDisconnect(Instant timestamp) {
            this.disconnectedAt = timestamp;
            this.lastActivity = timestamp;
        }

        public void incrementReconnections() {
            this.reconnectionCount++;
            this.lastActivity = Instant.now();
        }

        public void incrementMessagesSent() {
            this.messagesSent++;
            this.lastActivity = Instant.now();
        }

        public void incrementMessagesReceived() {
            this.messagesReceived++;
            this.lastActivity = Instant.now();
        }

        public void incrementMessageFailures() {
            this.messageFailures++;
            this.lastActivity = Instant.now();
        }

        public void recordLatency(long latencyMs) {
            if (latencies.size() >= 100) {
                latencies.remove(0);
            }
            latencies.add(latencyMs);
            this.lastActivity = Instant.now();
        }

        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
        public UUID getRunId() { return runId; }
        public Instant getConnectedAt() { return connectedAt; }
        public Instant getDisconnectedAt() { return disconnectedAt; }
        public long getReconnectionCount() { return reconnectionCount; }
        public long getMessagesSent() { return messagesSent; }
        public long getMessagesReceived() { return messagesReceived; }
        public long getMessageFailures() { return messageFailures; }
        public Instant getLastActivity() { return lastActivity; }

        public double getAverageLatency() {
            if (latencies.isEmpty()) return 0.0;
            return latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }

        public long getMaxLatency() {
            if (latencies.isEmpty()) return 0L;
            return latencies.stream().mapToLong(Long::longValue).max().orElse(0L);
        }

        public double getMessageDeliveryRate() {
            long total = messagesSent + messageFailures;
            if (total == 0) return 1.0;
            return (double) messagesSent / total;
        }
    }
}
