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
