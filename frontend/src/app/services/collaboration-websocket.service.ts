import { Injectable } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Observable } from 'rxjs';

export interface CollaborationEvent {
  eventType: 'GRAFT' | 'PRUNE' | 'FLAG' | 'USER_JOIN' | 'USER_LEAVE' | 'CURSOR_MOVE';
  userId: string;
  timestamp: number;
  data: any;
}

export interface PresenceState {
  activeUsers: string[];
  cursors: Map<string, string>; // userId -> nodeId
}

@Injectable({ providedIn: 'root' })
export class CollaborationWebSocketService {
  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private currentRunId: string | null = null;
  private userId: string;

  private eventsSubject = new BehaviorSubject<CollaborationEvent | null>(null);
  private presenceSubject = new BehaviorSubject<PresenceState>({
    activeUsers: [],
    cursors: new Map()
  });
  private connectedSubject = new BehaviorSubject<boolean>(false);

  readonly events$: Observable<CollaborationEvent | null> = this.eventsSubject.asObservable();
  readonly presence$: Observable<PresenceState> = this.presenceSubject.asObservable();
  readonly connected$: Observable<boolean> = this.connectedSubject.asObservable();

  constructor() {
    // Generate or retrieve user ID (in production, from auth service)
    this.userId = this.generateUserId();
  }

  connect(runId: string): void {
    if (this.currentRunId === runId && this.client?.connected) {
      return; // Already connected to this run
    }

    this.disconnect();
    this.currentRunId = runId;

    this.client = new Client({
      webSocketFactory: () => new SockJS(`http://localhost:8080/ws/runs/${runId}/collaboration`),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      connectHeaders: {
        'X-User-Id': this.userId
      },
      
      onConnect: () => {
        console.log('WebSocket connected for run:', runId);
        this.connectedSubject.next(true);
        
        // Subscribe to collaboration topic
        this.subscription = this.client!.subscribe(
          `/topic/runs/${runId}/collaboration`,
          (message) => {
            const event = JSON.parse(message.body) as CollaborationEvent;
            this.handleIncomingEvent(event);
          }
        );

        // Announce presence
        this.sendJoin();
      },

      onStompError: (frame) => {
        console.error('WebSocket error:', frame);
        this.connectedSubject.next(false);
      },

      onDisconnect: () => {
        console.log('WebSocket disconnected');
        this.connectedSubject.next(false);
      }
    });

    this.client.activate();
  }

  disconnect(): void {
    if (this.currentRunId && this.client?.connected) {
      this.sendLeave();
    }

    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = null;
    }

    if (this.client) {
      this.client.deactivate();
      this.client = null;
    }

    this.currentRunId = null;
    this.connectedSubject.next(false);
    this.presenceSubject.next({ activeUsers: [], cursors: new Map() });
  }

  sendGraft(after: string, agentName: string): void {
    if (!this.client?.connected || !this.currentRunId) return;

    this.client.publish({
      destination: `/app/runs/${this.currentRunId}/graft`,
      body: JSON.stringify({ after, agentName })
    });
  }

  sendPrune(stepId: string, isPruned: boolean): void {
    if (!this.client?.connected || !this.currentRunId) return;

    this.client.publish({
      destination: `/app/runs/${this.currentRunId}/prune`,
      body: JSON.stringify({ stepId, isPruned })
    });
  }

  sendFlag(stepId: string, note?: string): void {
    if (!this.client?.connected || !this.currentRunId) return;

    this.client.publish({
      destination: `/app/runs/${this.currentRunId}/flag`,
      body: JSON.stringify({ stepId, note: note || '' })
    });
  }

  sendCursorMove(nodeId: string): void {
    if (!this.client?.connected || !this.currentRunId) return;

    this.client.publish({
      destination: `/app/runs/${this.currentRunId}/cursor`,
      body: JSON.stringify({ nodeId })
    });
  }

  private sendJoin(): void {
    if (!this.client?.connected || !this.currentRunId) return;

    this.client.publish({
      destination: `/app/runs/${this.currentRunId}/join`,
      body: JSON.stringify({})
    });
  }

  private sendLeave(): void {
    if (!this.client?.connected || !this.currentRunId) return;

    this.client.publish({
      destination: `/app/runs/${this.currentRunId}/leave`,
      body: JSON.stringify({})
    });
  }

  private handleIncomingEvent(event: CollaborationEvent): void {
    // Emit the event
    this.eventsSubject.next(event);

    // Update presence state
    const currentPresence = this.presenceSubject.value;

    switch (event.eventType) {
      case 'USER_JOIN':
        if (event.data.activeUsers) {
          currentPresence.activeUsers = event.data.activeUsers;
        }
        break;

      case 'USER_LEAVE':
        if (event.data.activeUsers) {
          currentPresence.activeUsers = event.data.activeUsers;
        }
        // Remove cursor for leaving user
        currentPresence.cursors.delete(event.userId);
        break;

      case 'CURSOR_MOVE':
        if (event.data.cursors) {
          currentPresence.cursors = new Map(Object.entries(event.data.cursors));
        }
        break;
    }

    this.presenceSubject.next({ ...currentPresence });
  }

  private generateUserId(): string {
    // In production, get from authenticated session
    const stored = localStorage.getItem('atlasia_user_id');
    if (stored) return stored;

    const id = 'user_' + Math.random().toString(36).substring(2, 11);
    localStorage.setItem('atlasia_user_id', id);
    return id;
  }

  getUserId(): string {
    return this.userId;
  }
}
