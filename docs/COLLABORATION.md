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

## Configuration

### Backend
- STOMP broker on `/topic` and `/queue`
- Application prefix: `/app`
- SockJS enabled with CORS: `*`

### Frontend
- Auto-reconnect with exponential backoff
- Heartbeat: 4s incoming/outgoing
- User ID from localStorage or session

## Future Enhancements
- Vector clocks for true causal ordering
- CRDT-based collaborative editing
- User cursor trails and animations
- Chat/comments on pipeline nodes
- Conflict highlighting and resolution UI
- Real-time typing indicators
- Undo/redo with collaborative history
