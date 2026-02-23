# Multi-User Collaboration

WebSocket-based real-time collaboration system for workflow runs.

## Features

### Real-time Mutations
- **Graft**: Inject new agents into the pipeline
- **Prune**: Skip/enable pipeline steps  
- **Flag**: Mark steps for review

### Presence Indicators
- Active user count in pipeline header
- User avatars with color-coded identities
- Hover tooltip showing all active users

### Real-time Cursors
- Live cursor positions showing where teammates are focused
- Color-coded user indicators on pipeline nodes
- Automatic cursor updates on node hover

### Operational Transformation
- Conflict resolution for concurrent edits
- Last-Write-Wins (LWW) strategy for grafts at same position
- Set-based CRDT for prune operations

### Audit Trail
- All collaboration events stored in `collaboration_events` table
- Full history with user_id, event_type, timestamp
- Queryable via REST API: `/api/runs/{runId}/collaboration/events`

## Architecture

### Backend
- **WebSocket Endpoint**: `/ws/runs/{runId}/collaboration`
- **Protocol**: STOMP over SockJS
- **Topics**: `/topic/runs/{runId}/collaboration`
- **Message Types**: 
  - `GRAFT`: Inject agent
  - `PRUNE`: Toggle step pruning
  - `FLAG`: Flag for review
  - `USER_JOIN`: User presence
  - `USER_LEAVE`: User departure
  - `CURSOR_MOVE`: Focus tracking
  - `PING/PONG`: Latency measurement

### Connection Health Monitoring

The system tracks comprehensive connection health metrics:

#### Metrics Tracked
- **Latency**: Round-trip message latency measured via ping/pong (every 10 seconds)
- **Reconnection Frequency**: Number of reconnection attempts per session
- **Message Delivery Rate**: Percentage of successfully delivered messages
- **Connection Quality Score**: Composite score (0-100) based on:
  - Latency (40% weight)
  - Reconnections (30% weight)
  - Delivery rate (30% weight)

#### Client-Side Features
- **Automatic Latency Measurement**: Pings server every 10 seconds
- **Message Queuing**: Messages queued during disconnection with replay on reconnect
- **Automatic Fallback**: Falls back to HTTP polling after 5 failed reconnection attempts
- **Health Status**: Real-time health status (HEALTHY/DEGRADED/UNHEALTHY)
- **Persistent Sequence Tracking**: Tracks last received sequence number to replay missed messages

#### Server-Side Features
- **Message Persistence**: Critical events (GRAFT, PRUNE, FLAG) persisted in `persisted_collaboration_messages` table
- **Connection Tracking**: Per-session metrics tracked via `WebSocketConnectionMonitor`
- **Prometheus Metrics**: All metrics exported for monitoring
- **Scheduled Quality Updates**: Connection quality recalculated every 30 seconds

#### Admin Endpoints
- `GET /api/admin/websocket/connections` - List all active connections with counts
- `GET /api/admin/websocket/connections/{runId}` - Get connections for specific run
- `GET /api/admin/websocket/metrics` - Get detailed metrics for all connections
- `GET /api/admin/websocket/metrics/{sessionId}` - Get metrics for specific session
- `GET /api/admin/websocket/health` - Get overall health status with distribution
- `POST /api/admin/websocket/cleanup-stale?maxAgeMs=3600000` - Cleanup stale metrics

#### Prometheus Metrics
- `orchestrator_websocket_connections_total` - Total connections established
- `orchestrator_websocket_disconnections_total` - Total disconnections
- `orchestrator_websocket_reconnections_total` - Total reconnection attempts
- `orchestrator_websocket_messages_in_total` - Total incoming messages
- `orchestrator_websocket_messages_out_total` - Total outgoing messages
- `orchestrator_websocket_message_failures_total` - Total failed message deliveries
- `orchestrator_websocket_message_latency` - Message round-trip latency (histogram)
- `orchestrator_websocket_fallback_http_total` - Total HTTP polling fallbacks
- `orchestrator_websocket_connection_quality` - Connection quality score (0-100)
- `orchestrator_websocket_message_delivery_rate` - Message delivery success rate (0-1)

#### Grafana Dashboard
A dedicated dashboard (`infra/grafana-websocket-dashboard.json`) provides:
- Active connections and connection rate graphs
- Message throughput and latency percentiles (p50, p90, p95, p99)
- Reconnection frequency trends
- HTTP fallback rate monitoring
- Connection quality gauges
- Health status distribution
- Automatic alerting on low delivery rate (<95%)

### Frontend
- **Service**: `CollaborationWebSocketService`
- **Store**: `WorkflowStreamStore` (extended)
- **Component**: `NeuralTraceComponent` (with presence UI)

### Database Schema
```sql
CREATE TABLE collaboration_events (
    id UUID PRIMARY KEY,
    run_id UUID REFERENCES ai_run(id),
    user_id VARCHAR(255),
    event_type VARCHAR(50),
    event_data JSONB,
    timestamp TIMESTAMP
);
```

## Usage

### Enable Collaboration
```typescript
// Connect to run with collaboration enabled
workflowStreamStore.connectToRun(runId, true);
```

### Send Mutations
```typescript
// Graft
workflowStreamStore.sendGraft('ARCHITECT', 'security-scanner-v1');

// Prune
workflowStreamStore.sendPrune('QUALIFIER', true);

// Flag
workflowStreamStore.sendFlag('PM', 'Needs review');

// Cursor
workflowStreamStore.sendCursorMove('DEVELOPER');
```

### Subscribe to Events
```typescript
workflowStreamStore.collaborationEvents$.subscribe(event => {
  console.log('Collaboration event:', event);
});

workflowStreamStore.activeUsers$.subscribe(users => {
  console.log('Active users:', users);
});

workflowStreamStore.cursorPositions$.subscribe(cursors => {
  console.log('Cursor positions:', cursors);
});
```

### Monitor Connection Health
```typescript
// Subscribe to health updates
collaborationService.health$.subscribe(health => {
  console.log('Connection Health:', {
    latency: health.latency,
    reconnections: health.reconnectionCount,
    deliveryRate: health.messageDeliveryRate,
    quality: health.qualityScore,
    status: health.healthStatus,
    isHealthy: health.isHealthy
  });
  
  // Display warning if degraded
  if (health.healthStatus === 'DEGRADED') {
    showWarning('Connection quality is degraded');
  }
  
  // Switch to fallback if unhealthy
  if (health.healthStatus === 'UNHEALTHY') {
    showError('Connection is unhealthy, using HTTP polling');
  }
});

// Check if using fallback
const usingFallback = collaborationService.isUsingFallback();
const queuedMessages = collaborationService.getQueuedMessageCount();

console.log(`Fallback: ${usingFallback}, Queued: ${queuedMessages}`);
```

### Admin: Monitor All Connections
```bash
# Get all active connections
curl http://localhost:8080/api/admin/websocket/connections

# Get connections for specific run
curl http://localhost:8080/api/admin/websocket/connections/{runId}

# Get detailed metrics
curl http://localhost:8080/api/admin/websocket/metrics

# Get overall health status
curl http://localhost:8080/api/admin/websocket/health

# Get metrics for specific session
curl http://localhost:8080/api/admin/websocket/metrics/{sessionId}

# Cleanup stale metrics (older than 1 hour)
curl -X POST http://localhost:8080/api/admin/websocket/cleanup-stale?maxAgeMs=3600000
```

## Configuration

### Backend
- STOMP broker on `/topic` and `/queue`
- Application prefix: `/app`
- SockJS enabled with CORS: `*`

### Frontend
- Auto-reconnect with exponential backoff
- Heartbeat: 4s incoming/outgoing
- User ID from localStorage or session

## Monitoring & Observability

### Metrics Collection
All WebSocket health metrics are automatically collected and exposed to Prometheus at `/actuator/prometheus`.

### Grafana Dashboard Setup
1. Import the dashboard: `infra/grafana-websocket-dashboard.json`
2. Configure Prometheus datasource
3. Dashboard will auto-refresh every 5 seconds

### Health Status Thresholds

**Connection Quality Score Calculation:**
- Latency Score (40%):
  - 0-50ms: 100 points
  - 50-100ms: 90 points
  - 100-200ms: 75 points
  - 200-500ms: 50 points
  - 500-1000ms: 25 points
  - >1000ms: 10 points

- Reconnection Score (30%):
  - 0 reconnections: 100 points
  - 1 reconnection: 80 points
  - 2 reconnections: 60 points
  - 3-5 reconnections: 40 points
  - 6-10 reconnections: 20 points
  - >10 reconnections: 5 points

- Delivery Rate Score (30%):
  - Message delivery rate × 100

**Health Status:**
- **HEALTHY**: Quality score ≥ 80
- **DEGRADED**: Quality score 50-79
- **UNHEALTHY**: Quality score < 50

### Alerting Rules (Recommended)
```yaml
groups:
  - name: websocket_alerts
    rules:
      - alert: HighWebSocketLatency
        expr: histogram_quantile(0.95, rate(orchestrator_websocket_message_latency_bucket[5m])) > 500
        for: 5m
        annotations:
          summary: "High WebSocket latency detected"
          
      - alert: LowMessageDeliveryRate
        expr: orchestrator_websocket_message_delivery_rate < 0.95
        for: 5m
        annotations:
          summary: "Low message delivery rate detected"
          
      - alert: HighReconnectionRate
        expr: rate(orchestrator_websocket_reconnections_total[5m]) > 0.1
        for: 5m
        annotations:
          summary: "High reconnection rate detected"
          
      - alert: HttpPollingFallbackActive
        expr: rate(orchestrator_websocket_fallback_http_total[5m]) > 0
        for: 1m
        annotations:
          summary: "WebSocket fallback to HTTP polling is active"
```

## Troubleshooting

### Connection Issues
1. Check network connectivity
2. Verify WebSocket endpoint is accessible: `/ws/runs/{runId}/collaboration`
3. Check browser console for errors
4. Review server logs for connection errors

### High Latency
1. Check network conditions
2. Review server resource usage (CPU, memory)
3. Check database query performance
4. Consider scaling if consistently high

### Message Delivery Failures
1. Check message queue size: `GET /api/admin/websocket/metrics/{sessionId}`
2. Review network stability
3. Check for client-side errors in browser console
4. Verify message size isn't too large

### Fallback to HTTP Polling
This occurs after 5 consecutive WebSocket reconnection failures:
1. Check WebSocket protocol support
2. Verify firewall/proxy configuration
3. Check for SSL/TLS certificate issues
4. Review server WebSocket configuration

## Future Enhancements
- Vector clocks for true causal ordering
- CRDT-based collaborative editing
- User cursor trails and animations
- Chat/comments on pipeline nodes
- Conflict highlighting and resolution UI
- Real-time typing indicators
- Undo/redo with collaborative history
- Per-user bandwidth throttling
- Adaptive quality based on connection health
- Message compression for slow connections
