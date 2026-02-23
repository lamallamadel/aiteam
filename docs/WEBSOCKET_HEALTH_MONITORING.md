# WebSocket Health Monitoring & Resilience

This document describes the WebSocket connection health monitoring and resilience improvements implemented for the real-time collaboration system.

## Features

### 1. Connection Quality Metrics

The system tracks comprehensive metrics for all WebSocket connections:

- **Latency**: Round-trip message latency (p50, p95, p99)
- **Reconnection Frequency**: Number of reconnection attempts per connection
- **Message Delivery Rate**: Percentage of successfully delivered messages
- **Active Connections**: Number of active WebSocket connections per run
- **Throughput**: Messages sent/received per second

#### Prometheus Metrics

All metrics are exposed via Prometheus at `/actuator/prometheus`:

```
orchestrator_websocket_connections_total
orchestrator_websocket_disconnections_total
orchestrator_websocket_reconnections_total
orchestrator_websocket_messages_in_total
orchestrator_websocket_messages_out_total
orchestrator_websocket_message_failures_total
orchestrator_websocket_message_latency
orchestrator_websocket_fallback_http_total
```

#### Grafana Dashboard

Import the dashboard at `infra/grafana-websocket-dashboard.json` to visualize:
- Active connections over time
- Connection/disconnection rates
- Message throughput
- Latency percentiles
- Reconnection frequency
- HTTP fallback rate

### 2. Client-Side Message Queuing

When the WebSocket connection is lost, messages are queued locally and replayed upon reconnection:

- Messages queued during disconnection
- Automatic retry with exponential backoff (up to 3 attempts)
- Persistent queue survives temporary disconnections
- Visual indicator shows number of queued messages

### 3. Server-Side Message Persistence

Critical collaboration events (GRAFT, PRUNE, FLAG) are persisted to the database:

- **Table**: `persisted_collaboration_messages`
- **Retention**: Last 1000 messages per run
- **Sequence Numbers**: Monotonically increasing per run
- **Critical Flag**: Marks events that must be replayed

#### Late-Joiner Replay

When a user joins a collaboration session, they receive:
1. All critical events (grafts, prunes, flags)
2. Current presence state (active users, cursor positions)

### 4. HTTP Polling Fallback

If WebSocket fails repeatedly (5 reconnection attempts), the system automatically falls back to HTTP polling:

- Polls every 2 seconds
- Fetches messages after last received sequence number
- Uses same event format as WebSocket
- Visual indicator shows fallback mode

#### Polling Endpoint

```
GET /api/runs/{runId}/collaboration/poll?afterSequence={seq}
```

Response:
```json
{
  "runId": "uuid",
  "events": [...],
  "activeUsers": [...],
  "cursorPositions": {...}
}
```

### 5. Message Replay API

Retrieve persisted messages for a run:

```
GET /api/runs/{runId}/collaboration/replay?fromSequence={seq}&limit=100
```

Response:
```json
{
  "runId": "uuid",
  "events": [
    {
      "eventType": "GRAFT",
      "userId": "user_123",
      "timestamp": 1234567890,
      "sequenceNumber": 42,
      "data": {...},
      "isCritical": true
    }
  ],
  "totalEvents": 50
}
```

### 6. Admin Monitoring Endpoints

#### Get All Active Connections

```
GET /api/admin/websocket/connections
```

Response:
```json
{
  "totalRuns": 5,
  "totalConnections": 12,
  "connectionsByRun": {
    "run-uuid-1": 3,
    "run-uuid-2": 2
  }
}
```

#### Get Connections for Specific Run

```
GET /api/admin/websocket/connections/{runId}
```

Response:
```json
{
  "runId": "uuid",
  "connectionCount": 3,
  "connections": [
    {
      "sessionId": "session-123",
      "userId": "user-456",
      "connectedAt": "2024-01-15T10:30:00Z"
    }
  ],
  "activeUsers": ["user-456", "user-789"]
}
```

#### Get Connection Metrics

```
GET /api/admin/websocket/metrics
```

Response:
```json
[
  {
    "sessionId": "session-123",
    "userId": "user-456",
    "runId": "run-uuid",
    "connectedAt": "2024-01-15T10:30:00Z",
    "disconnectedAt": null,
    "reconnectionCount": 2,
    "messagesSent": 150,
    "messagesReceived": 75,
    "messageFailures": 3,
    "averageLatencyMs": 45.2,
    "maxLatencyMs": 120,
    "messageDeliveryRate": 0.98,
    "lastActivity": "2024-01-15T11:00:00Z"
  }
]
```

#### Get Metrics for Specific Session

```
GET /api/admin/websocket/metrics/{sessionId}
```

#### Cleanup Stale Metrics

```
POST /api/admin/websocket/cleanup-stale?maxAgeMs=3600000
```

## Frontend Integration

### Connection Health Component

The UI displays real-time connection health:

```typescript
<app-connection-health
  [health]="streamStore.connectionHealth()"
  [queuedMessages]="streamStore.queuedMessageCount()"
  [usingFallback]="streamStore.usingFallback()">
</app-connection-health>
```

Displays:
- Connection status (Connected/Reconnected/Disconnected/HTTP Polling)
- Current latency
- Message delivery rate
- Reconnection count
- Queued message count
- Fallback mode warning

### Health Signals

Access health metrics in components:

```typescript
streamStore.connectionHealth() // { latency, reconnectionCount, messageDeliveryRate, isHealthy }
streamStore.queuedMessageCount() // number of queued messages
streamStore.usingFallback() // true if using HTTP polling
```

## Configuration

### Backend

Adjust message persistence settings in `CollaborationService`:

```java
private static final int MAX_PERSISTED_MESSAGES_PER_RUN = 1000;
private static final Set<String> CRITICAL_EVENT_TYPES = Set.of("GRAFT", "PRUNE", "FLAG");
```

### Frontend

Adjust reconnection and fallback settings in `CollaborationWebSocketService`:

```typescript
private maxReconnectAttempts = 5;  // Attempts before fallback
private pollingInterval = 2000;     // HTTP polling interval (ms)
private messageRetryLimit = 3;      // Per-message retry attempts
```

## Database Schema

### Persisted Collaboration Messages

```sql
CREATE TABLE persisted_collaboration_messages (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ai_run(id),
    user_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    message_data JSONB,
    timestamp TIMESTAMP NOT NULL,
    sequence_number BIGINT NOT NULL,
    is_critical BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_run_id_sequence ON persisted_collaboration_messages(run_id, sequence_number DESC);
CREATE INDEX idx_critical_messages ON persisted_collaboration_messages(run_id, is_critical) WHERE is_critical = TRUE;
```

## Monitoring Best Practices

1. **Set up alerts** on high reconnection rates (> 10/min)
2. **Monitor latency spikes** (p95 > 500ms indicates issues)
3. **Track fallback rate** (> 1% indicates infrastructure problems)
4. **Watch message failures** (> 0.1% suggests network issues)
5. **Review queued messages** (> 50 indicates prolonged disconnection)

## Troubleshooting

### High Reconnection Rate
- Check network stability
- Review WebSocket proxy configuration
- Verify heartbeat settings

### Poor Message Delivery Rate
- Inspect message failure logs
- Check database write performance
- Review message size limits

### Frequent HTTP Fallback
- Investigate WebSocket endpoint availability
- Check SockJS configuration
- Review firewall/proxy settings

### High Latency
- Analyze network path
- Check server load
- Review message processing time
