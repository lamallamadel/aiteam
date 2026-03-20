export interface CollaborationEvent {
  eventType: 'GRAFT' | 'PRUNE' | 'FLAG' | 'USER_JOIN' | 'USER_LEAVE' | 'CURSOR_MOVE';
  userId: string;
  timestamp: number;
  data: any;
}

export interface CollaborationEventEntity {
  id: string;
  runId: string;
  userId: string;
  eventType: string;
  eventData: string;
  timestamp: string | Date;
}

export interface PresenceState {
  activeUsers: string[];
  cursors: Map<string, string>;
}

export interface GraftMutation {
  after: string;
  agentName: string;
}

export interface PruneMutation {
  stepId: string;
  isPruned: boolean;
}

export interface FlagMutation {
  stepId: string;
  note?: string;
}

export interface TimeTravelSnapshot {
  eventId: string;
  userId: string;
  eventType: string;
  timestamp: string;
  stateBefore: Record<string, any>;
  stateAfter: Record<string, any>;
  diff: Record<string, any>;
  description: string;
}

export interface CollaborationAnalytics {
  runId: string;
  totalEvents: number;
  uniqueUsers: number;
  sessionDuration: string;
  firstEventTime: string;
  lastEventTime: string;
  eventTypeCounts: Record<string, number>;
  userActivityCounts: Record<string, number>;
  mostGraftedCheckpoints: GraftCheckpoint[];
  userActivityHeatmaps: Record<string, UserActivityHeatmap>;
  conflictResolutions: ConflictResolution[];
  averageSessionDurationMinutes: number;
  eventsPerMinute: number;
}

export interface GraftCheckpoint {
  checkpointName: string;
  graftCount: number;
  agentNames: string[];
}

export interface UserActivityHeatmap {
  userId: string;
  hourlyActivity: Record<number, number>;
  totalActions: number;
}

export interface ConflictResolution {
  timestamp: string;
  userId1: string;
  userId2: string;
  conflictType: string;
  resolution: string;
  targetNode: string;
}

export interface CrdtMessage {
  type: 'CRDT_SYNC' | 'CRDT_UPDATE' | 'CRDT_FULL_SYNC';
  runId: string;
  sourceRegion: string;
  /** Base64-encoded CRDT changes (CRDT_SYNC, CRDT_UPDATE). */
  changes?: string;
  /** Base64-encoded full document state (CRDT_FULL_SYNC). */
  state?: string;
  timestamp: number;
}

export interface CrdtDocumentState {
  runId: string;
  grafts: Record<string, unknown>[];
  prunedSteps: string[];
  flags: Record<string, unknown>[];
  region: string;
}

export interface CrdtPeersResponse {
  runId: string;
  peers: string[];
  region: string;
}
