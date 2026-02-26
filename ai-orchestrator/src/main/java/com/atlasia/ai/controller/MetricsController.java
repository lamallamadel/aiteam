package com.atlasia.ai.controller;

import com.atlasia.ai.service.GraftExecutionService;
import com.atlasia.ai.service.WebSocketConnectionMonitor;
import com.atlasia.ai.service.observability.OrchestratorMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final OrchestratorMetrics metrics;
    private final GraftExecutionService graftExecutionService;
    private final WebSocketConnectionMonitor wsMonitor;
    private final MeterRegistry meterRegistry;

    public MetricsController(
            OrchestratorMetrics metrics,
            GraftExecutionService graftExecutionService,
            WebSocketConnectionMonitor wsMonitor) {
        this.metrics = metrics;
        this.graftExecutionService = graftExecutionService;
        this.wsMonitor = wsMonitor;
        this.meterRegistry = metrics.getMeterRegistry();
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("timestamp", System.currentTimeMillis());
        summary.put("totalMetrics", meterRegistry.getMeters().size());
        
        Map<String, Long> metricsByType = meterRegistry.getMeters().stream()
            .collect(Collectors.groupingBy(
                meter -> meter.getId().getType().toString(),
                Collectors.counting()
            ));
        summary.put("metricsByType", metricsByType);
        
        summary.put("activeWebSocketConnections", wsMonitor.getAllConnectionMetrics().stream()
            .filter(cm -> cm.getDisconnectedAt() == null)
            .count());
        
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/circuit-breakers")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerStates() {
        Map<String, Object> states = new HashMap<>();
        
        List<String> agentNames = Arrays.asList(
            "pm-v1", "qualifier-v1", "architect-v1", "tester-v1", "writer-v1"
        );
        
        Map<String, Map<String, Object>> circuitBreakers = new HashMap<>();
        for (String agentName : agentNames) {
            Map<String, Object> state = new HashMap<>();
            state.put("state", graftExecutionService.getCircuitBreakerState(agentName));
            state.put("failureCount", graftExecutionService.getCircuitBreakerFailureCount(agentName));
            state.put("lastFailureTime", graftExecutionService.getCircuitBreakerLastFailureTime(agentName));
            circuitBreakers.put(agentName, state);
        }
        
        states.put("circuitBreakers", circuitBreakers);
        states.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(states);
    }

    @GetMapping("/circuit-breakers/{agentName}/reset")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker(@PathVariable String agentName) {
        graftExecutionService.resetCircuitBreaker(agentName);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("agent", agentName);
        response.put("message", "Circuit breaker reset to CLOSED state");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/websocket/connections")
    public ResponseEntity<Map<String, Object>> getWebSocketConnections() {
        Map<String, Object> response = new HashMap<>();
        
        List<Map<String, Object>> connections = wsMonitor.getAllConnectionMetrics().stream()
            .filter(cm -> cm.getDisconnectedAt() == null)
            .map(cm -> {
                Map<String, Object> conn = new HashMap<>();
                conn.put("sessionId", cm.getSessionId());
                conn.put("userId", cm.getUserId());
                conn.put("runId", cm.getRunId().toString());
                conn.put("connectedAt", cm.getConnectedAt().toString());
                conn.put("messagesSent", cm.getMessagesSent());
                conn.put("messagesReceived", cm.getMessagesReceived());
                conn.put("messageFailures", cm.getMessageFailures());
                conn.put("reconnectionCount", cm.getReconnectionCount());
                conn.put("averageLatency", cm.getAverageLatency());
                conn.put("maxLatency", cm.getMaxLatency());
                conn.put("deliveryRate", cm.getMessageDeliveryRate());
                return conn;
            })
            .collect(Collectors.toList());
        
        response.put("activeConnections", connections);
        response.put("totalActive", connections.size());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> getAvailableMetrics() {
        Map<String, Object> available = new HashMap<>();
        
        List<String> metricNames = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith("orchestrator."))
            .map(meter -> meter.getId().getName())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        Map<String, List<String>> categorized = new LinkedHashMap<>();
        categorized.put("agent", filterByPrefix(metricNames, "orchestrator.agent"));
        categorized.put("graft", filterByPrefix(metricNames, "orchestrator.graft"));
        categorized.put("websocket", filterByPrefix(metricNames, "orchestrator.websocket"));
        categorized.put("jwt", filterByPrefix(metricNames, "orchestrator.jwt"));
        categorized.put("vault", filterByPrefix(metricNames, "orchestrator.vault"));
        categorized.put("cost", filterByPrefix(metricNames, "orchestrator.cost"));
        categorized.put("workflow", filterByPrefix(metricNames, "orchestrator.workflow"));
        categorized.put("llm", filterByPrefix(metricNames, "orchestrator.llm"));
        categorized.put("github", filterByPrefix(metricNames, "orchestrator.github"));
        categorized.put("other", filterByOtherPrefixes(metricNames));
        
        available.put("categories", categorized);
        available.put("totalMetrics", metricNames.size());
        
        return ResponseEntity.ok(available);
    }

    private List<String> filterByPrefix(List<String> names, String prefix) {
        return names.stream()
            .filter(name -> name.startsWith(prefix))
            .collect(Collectors.toList());
    }

    private List<String> filterByOtherPrefixes(List<String> names) {
        Set<String> knownPrefixes = Set.of(
            "orchestrator.agent", "orchestrator.graft", "orchestrator.websocket",
            "orchestrator.jwt", "orchestrator.vault", "orchestrator.cost",
            "orchestrator.workflow", "orchestrator.llm", "orchestrator.github"
        );
        
        return names.stream()
            .filter(name -> knownPrefixes.stream().noneMatch(name::startsWith))
            .collect(Collectors.toList());
    }
}
