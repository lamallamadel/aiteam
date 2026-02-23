# WebSocket Connection Health Monitoring & Resilience

This document describes the comprehensive WebSocket health monitoring and resilience features implemented for the Atlasia AI Orchestrator collaboration system.

## Overview

The WebSocket collaboration system includes production-ready monitoring, resilience, and observability features to ensure reliable real-time collaboration even under adverse network conditions.

## Features Implemented

### 1. Connection Quality Metrics

**Client-Side Tracking:**
- Round-trip message latency (measured via PING/PONG every 10 seconds)
- Reconnection attempt counter
- Message delivery success rate
- Connection quality score (0-100)
- Health status (HEALTHY/DEGRADED/UNHEALTHY)

**Server-Side Tracking:**
- Per-session connection metrics
- Message throughput (in/out)
- Message failure tracking
- Latency histograms
- Active connection counts per run

### 2. Client-Side Resilience

**Message Queuing:**
- Messages are automatically queued when connection is lost
- Queued messages are replayed in order upon reconnection
- Retry logic with exponential backoff (up to 3 attempts per message)
- Queue size exposed to UI for user visibility

**Automatic Fallback:**
- Falls back to HTTP polling after 5 consecutive WebSocket failures
- HTTP polling endpoint: `GET /api/runs/{runId}/collaboration/poll`
- Polls every 2 seconds with sequence number tracking
- Automatically switches back to WebSocket when available

**Sequence Tracking:**
- Client tracks last received sequence number
- Requests missed messages on reconnection
- Ensures no events are lost during temporary disconnections

### 3. Server-Side Persistence

**Critical Event Storage:**
- GRAFT, PRUNE, and FLAG events automatically persisted
- Stored in `persisted_collaboration_messages` table
- Last 1000 messages kept per run (configurable)
- Sequence numbers for ordered replay

**Replay Endpoints:**
- `GET /api/runs/{runId}/collaboration/replay` - Get all critical events
- `GET /api/runs/{runId}/collaboration/replay?fromSequence={seq}` - Get events after sequence
- Used by late-joiners and reconnecting clients

### 4. Admin Monitoring Endpoints

**Connection Management:**
```bash
# List all active connections
GET /api/admin/websocket/connections

# Get connections for specific run
GET /api/admin/websocket/connections/{runId}

# Get detailed metrics for all sessions
GET /api/admin/websocket/metrics

# Get metrics for specific session
GET /api/admin/websocket/metrics/{sessionId}

# Get overall health status
GET /api/admin/websocket/health

# Cleanup stale metrics
POST /api/admin/websocket/cleanup-stale?maxAgeMs=3600000
```

**Response Example:**
```json
{
  "sessionId": "abc123",
  "userId": "user_xyz",
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "connectedAt": "2024-01-15T10:30:00Z",
  "reconnectionCount": 2,
  "messagesSent": 145,
  "messagesReceived": 132,
  "messageFailures": 3,
  "averageLatencyMs": 85.5,
  "maxLatencyMs": 250,
  "messageDeliveryRate": 0.98,
  "connectionQuality": 87.5,
  "healthStatus": "HEALTHY"
}
```

### 5. Prometheus Metrics

All metrics are exposed at `/actuator/prometheus`:

**Counters:**
- `orchestrator_websocket_connections_total` - Total connections
- `orchestrator_websocket_disconnections_total` - Total disconnections
- `orchestrator_websocket_reconnections_total` - Reconnection attempts
- `orchestrator_websocket_messages_in_total` - Incoming messages
- `orchestrator_websocket_messages_out_total` - Outgoing messages
- `orchestrator_websocket_message_failures_total` - Failed deliveries
- `orchestrator_websocket_fallback_http_total` - HTTP fallback events

**Histograms:**
- `orchestrator_websocket_message_latency` - Round-trip latency

**Gauges:**
- `orchestrator_websocket_connection_quality` - Quality score (0-100)
- `orchestrator_websocket_message_delivery_rate` - Delivery rate (0-1)

### 6. Grafana Dashboard

**Location:** `infra/grafana-websocket-dashboard.json`

**Panels Include:**
- Active connections graph
- Connection/disconnection rates
- Message throughput (in/out)
- Latency percentiles (p50, p90, p95, p99)
- Message delivery rate over time
- Reconnection frequency
- HTTP polling fallback rate
- Message failure rate
- Connection quality gauge
- Health status distribution

**Auto-refresh:** 5 seconds

### 7. Alerting Rules

**Location:** `infra/websocket-alerts.yml`

**Alerts Configured:**
- High latency (>500ms p95)
- Critical latency (>1000ms p95)
- Low delivery rate (<95%)
- Critical delivery rate (<80%)
- High reconnection rate
- HTTP fallback active
- High message failure rate
- Degraded connection quality
- Unhealthy connection quality
- No active connections
- Connection spike detected

### 8. UI Health Indicator

**Component:** `ConnectionHealthComponent`

**Displays:**
- Visual health indicator (green/yellow/red)
- Real-time latency
- Message delivery rate
- Reconnection count
- Quality score
- Queued messages (when disconnected)
- Fallback mode indicator

**Location:** Visible at top of run detail page when collaboration is active

## Architecture

### Backend Components

1. **WebSocketConnectionMonitor** (`ai-orchestrator/src/main/java/com/atlasia/ai/service/WebSocketConnectionMonitor.java`)
   - Tracks active connections per run
   - Records metrics per session
   - Scheduled quality score calculation (every 30s)

2. **CollaborationService** (`ai-orchestrator/src/main/java/com/atlasia/ai/service/CollaborationService.java`)
   - Handles PING/PONG for latency measurement
   - Persists critical collaboration events
   - Manages message sequence numbers

3. **WebSocketAdminController** (`ai-orchestrator/src/main/java/com/atlasia/ai/controller/WebSocketAdminController.java`)
   - Admin endpoints for monitoring
   - Connection health aggregation
   - Metrics cleanup

4. **OrchestratorMetrics** (`ai-orchestrator/src/main/java/com/atlasia/ai/service/observability/OrchestratorMetrics.java`)
   - Prometheus metric definitions
   - Recording methods for all WebSocket events

### Frontend Components

1. **CollaborationWebSocketService** (`frontend/src/app/services/collaboration-websocket.service.ts`)
   - WebSocket connection management
   - Message queuing and replay
   - HTTP polling fallback
   - Automatic latency measurement
   - Health score calculation

2. **ConnectionHealthComponent** (`frontend/src/app/components/connection-health.component.ts`)
   - UI health indicator
   - Metrics display
   - Status visualization

3. **WorkflowStreamStore** (`frontend/src/app/services/workflow-stream.store.ts`)
   - Health state management
   - Observable streams for health data

## Connection Quality Scoring

**Formula:**
```
Quality Score = (Latency Score × 0.4) + (Reconnection Score × 0.3) + (Delivery Rate × 100 × 0.3)
```

**Latency Score:**
- 0-50ms: 100 points
- 50-100ms: 90 points
- 100-200ms: 75 points
- 200-500ms: 50 points
- 500-1000ms: 25 points
- >1000ms: 10 points

**Reconnection Score:**
- 0: 100 points
- 1: 80 points
- 2: 60 points
- 3-5: 40 points
- 6-10: 20 points
- >10: 5 points

**Health Status:**
- HEALTHY: Score ≥ 80
- DEGRADED: Score 50-79
- UNHEALTHY: Score < 50

## Database Schema

**Table:** `persisted_collaboration_messages`

```sql
CREATE TABLE persisted_collaboration_messages (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    message_data JSONB,
    timestamp TIMESTAMP NOT NULL,
    sequence_number BIGINT NOT NULL,
    is_critical BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_run FOREIGN KEY (run_id) REFERENCES ai_run(id)
);

CREATE INDEX idx_run_id_sequence ON persisted_collaboration_messages(run_id, sequence_number DESC);
CREATE INDEX idx_critical_messages ON persisted_collaboration_messages(run_id, is_critical) WHERE is_critical = TRUE;
CREATE INDEX idx_sequence_after ON persisted_collaboration_messages(run_id, sequence_number);
```

## Usage Examples

### Monitoring Connection Health (Frontend)

```typescript
import { CollaborationWebSocketService } from './services/collaboration-websocket.service';

// Subscribe to health updates
this.collaborationService.health$.subscribe(health => {
  console.log('Latency:', health.latency, 'ms');
  console.log('Quality:', health.qualityScore, '/100');
  console.log('Status:', health.healthStatus);
  
  if (health.healthStatus === 'DEGRADED') {
    this.showWarning('Connection quality degraded');
  }
});

// Check fallback status
if (this.collaborationService.isUsingFallback()) {
  console.log('Using HTTP polling fallback');
}

// Check queue size
const queued = this.collaborationService.getQueuedMessageCount();
console.log(`${queued} messages queued`);
```

### Monitoring All Connections (Admin)

```bash
# Get overall health
curl http://localhost:8080/api/admin/websocket/health

# Response:
{
  "totalConnections": 15,
  "healthyConnections": 12,
  "degradedConnections": 2,
  "unhealthyConnections": 1,
  "averageQualityScore": 82.5,
  "overallHealth": "HEALTHY"
}
```

### Setting Up Alerting

1. Configure Prometheus to load rules:
```yaml
rule_files:
  - /etc/prometheus/rules/websocket-alerts.yml
```

2. Configure Alertmanager for notifications
3. Import Grafana dashboard from `infra/grafana-websocket-dashboard.json`

## Performance Characteristics

- **Message Queuing:** No limit on queue size, but older messages may be dropped on reconnect if exceeding 1000
- **Persistence:** Last 1000 messages per run kept in database
- **Latency Measurement:** Every 10 seconds per connection
- **Metrics Update:** Connection quality recalculated every 30 seconds
- **Cleanup:** Stale metrics can be cleaned via admin endpoint

## Best Practices

1. **Monitor the Grafana dashboard** regularly for anomalies
2. **Set up alerting** for critical metrics (latency >1s, delivery <80%)
3. **Cleanup stale metrics** periodically (recommended: daily)
4. **Review health endpoint** to identify problematic connections
5. **Check fallback usage** as indicator of WebSocket issues
6. **Monitor queue sizes** - high queues indicate connectivity problems

## Troubleshooting

See `docs/COLLABORATION.md` for detailed troubleshooting guide.

## Files Changed

### Backend
- `ai-orchestrator/src/main/java/com/atlasia/ai/controller/CollaborationController.java` - Added ping handling
- `ai-orchestrator/src/main/java/com/atlasia/ai/controller/WebSocketAdminController.java` - Enhanced with health endpoint
- `ai-orchestrator/src/main/java/com/atlasia/ai/service/CollaborationService.java` - Added ping/pong handling
- `ai-orchestrator/src/main/java/com/atlasia/ai/service/WebSocketConnectionMonitor.java` - Added quality calculation
- `ai-orchestrator/src/main/java/com/atlasia/ai/service/observability/OrchestratorMetrics.java` - Added quality metrics
- `ai-orchestrator/src/main/resources/db/migration/V12__create_persisted_collaboration_messages.sql` - Enhanced indexes

### Frontend
- `frontend/src/app/services/collaboration-websocket.service.ts` - Added latency measurement, health tracking
- `frontend/src/app/components/connection-health.component.ts` - New health indicator component
- `frontend/src/app/services/workflow-stream.store.ts` - Already integrated

### Monitoring
- `infra/grafana-websocket-dashboard.json` - Comprehensive dashboard
- `infra/websocket-alerts.yml` - Prometheus alerting rules

### Documentation
- `docs/COLLABORATION.md` - Updated with monitoring details
- `docs/WEBSOCKET_MONITORING.md` - This file
- `AGENTS.md` - Updated with feature summary
