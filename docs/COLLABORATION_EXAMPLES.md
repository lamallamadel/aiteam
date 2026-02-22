# Collaboration Feature Examples

## Basic Setup

### Connect to a Run with Collaboration
```typescript
// In RunDetailComponent or similar
ngOnInit() {
  const runId = this.route.snapshot.paramMap.get('id');
  if (runId) {
    // Enable collaboration on connection
    this.workflowStreamStore.connectToRun(runId, true);
  }
}
```

## Sending Mutations

### Graft a New Agent
```typescript
// Inject security scanner after ARCHITECT step
onGraftAgent() {
  this.workflowStreamStore.sendGraft('ARCHITECT', 'security-scanner-v1');
}
```

### Prune a Step
```typescript
// Skip the QUALIFIER step
onPruneQualifier() {
  this.workflowStreamStore.sendPrune('QUALIFIER', true);
}

// Re-enable a previously pruned step
onUnpruneQualifier() {
  this.workflowStreamStore.sendPrune('QUALIFIER', false);
}
```

### Flag a Step for Review
```typescript
// Flag PM step with a note
onFlagPM() {
  this.workflowStreamStore.sendFlag('PM', 'Needs refinement on acceptance criteria');
}
```

### Track Cursor Position
```typescript
// Send cursor position when user hovers over a node
onNodeHover(nodeId: string) {
  this.workflowStreamStore.sendCursorMove(nodeId);
}
```

## Observing Collaboration State

### Active Users
```typescript
// Get list of active users
this.workflowStreamStore.activeUsers$.subscribe(users => {
  console.log('Users viewing this run:', users);
});

// In template
<div class="users-list">
  <span *ngFor="let user of streamStore.activeUsers()">{{ user }}</span>
</div>
```

### Cursor Positions
```typescript
// Get cursor positions map
this.workflowStreamStore.cursorPositions$.subscribe(cursors => {
  cursors.forEach((nodeId, userId) => {
    console.log(`${userId} is viewing ${nodeId}`);
  });
});

// Check who's viewing a specific node
getUsersAtNode(nodeId: string): string[] {
  const cursors = this.streamStore.cursorPositions();
  const users: string[] = [];
  cursors.forEach((cursorNodeId, userId) => {
    if (cursorNodeId === nodeId) {
      users.push(userId);
    }
  });
  return users;
}
```

### Collaboration Events
```typescript
// Subscribe to all collaboration events
this.workflowStreamStore.collaborationEvents$.subscribe(events => {
  events.forEach(event => {
    console.log(`${event.userId} performed ${event.eventType}`, event.data);
  });
});
```

## Advanced Usage

### Custom Event Handling
```typescript
// React to specific event types
this.collaborationService.events$.subscribe(event => {
  if (!event) return;
  
  switch (event.eventType) {
    case 'GRAFT':
      this.handleRemoteGraft(event.data);
      break;
    case 'PRUNE':
      this.handleRemotePrune(event.data);
      break;
    case 'FLAG':
      this.handleRemoteFlag(event.data);
      break;
    case 'USER_JOIN':
      this.showUserJoinedMessage(event.userId);
      break;
    case 'USER_LEAVE':
      this.showUserLeftMessage(event.userId);
      break;
  }
});
```

### Conflict Detection
```typescript
// Detect if another user grafted at same position
handleRemoteGraft(data: any) {
  const localGrafts = this.pendingGrafts();
  const hasConflict = localGrafts.some(g => g.after === data.after);
  
  if (hasConflict) {
    this.showConflictWarning(`Another user grafted at ${data.after}`);
  }
}
```

### Load Historical Events
```typescript
// Load past collaboration events via REST API
loadCollaborationHistory(runId: string) {
  this.orchestratorService.getCollaborationEvents(runId, 100)
    .subscribe(events => {
      console.log('Historical collaboration events:', events);
      this.displayEventTimeline(events);
    });
}
```

## Integration with NeuralTraceComponent

```typescript
// Full example with all collaboration features
<app-neural-trace
  [steps]="streamStore.stepTimeline()"
  [interactive]="true"
  [showPresence]="streamStore.isCollaborationConnected()"
  [activeUsers]="streamStore.activeUsers()"
  [cursorPositions]="streamStore.cursorPositions()"
  (flagged)="onNodeFlagged($event)"
  (pruned)="onNodePruned($event)"
  (grafted)="onNodeGrafted($event)"
  (cursorMoved)="onCursorMoved($event)">
</app-neural-trace>

// Component handlers
onNodeFlagged(stepId: string) {
  this.streamStore.sendFlag(stepId);
  this.orchestratorService.flagNode(this.runId, stepId).subscribe();
}

onNodePruned(stepId: string) {
  const isPruned = !this.prunedSteps().includes(stepId);
  this.streamStore.sendPrune(stepId, isPruned);
  this.orchestratorService.setPrunedSteps(this.runId, this.getPrunedStepsString()).subscribe();
}

onNodeGrafted(event: GraftEvent) {
  this.streamStore.sendGraft(event.after, event.agentName);
  this.orchestratorService.addGraft(this.runId, event.after, event.agentName).subscribe();
}

onCursorMoved(nodeId: string) {
  this.streamStore.sendCursorMove(nodeId);
}
```

## User Identification

```typescript
// Get current user ID
const userId = this.workflowStreamStore.getCurrentUserId();

// Check if event is from current user
if (event.userId === this.getCurrentUserId()) {
  // This is our own event, don't show notification
  return;
}
```

## Cleanup

```typescript
ngOnDestroy() {
  // Disconnect from collaboration when leaving
  this.workflowStreamStore.disconnect();
}
```
