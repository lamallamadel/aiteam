# WebSocket Health Monitoring & Resilience - Implementation Summary

## Overview

This implementation adds comprehensive WebSocket connection health monitoring and resilience improvements to the Atlasia AI Orchestrator's real-time collaboration system.

## Backend Changes

### 1. New Services

#### `WebSocketConnectionMonitor.java`
- Tracks active connections per run
- Records connection metrics (latency, message counts, failures, reconnections)
- Provides connection health statistics
- Auto-cleanup of stale metrics

**Key Methods:**
- `recordConnection(runId, sessionId, userId)`
- `recordDisconnection(runId, sessionId)`
- `recordMessageSent/Received/Failure(sessionId)`
- `recordMessageLatency(sessionId, latencyMs)`
- `getActiveConnections(runId)`
- `getConnectionMetrics(sessionId)`

### 2. Enhanced CollaborationService

**New Features:**
- Message sequence numbering for ordered replay
- Persistence of critical events (GRAFT, PRUNE, FLAG)
- Automatic cleanup of old messages (keeps last 1000 per run)
- Late-joiner message replay support

**New Methods:**
- `getPersistedMessages(runId, afterSequence)`
- `getCriticalMessages(runId)`

### 3. Prometheus Metrics

Added to `OrchestratorMetrics.java`:
- `orchestrator.websocket.connections.total`
- `orchestrator.websocket.disconnections.total`
- `orchestrator.websocket.reconnections.total`
- `orchestrator.websocket.messages.in.total`
- `orchestrator.websocket.messages.out.total`
- `orchestrator.websocket.message.failures.total`
- `orchestrator.websocket.message.latency` (Timer)
- `orchestrator.websocket.fallback.http.total`

### 4. WebSocket Interceptor Updates

`WebSocketAuthInterceptor.java` now:
- Tracks connection/disconnection events
- Monitors message send/receive
- Records message delivery success/failure
- Extracts run ID from connection destination

### 5. New REST Endpoints

#### Admin Monitoring (`WebSocketAdminController.java`)
- `GET /api/admin/websocket/connections` - All active connections
- `GET /api/admin/websocket/connections/{runId}` - Connections for specific run
- `GET /api/admin/websocket/metrics` - All connection metrics
- `GET /api/admin/websocket/metrics/{sessionId}` - Metrics for specific session
- `POST /api/admin/websocket/cleanup-stale` - Cleanup old metrics

#### HTTP Polling Fallback (`CollaborationController.java`)
- `GET /api/runs/{runId}/collaboration/poll` - Poll for new events
- `GET /api/runs/{runId}/collaboration/replay` - Replay persisted messages

### 6. Database Changes

**New Table:** `persisted_collaboration_messages`
```sql
- id (UUID, primary key)
- run_id (UUID, foreign key to ai_run)
- user_id (VARCHAR)
- event_type (VARCHAR)
- message_data (JSONB)
- timestamp (TIMESTAMP)
- sequence_number (BIGINT)
- is_critical (BOOLEAN)
```

**Indexes:**
- `idx_run_id_timestamp` - For time-based queries
- `idx_run_id_sequence` - For sequence-based replay
- `idx_critical_messages` - For critical event queries

**Migration:** `V12__create_persisted_collaboration_messages.sql`

## Frontend Changes

### 1. Enhanced CollaborationWebSocketService

**New Features:**
- Client-side message queuing during disconnection
- Automatic reconnection with exponential backoff
- HTTP polling fallback after 5 failed reconnection attempts
- Message replay on reconnect using sequence numbers
- Connection health tracking (latency, delivery rate, reconnection count)
- Latency measurement via ping/pong

**New Properties:**
- `messageQueue: QueuedMessage[]` - Queued messages during disconnection
- `useFallbackPolling: boolean` - Whether using HTTP polling
- `lastReceivedSequence: number` - Last sequence number received
- `latencyMeasurements: number[]` - Recent latency samples

**New Methods:**
- `getQueuedMessageCount()` - Returns number of queued messages
- `isUsingFallback()` - Returns true if using HTTP polling
- `measureLatency()` - Sends ping to measure latency
- `replayMissedMessages(runId)` - Fetches and replays missed events

**New Observables:**
- `health$: Observable<ConnectionHealth>` - Health metrics stream

### 2. WorkflowStreamStore Updates

**New Signals:**
- `connectionHealth` - Health metrics (latency, reconnection count, delivery rate)
- `queuedMessageCount` - Number of queued messages
- `usingFallback` - Whether using HTTP polling fallback

### 3. New Component: ConnectionHealthComponent

Displays real-time connection health:
- Visual status indicator (green/red dot)
- Status text (Connected/Reconnected/Disconnected/HTTP Polling)
- Health metrics (latency, delivery rate, reconnection count)
- Queued message count
- Fallback mode warning

### 4. RunDetailComponent Integration

Added connection health display:
```html
<app-connection-health
  [health]="streamStore.connectionHealth()"
  [queuedMessages]="streamStore.queuedMessageCount()"
  [usingFallback]="streamStore.usingFallback()">
</app-connection-health>
```

## Infrastructure

### Grafana Dashboard

**File:** `infra/grafana-websocket-dashboard.json`

**Panels:**
1. Active WebSocket Connections
2. Connection Rate (connections/disconnections per second)
3. Message Throughput (in/out per second)
4. Message Delivery Rate
5. Message Latency (p50, p95, p99)
6. Reconnection Frequency
7. HTTP Polling Fallback Rate
8. Message Failures

## Documentation

### New Files:
1. `docs/WEBSOCKET_HEALTH_MONITORING.md` - Complete feature documentation
2. `IMPLEMENTATION_SUMMARY.md` - This file

## Key Features Summary

### ✅ Connection Quality Metrics
- Latency tracking (p50, p95, p99)
- Reconnection frequency monitoring
- Message delivery rate calculation
- Exposed via Prometheus for Grafana dashboards

### ✅ Client-Side Message Queuing
- Messages queued during disconnection
- Automatic replay on reconnection
- Retry logic with exponential backoff
- Visual queue size indicator

### ✅ Server-Side Message Persistence
- Critical events persisted to database
- Sequence numbers for ordered replay
- Automatic retention (last 1000 messages per run)
- Late-joiner replay support

### ✅ HTTP Polling Fallback
- Automatic fallback after 5 failed reconnections
- Polls every 2 seconds
- Same event format as WebSocket
- Seamless transition

### ✅ Admin Monitoring
- REST API for connection monitoring
- Per-run and per-session metrics
- Active connection tracking
- Stale metric cleanup

## Testing Recommendations

1. **Connection Loss**: Disconnect network to verify message queuing and reconnection
2. **Fallback Mode**: Force 5+ reconnection failures to test HTTP polling
3. **Late Joiners**: Join collaboration mid-session to verify replay
4. **Message Persistence**: Create grafts/prunes/flags, verify they persist across reconnections
5. **Metrics**: Check Prometheus endpoint for metric updates
6. **Admin API**: Test all admin endpoints with multiple concurrent connections

## Configuration

### Backend
- `MAX_PERSISTED_MESSAGES_PER_RUN = 1000` (CollaborationService.java)
- `CRITICAL_EVENT_TYPES = Set.of("GRAFT", "PRUNE", "FLAG")` (CollaborationService.java)

### Frontend
- `maxReconnectAttempts = 5` (CollaborationWebSocketService.ts)
- `pollingInterval = 2000` (CollaborationWebSocketService.ts)
- `messageRetryLimit = 3` (CollaborationWebSocketService.ts)

## Files Created/Modified

### Created:
- `ai-orchestrator/src/main/java/com/atlasia/ai/service/WebSocketConnectionMonitor.java`
- `ai-orchestrator/src/main/java/com/atlasia/ai/model/PersistedCollaborationMessage.java`
- `ai-orchestrator/src/main/java/com/atlasia/ai/persistence/PersistedCollaborationMessageRepository.java`
- `ai-orchestrator/src/main/java/com/atlasia/ai/controller/WebSocketAdminController.java`
- `ai-orchestrator/src/main/resources/db/migration/V12__create_persisted_collaboration_messages.sql`
- `frontend/src/app/components/connection-health.component.ts`
- `infra/grafana-websocket-dashboard.json`
- `docs/WEBSOCKET_HEALTH_MONITORING.md`
- `IMPLEMENTATION_SUMMARY.md`

### Modified:
- `ai-orchestrator/src/main/java/com/atlasia/ai/service/observability/OrchestratorMetrics.java`
- `ai-orchestrator/src/main/java/com/atlasia/ai/service/CollaborationService.java`
- `ai-orchestrator/src/main/java/com/atlasia/ai/config/WebSocketAuthInterceptor.java`
- `ai-orchestrator/src/main/java/com/atlasia/ai/controller/CollaborationController.java`
- `frontend/src/app/services/collaboration-websocket.service.ts`
- `frontend/src/app/services/workflow-stream.store.ts`
- `frontend/src/app/components/run-detail.component.ts`

## Next Steps for Production

1. **Set up Prometheus/Grafana** for production monitoring
2. **Import Grafana dashboard** from `infra/grafana-websocket-dashboard.json`
3. **Configure alerts** for:
   - High reconnection rate (> 10/min)
   - Poor delivery rate (< 95%)
   - High latency (p95 > 500ms)
   - Frequent fallback mode
4. **Test load** with multiple concurrent users
5. **Monitor metrics** during initial rollout
6. **Tune parameters** based on observed behavior
