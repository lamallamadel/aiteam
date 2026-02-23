package com.atlasia.ai.controller;

import com.atlasia.ai.service.CollaborationService;
import com.atlasia.ai.service.WebSocketConnectionMonitor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/websocket")
public class WebSocketAdminController {

    private final WebSocketConnectionMonitor connectionMonitor;
    private final CollaborationService collaborationService;

    public WebSocketAdminController(WebSocketConnectionMonitor connectionMonitor,
                                   CollaborationService collaborationService) {
        this.connectionMonitor = connectionMonitor;
        this.collaborationService = collaborationService;
    }

    @GetMapping("/connections")
    public ResponseEntity<Map<String, Object>> getAllConnections() {
        Map<UUID, Integer> connectionCounts = connectionMonitor.getAllActiveConnectionCounts();
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalRuns", connectionCounts.size());
        response.put("totalConnections", connectionCounts.values().stream().mapToInt(Integer::intValue).sum());
        response.put("connectionsByRun", connectionCounts);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/connections/{runId}")
    public ResponseEntity<Map<String, Object>> getConnectionsForRun(@PathVariable UUID runId) {
        Set<WebSocketConnectionMonitor.ConnectionInfo> connections = 
            connectionMonitor.getActiveConnections(runId);
        
        List<Map<String, Object>> connectionDetails = connections.stream()
            .map(conn -> {
                Map<String, Object> details = new HashMap<>();
                details.put("sessionId", conn.getSessionId());
                details.put("userId", conn.getUserId());
                details.put("connectedAt", conn.getConnectedAt().toString());
                return details;
            })
            .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("runId", runId);
        response.put("connectionCount", connections.size());
        response.put("connections", connectionDetails);
        response.put("activeUsers", collaborationService.getActiveUsers(runId));
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics")
    public ResponseEntity<List<Map<String, Object>>> getConnectionMetrics() {
        List<WebSocketConnectionMonitor.ConnectionMetrics> allMetrics = 
            connectionMonitor.getAllConnectionMetrics();
        
        List<Map<String, Object>> metricsData = allMetrics.stream()
            .map(metrics -> {
                Map<String, Object> data = new HashMap<>();
                data.put("sessionId", metrics.getSessionId());
                data.put("userId", metrics.getUserId());
                data.put("runId", metrics.getRunId());
                data.put("connectedAt", metrics.getConnectedAt() != null ? 
                    metrics.getConnectedAt().toString() : null);
                data.put("disconnectedAt", metrics.getDisconnectedAt() != null ? 
                    metrics.getDisconnectedAt().toString() : null);
                data.put("reconnectionCount", metrics.getReconnectionCount());
                data.put("messagesSent", metrics.getMessagesSent());
                data.put("messagesReceived", metrics.getMessagesReceived());
                data.put("messageFailures", metrics.getMessageFailures());
                data.put("averageLatencyMs", metrics.getAverageLatency());
                data.put("maxLatencyMs", metrics.getMaxLatency());
                data.put("messageDeliveryRate", metrics.getMessageDeliveryRate());
                data.put("lastActivity", metrics.getLastActivity().toString());
                return data;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(metricsData);
    }

    @GetMapping("/metrics/{sessionId}")
    public ResponseEntity<Map<String, Object>> getConnectionMetricsBySession(
            @PathVariable String sessionId) {
        WebSocketConnectionMonitor.ConnectionMetrics metrics = 
            connectionMonitor.getConnectionMetrics(sessionId);
        
        if (metrics == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", metrics.getSessionId());
        data.put("userId", metrics.getUserId());
        data.put("runId", metrics.getRunId());
        data.put("connectedAt", metrics.getConnectedAt() != null ? 
            metrics.getConnectedAt().toString() : null);
        data.put("disconnectedAt", metrics.getDisconnectedAt() != null ? 
            metrics.getDisconnectedAt().toString() : null);
        data.put("reconnectionCount", metrics.getReconnectionCount());
        data.put("messagesSent", metrics.getMessagesSent());
        data.put("messagesReceived", metrics.getMessagesReceived());
        data.put("messageFailures", metrics.getMessageFailures());
        data.put("averageLatencyMs", metrics.getAverageLatency());
        data.put("maxLatencyMs", metrics.getMaxLatency());
        data.put("messageDeliveryRate", metrics.getMessageDeliveryRate());
        data.put("lastActivity", metrics.getLastActivity().toString());
        data.put("connectionQuality", calculateQualityScore(metrics));
        data.put("healthStatus", determineHealthStatus(metrics));
        
        return ResponseEntity.ok(data);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getConnectionHealth() {
        List<WebSocketConnectionMonitor.ConnectionMetrics> allMetrics = 
            connectionMonitor.getAllConnectionMetrics();
        
        int totalConnections = allMetrics.size();
        int healthyConnections = 0;
        int degradedConnections = 0;
        int unhealthyConnections = 0;
        double totalQuality = 0.0;
        
        for (WebSocketConnectionMonitor.ConnectionMetrics metrics : allMetrics) {
            String health = determineHealthStatus(metrics);
            switch (health) {
                case "HEALTHY" -> healthyConnections++;
                case "DEGRADED" -> degradedConnections++;
                case "UNHEALTHY" -> unhealthyConnections++;
            }
            totalQuality += calculateQualityScore(metrics);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalConnections", totalConnections);
        response.put("healthyConnections", healthyConnections);
        response.put("degradedConnections", degradedConnections);
        response.put("unhealthyConnections", unhealthyConnections);
        response.put("averageQualityScore", totalConnections > 0 ? totalQuality / totalConnections : 0.0);
        response.put("overallHealth", determineOverallHealth(healthyConnections, degradedConnections, 
            unhealthyConnections, totalConnections));
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cleanup-stale")
    public ResponseEntity<Map<String, String>> cleanupStaleMetrics(
            @RequestParam(defaultValue = "3600000") long maxAgeMs) {
        connectionMonitor.cleanupStaleMetrics(maxAgeMs);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Cleaned up metrics older than " + maxAgeMs + "ms");
        
        return ResponseEntity.ok(response);
    }

    private double calculateQualityScore(WebSocketConnectionMonitor.ConnectionMetrics metrics) {
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

    private String determineHealthStatus(WebSocketConnectionMonitor.ConnectionMetrics metrics) {
        double quality = calculateQualityScore(metrics);
        if (quality >= 80.0) return "HEALTHY";
        if (quality >= 50.0) return "DEGRADED";
        return "UNHEALTHY";
    }

    private String determineOverallHealth(int healthy, int degraded, int unhealthy, int total) {
        if (total == 0) return "UNKNOWN";
        double healthyPercentage = (double) healthy / total;
        if (healthyPercentage >= 0.9) return "HEALTHY";
        if (healthyPercentage >= 0.6) return "DEGRADED";
        return "UNHEALTHY";
    }
}
