import { Injectable, OnDestroy, NgZone } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Observable, interval, Subscription } from 'rxjs';
import { HttpClient } from '@angular/common/http';

export interface CollaborationEvent {
  eventType: 'GRAFT' | 'PRUNE' | 'FLAG' | 'USER_JOIN' | 'USER_LEAVE' | 'CURSOR_MOVE' | 'PONG';
  userId: string;
  timestamp: number;
  sequenceNumber?: number;
  data: any;
}

export interface PresenceState {
  activeUsers: string[];
  cursors: Map<string, string>;
}

export interface QueuedMessage {
  destination: string;
  body: string;
  timestamp: number;
  retryCount: number;
}

export interface ConnectionHealth {
  latency: number;
  reconnectionCount: number;
  messageDeliveryRate: number;
  isHealthy: boolean;
  qualityScore?: number;
  healthStatus?: 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY';
}

@Injectable({ providedIn: 'root' })
export class CollaborationWebSocketService implements OnDestroy {
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
  private healthSubject = new BehaviorSubject<ConnectionHealth>({
    latency: 0,
    reconnectionCount: 0,
    messageDeliveryRate: 1.0,
    isHealthy: true
  });

  readonly events$: Observable<CollaborationEvent | null> = this.eventsSubject.asObservable();
  readonly presence$: Observable<PresenceState> = this.presenceSubject.asObservable();
  readonly connected$: Observable<boolean> = this.connectedSubject.asObservable();
  readonly health$: Observable<ConnectionHealth> = this.healthSubject.asObservable();

  private messageQueue: QueuedMessage[] = [];
  private reconnectionAttempts = 0;
  private maxReconnectAttempts = 5;
  private useFallbackPolling = false;
  private pollingSubscription: Subscription | null = null;
  private latencyCheckSubscription: Subscription | null = null;
  private mockBroadcastChannel: BroadcastChannel | null = null;
  private lastReceivedSequence: number | null = null;
  private messagesSent = 0;
  private messagesFailed = 0;
  private lastPingSent = 0;
  private latencyMeasurements: number[] = [];

  constructor(private http: HttpClient, private ngZone: NgZone) {
    this.userId = this.generateUserId();
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private isE2EMock(): boolean {
    return typeof window !== 'undefined' && (window as any).__E2E_MOCK_COLLAB__ === true;
  }

  connect(runId: string): void {
    if (this.currentRunId === runId && this.client?.connected) {
      return;
    }

    this.disconnect();
    this.currentRunId = runId;
    this.reconnectionAttempts = 0;
    this.useFallbackPolling = false;

    if (this.isE2EMock()) {
      // Pick up the test-injected userId if available
      const storedId = typeof localStorage !== 'undefined' ? localStorage.getItem('atlasia_user_id') : null;
      if (storedId) {
        this.userId = storedId;
      }

      // Cross-page sync via BroadcastChannel
      if (typeof BroadcastChannel !== 'undefined') {
        if (this.mockBroadcastChannel) {
          this.mockBroadcastChannel.close();
        }
        this.mockBroadcastChannel = new BroadcastChannel('e2e-collab-' + runId);
        this.mockBroadcastChannel.onmessage = (evt) => {
          this.handleIncomingEvent(evt.data as CollaborationEvent);
        };
      }

      this.connectedSubject.next(true);
      this.presenceSubject.next({ activeUsers: [this.userId], cursors: new Map() });
      this.healthSubject.next({
        latency: 0,
        reconnectionCount: 0,
        messageDeliveryRate: 1.0,
        isHealthy: true,
        qualityScore: 100,
        healthStatus: 'HEALTHY'
      });
      const joinEvent: CollaborationEvent = {
        eventType: 'USER_JOIN',
        userId: this.userId,
        timestamp: Date.now(),
        data: {}
      };
      this.eventsSubject.next(joinEvent);
      if (this.mockBroadcastChannel) {
        this.mockBroadcastChannel.postMessage(joinEvent);
      }
      // Bridge to other Playwright contexts (synchronous Node.js call via exposeFunction)
      this.e2eBroadcast(joinEvent);
      // Provide a mock STOMP client so tests that call client methods still work.
      this.client = {
        deactivate: () => this.disconnect(),
        forceDisconnect: () => {},  // no-op: mock stays connected
        connected: true,
      } as any;
      return;
    }

    const forcePolling = (window as any).__E2E_FORCE_COLLAB_POLLING__ === true;
    if (forcePolling) {
      this.connectedSubject.next(true);
      this.startHttpPolling(runId);
      return;
    }

    this.connectWebSocket(runId);
  }

  private connectWebSocket(runId: string): void {
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
        this.reconnectionAttempts = 0;
        this.updateHealth();
        
        this.subscription = this.client!.subscribe(
          `/topic/runs/${runId}/collaboration`,
          (message: any) => {
            const event = JSON.parse(message.body) as CollaborationEvent;
            this.handleIncomingEvent(event);
            
            if (event.sequenceNumber) {
              this.lastReceivedSequence = event.sequenceNumber;
            }
          }
        );

        this.sendJoin();
        this.replayMissedMessages(runId);
        this.processMessageQueue();
        this.startLatencyChecks();
      },

      onStompError: (frame: any) => {
        console.error('WebSocket error:', frame);
        this.connectedSubject.next(false);
        this.handleConnectionFailure(runId);
      },

      onDisconnect: () => {
        console.log('WebSocket disconnected');
        this.connectedSubject.next(false);
        this.handleConnectionFailure(runId);
      },

      onWebSocketClose: () => {
        console.log('WebSocket closed');
        this.connectedSubject.next(false);
        this.handleConnectionFailure(runId);
      }
    });

    this.client.activate();
  }

  private handleConnectionFailure(runId: string): void {
    this.reconnectionAttempts++;
    this.updateHealth();

    if (this.reconnectionAttempts >= this.maxReconnectAttempts) {
      console.warn('Max reconnection attempts reached, falling back to HTTP polling');
      this.useFallbackPolling = true;
      this.startHttpPolling(runId);
    }
  }

  private startHttpPolling(runId: string): void {
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
    }

    if (!this.connectedSubject.getValue()) {
      this.connectedSubject.next(true);
    }

    this.pollingSubscription = interval(2000).subscribe(() => {
      this.http.get<any>(`/api/runs/${runId}/collaboration/poll`, {
        params: this.lastReceivedSequence ? 
          { afterSequence: this.lastReceivedSequence.toString() } : {}
      }).subscribe({
        next: (response) => {
          if (response.events && response.events.length > 0) {
            response.events.forEach((event: any) => {
              this.handleIncomingEvent(event);
              if (event.sequenceNumber) {
                this.lastReceivedSequence = event.sequenceNumber;
              }
            });
          }

          if (response.activeUsers) {
            this.presenceSubject.next({
              activeUsers: response.activeUsers,
              cursors: new Map(Object.entries(response.cursorPositions || {}))
            });
          }
        },
        error: (err) => {
          console.error('HTTP polling error:', err);
        }
      });
    });
  }

  private replayMissedMessages(runId: string): void {
    if (this.lastReceivedSequence === null) {
      this.http.get<any>(`/api/runs/${runId}/collaboration/replay`).subscribe({
        next: (response) => {
          if (response.events && response.events.length > 0) {
            response.events.forEach((event: any) => {
              this.handleIncomingEvent(event);
              if (event.sequenceNumber) {
                this.lastReceivedSequence = event.sequenceNumber;
              }
            });
          }
        }
      });
    } else {
      this.http.get<any>(`/api/runs/${runId}/collaboration/replay`, {
        params: { fromSequence: this.lastReceivedSequence.toString() }
      }).subscribe({
        next: (response) => {
          if (response.events && response.events.length > 0) {
            response.events.forEach((event: any) => {
              this.handleIncomingEvent(event);
              if (event.sequenceNumber) {
                this.lastReceivedSequence = event.sequenceNumber;
              }
            });
          }
        }
      });
    }
  }

  disconnect(): void {
    if (this.isE2EMock()) {
      if (!this.connectedSubject.value) return; // already disconnected — avoid duplicate USER_LEAVE
      const leaveEvent: CollaborationEvent = {
        eventType: 'USER_LEAVE',
        userId: this.userId,
        timestamp: Date.now(),
        data: {} // no activeUsers → receivers will remove this.userId from their list
      };
      if (this.mockBroadcastChannel) {
        this.mockBroadcastChannel.postMessage(leaveEvent);
        this.mockBroadcastChannel.close();
        this.mockBroadcastChannel = null;
      }
      // Bridge to other Playwright contexts (synchronous Node.js call via exposeFunction)
      this.e2eBroadcast(leaveEvent);
      this.eventsSubject.next(leaveEvent);
      this.currentRunId = null;
      this.connectedSubject.next(false);
      this.presenceSubject.next({ activeUsers: [], cursors: new Map() });
      return;
    }

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

    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = null;
    }

    if (this.latencyCheckSubscription) {
      this.latencyCheckSubscription.unsubscribe();
      this.latencyCheckSubscription = null;
    }

    this.currentRunId = null;
    this.connectedSubject.next(false);
    this.presenceSubject.next({ activeUsers: [], cursors: new Map() });
    this.useFallbackPolling = false;
  }

  sendGraft(after: string, agentName: string): void {
    this.sendMessage(`/app/runs/${this.currentRunId}/graft`, { after, agentName });
  }

  sendPrune(stepId: string, isPruned: boolean): void {
    this.sendMessage(`/app/runs/${this.currentRunId}/prune`, { stepId, isPruned });
  }

  sendFlag(stepId: string, note?: string): void {
    this.sendMessage(`/app/runs/${this.currentRunId}/flag`, { stepId, note: note || '' });
  }

  sendCursorMove(nodeId: string): void {
    this.sendMessage(`/app/runs/${this.currentRunId}/cursor`, { nodeId });
  }

  private sendMessage(destination: string, body: any): void {
    if (this.isE2EMock()) {
      const eventType: CollaborationEvent['eventType'] = destination.includes('/graft') ? 'GRAFT'
        : destination.includes('/prune') ? 'PRUNE'
        : destination.includes('/flag') ? 'FLAG'
        : destination.includes('/cursor') ? 'CURSOR_MOVE'
        : 'GRAFT';
      const event: CollaborationEvent = {
        eventType,
        userId: this.userId,
        timestamp: Date.now(),
        data: typeof body === 'string' ? JSON.parse(body) : body
      };
      // Bridge to other Playwright contexts (synchronous Node.js call via exposeFunction)
      this.e2eBroadcast(event);
      // Also broadcast via BroadcastChannel (same-origin same-partition contexts)
      if (this.mockBroadcastChannel) {
        this.mockBroadcastChannel.postMessage(event);
      }
      this.ngZone.run(() => this.handleIncomingEvent(event));
      return;
    }

    const messageBody = JSON.stringify(body);
    const queuedMessage: QueuedMessage = {
      destination,
      body: messageBody,
      timestamp: Date.now(),
      retryCount: 0
    };

    if (this.client?.connected && !this.useFallbackPolling) {
      try {
        this.client.publish({ destination, body: messageBody });
        this.messagesSent++;
        this.updateHealth();
      } catch (error) {
        console.error('Failed to send message:', error);
        this.messageQueue.push(queuedMessage);
        this.messagesFailed++;
        this.updateHealth();
      }
    } else {
      this.messageQueue.push(queuedMessage);
    }
  }

  private processMessageQueue(): void {
    if (!this.client?.connected || this.messageQueue.length === 0) {
      return;
    }

    const messagesToSend = [...this.messageQueue];
    this.messageQueue = [];

    messagesToSend.forEach(msg => {
      if (msg.retryCount < 3) {
        try {
          this.client!.publish({ 
            destination: msg.destination, 
            body: msg.body 
          });
          this.messagesSent++;
        } catch (error) {
          msg.retryCount++;
          if (msg.retryCount < 3) {
            this.messageQueue.push(msg);
          } else {
            this.messagesFailed++;
          }
        }
      }
    });

    this.updateHealth();
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

  /** E2E-only: call the Playwright exposeFunction bridge to deliver to peer contexts. */
  private e2eBroadcast(event: CollaborationEvent): void {
    if (!this.isE2EMock()) return;
    const bridge = (window as any).__e2eBroadcastEvent;
    if (typeof bridge === 'function') {
      // exposeFunction calls are synchronous from the browser's perspective — the browser
      // awaits the Node.js response before the call returns, ensuring delivery completes
      // before the calling evaluate() resolves (critical for disconnect + context.close()).
      bridge(event).catch?.(() => {});
    }
  }

  /** E2E-only: inject an event as if received from the server (mock mode only). */
  e2eInjectEvent(event: CollaborationEvent): void {
    if (this.isE2EMock()) {
      this.ngZone.run(() => this.handleIncomingEvent(event));
    }
  }

  private handleIncomingEvent(event: CollaborationEvent): void {
    if (event.eventType === 'PONG') {
      this.recordPongReceived();
      return;
    }

    this.eventsSubject.next(event);

    const currentPresence = this.presenceSubject.value;

    switch (event.eventType) {
      case 'USER_JOIN':
        if (event.data?.activeUsers?.length > 0) {
          // Real backend sends the full list
          currentPresence.activeUsers = event.data.activeUsers;
        } else if (!currentPresence.activeUsers.includes(event.userId)) {
          // Mock/single-user event — add to existing list
          currentPresence.activeUsers = [...currentPresence.activeUsers, event.userId];
        }
        break;

      case 'USER_LEAVE':
        if (event.data?.activeUsers?.length > 0) {
          // Real backend sends the full list
          currentPresence.activeUsers = event.data.activeUsers;
        } else {
          // Mock/single-user event — remove from existing list
          currentPresence.activeUsers = currentPresence.activeUsers.filter(u => u !== event.userId);
        }
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

  private updateHealth(): void {
    const avgLatency = this.latencyMeasurements.length > 0 
      ? this.latencyMeasurements.reduce((a, b) => a + b, 0) / this.latencyMeasurements.length 
      : 0;
    
    const totalMessages = this.messagesSent + this.messagesFailed;
    const deliveryRate = totalMessages > 0 ? this.messagesSent / totalMessages : 1.0;
    
    const isHealthy = this.client?.connected || false;
    const qualityScore = this.calculateQualityScore(avgLatency, this.reconnectionAttempts, deliveryRate);
    const healthStatus = this.determineHealthStatus(qualityScore);

    this.healthSubject.next({
      latency: avgLatency,
      reconnectionCount: this.reconnectionAttempts,
      messageDeliveryRate: deliveryRate,
      isHealthy,
      qualityScore,
      healthStatus
    });
  }

  private calculateQualityScore(avgLatency: number, reconnections: number, deliveryRate: number): number {
    const latencyScore = this.calculateLatencyScore(avgLatency);
    const reconnectionScore = this.calculateReconnectionScore(reconnections);
    const deliveryScore = deliveryRate * 100;
    
    return (latencyScore * 0.4) + (reconnectionScore * 0.3) + (deliveryScore * 0.3);
  }

  private calculateLatencyScore(avgLatency: number): number {
    if (avgLatency === 0) return 100;
    if (avgLatency < 50) return 100;
    if (avgLatency < 100) return 90;
    if (avgLatency < 200) return 75;
    if (avgLatency < 500) return 50;
    if (avgLatency < 1000) return 25;
    return 10;
  }

  private calculateReconnectionScore(reconnectionCount: number): number {
    if (reconnectionCount === 0) return 100;
    if (reconnectionCount === 1) return 80;
    if (reconnectionCount === 2) return 60;
    if (reconnectionCount <= 5) return 40;
    if (reconnectionCount <= 10) return 20;
    return 5;
  }

  private determineHealthStatus(qualityScore: number): 'HEALTHY' | 'DEGRADED' | 'UNHEALTHY' {
    if (qualityScore >= 80) return 'HEALTHY';
    if (qualityScore >= 50) return 'DEGRADED';
    return 'UNHEALTHY';
  }

  private startLatencyChecks(): void {
    if (this.latencyCheckSubscription) {
      this.latencyCheckSubscription.unsubscribe();
    }

    this.latencyCheckSubscription = interval(10000).subscribe(() => {
      this.measureLatency();
    });

    this.measureLatency();
  }

  measureLatency(): void {
    if (!this.client?.connected) return;

    this.lastPingSent = Date.now();
    this.client.publish({
      destination: `/app/runs/${this.currentRunId}/ping`,
      body: JSON.stringify({ timestamp: this.lastPingSent })
    });
  }

  recordPongReceived(): void {
    if (this.lastPingSent > 0) {
      const latency = Date.now() - this.lastPingSent;
      this.latencyMeasurements.push(latency);
      
      if (this.latencyMeasurements.length > 20) {
        this.latencyMeasurements.shift();
      }
      
      this.updateHealth();
      this.lastPingSent = 0;
    }
  }

  private generateUserId(): string {
    const stored = localStorage.getItem('atlasia_user_id');
    if (stored) return stored;

    const id = 'user_' + Math.random().toString(36).substring(2, 11);
    localStorage.setItem('atlasia_user_id', id);
    return id;
  }

  getUserId(): string {
    return this.userId;
  }

  getQueuedMessageCount(): number {
    return this.messageQueue.length;
  }

  isUsingFallback(): boolean {
    return this.useFallbackPolling;
  }
}
