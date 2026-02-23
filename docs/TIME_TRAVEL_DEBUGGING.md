# Time-Travel Debugging & Collaboration Replay

This document describes the time-travel debugging and replay capabilities for the Atlasia collaboration system.

## Overview

The time-travel debugging system allows developers to:
- Step through collaboration event history with VCR-like controls
- Visualize before/after state changes for each event
- Analyze collaboration patterns and metrics
- Export session data for offline analysis
- Review conflict resolution decisions in the OT system

## Architecture

### Backend Components

#### 1. CollaborationEventEntity (Enhanced)
```java
@Entity
@Table(name = "collaboration_events")
public class CollaborationEventEntity {
    // Existing fields...
    
    @Column(name = "state_before", columnDefinition = "jsonb")
    private String stateBefore;
    
    @Column(name = "state_after", columnDefinition = "jsonb")
    private String stateAfter;
}
```

Tracks state snapshots before and after each collaboration event for diff visualization.

#### 2. TimeTravelService
```java
@Service
public class TimeTravelService {
    List<TimeTravelSnapshot> getEventHistory(UUID runId);
    CollaborationAnalytics getAnalytics(UUID runId);
    String exportEventsAsJson(UUID runId);
    String exportEventsAsCsv(UUID runId);
}
```

Core service providing:
- Event history retrieval with state diffs
- Analytics computation (session duration, user activity, graft patterns)
- Export functionality in JSON and CSV formats

#### 3. REST Endpoints
```
GET /api/runs/{runId}/collaboration/history
    ?startTimestamp={timestamp}&endTimestamp={timestamp}
    
GET /api/runs/{runId}/collaboration/analytics

GET /api/runs/{runId}/collaboration/export/json

GET /api/runs/{runId}/collaboration/export/csv
```

### Frontend Components

#### 1. TimeTravelControlsComponent
VCR-style playback controls:
- **Timeline slider** with event markers (color-coded by type)
- **Playback controls**: Start, Step Back, Play/Pause, Step Forward, End
- **Speed control**: 0.25x, 0.5x, 1x, 2x, 4x
- **Export buttons**: JSON and CSV download

Usage:
```typescript
<app-time-travel-controls
  [runId]="runId"
  (snapshotChanged)="onSnapshotChanged($event)">
</app-time-travel-controls>
```

#### 2. EventSourcingVizComponent
Visualizes state changes:
- Event metadata (ID, user, type, timestamp)
- **Before/After diff view** with highlighted changes
- Expandable full state viewer
- Color-coded change indicators

Usage:
```typescript
<app-event-sourcing-viz
  [snapshot]="currentSnapshot">
</app-event-sourcing-viz>
```

#### 3. CollaborationAnalyticsDashboardComponent
Comprehensive analytics dashboard:

**Metrics Cards:**
- Total events
- Unique users
- Session duration
- Events per minute

**Event Type Breakdown:**
- Bar chart showing distribution of GRAFT, PRUNE, FLAG, etc.

**Most Grafted Checkpoints:**
- Top 10 pipeline nodes where grafts were inserted
- List of agents used at each checkpoint

**User Activity:**
- Actions per user with color-coded avatars

**Activity Heatmap:**
- 24-hour heatmap showing when users were most active
- Visual intensity based on action frequency

**Conflict Resolution Audit Trail:**
- Timeline of OT conflicts
- Users involved
- Resolution method applied

Usage:
```typescript
<app-collaboration-analytics-dashboard
  [runId]="runId">
</app-collaboration-analytics-dashboard>
```

#### 4. TimeTravelDemoComponent
Unified interface with tabbed navigation:
- **Playback tab**: Controls + current event preview
- **Event Sourcing tab**: State diff visualization
- **Analytics tab**: Full metrics dashboard

Usage:
```typescript
<app-time-travel-demo [runId]="runId"></app-time-travel-demo>
```

## Data Flow

### Event Capture
1. User performs collaboration action (graft, prune, flag)
2. CollaborationService captures state before action
3. Action is applied
4. CollaborationService captures state after action
5. Event stored in `collaboration_events` with both snapshots

### Event Replay
1. Frontend requests history via TimeTravelService
2. Backend computes diffs between before/after states
3. Returns TimeTravelSnapshot array with:
   - Event metadata
   - State before
   - State after
   - Computed diff
   - Human-readable description

### Analytics Computation
1. TimeTravelService loads all events for run
2. Aggregates metrics:
   - Event counts by type
   - User activity counts
   - Graft checkpoint frequencies
   - Hourly activity patterns
3. Returns CollaborationAnalytics DTO

## Database Schema

```sql
CREATE TABLE collaboration_events (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    user_id VARCHAR NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_data JSONB,
    timestamp TIMESTAMP NOT NULL,
    state_before JSONB,     -- NEW
    state_after JSONB       -- NEW
);

CREATE INDEX idx_run_timestamp ON collaboration_events(run_id, timestamp);
CREATE INDEX idx_run_user ON collaboration_events(run_id, user_id);
CREATE INDEX idx_run_event_type ON collaboration_events(run_id, event_type);
```

## Export Formats

### JSON Export
```json
{
  "runId": "uuid",
  "exportedAt": "2024-01-15T10:30:00Z",
  "totalEvents": 42,
  "events": [
    {
      "id": "event-uuid",
      "userId": "user_abc123",
      "eventType": "GRAFT",
      "timestamp": "2024-01-15T10:15:00Z",
      "eventData": { "after": "DEVELOPER", "agentName": "developer-v1" },
      "stateBefore": { "grafts": [] },
      "stateAfter": { "grafts": [{ "id": "graft-1", ... }] }
    }
  ]
}
```

### CSV Export
```csv
Event ID,Run ID,User ID,Event Type,Timestamp,Event Data
uuid1,run-uuid,user_abc123,GRAFT,2024-01-15T10:15:00Z,"{""after"":""DEVELOPER"",...}"
```

## Integration with Neural Trace

The time-travel controls can be integrated directly into the neural-trace component:

```typescript
<app-neural-trace
  [steps]="workflowEvents"
  [interactive]="true"
  [showPresence]="true"
  [showTimeTravelControls]="true"
  [runId]="runId">
</app-neural-trace>
```

## Performance Considerations

1. **Indexed Queries**: All time-range and user-specific queries use database indexes
2. **Lazy Loading**: Analytics computed on-demand, not in real-time
3. **Pagination**: History API supports pagination for large sessions
4. **JSONB**: PostgreSQL JSONB for efficient state storage and querying

## Use Cases

### 1. Debugging Collaboration Issues
Step through events to identify when and how conflicts occurred.

### 2. Training & Education
Replay sessions to demonstrate collaboration patterns and best practices.

### 3. Audit & Compliance
Export full event logs for compliance and audit trails.

### 4. Performance Analysis
Identify bottlenecks in collaboration workflows (e.g., checkpoints with excessive grafts).

### 5. User Behavior Analysis
Understand how teams collaborate:
- When are they most active?
- Which checkpoints need the most intervention?
- Are there patterns in conflict resolution?

## Future Enhancements

- **Snapshot branching**: Create alternate timelines from any point
- **State reconstruction**: Fully rebuild application state at any timestamp
- **Collaborative replay**: Multiple users reviewing same session simultaneously
- **ML-powered insights**: Detect anomalies and suggest improvements
- **Video recording**: Capture screen recording alongside event stream
