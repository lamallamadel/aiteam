import { Injectable, signal, computed } from '@angular/core';
import { Subscription } from 'rxjs';
import { SseService } from './sse.service';
import { WorkflowEvent } from '../models/orchestrator.model';
import { CollaborationWebSocketService, CollaborationEvent } from './collaboration-websocket.service';

@Injectable({ providedIn: 'root' })
export class WorkflowStreamStore {
  private subscription: Subscription | null = null;
  private collaborationSubscription: Subscription | null = null;
  private retryCount = 0;
  private readonly MAX_RETRIES = 3;
  private readonly RETRY_DELAYS = [2000, 4000, 8000];
  private retryTimeout: ReturnType<typeof setTimeout> | null = null;
  private currentRunId: string | null = null;

  // Core signals
  readonly events = signal<WorkflowEvent[]>([]);
  readonly currentAgent = signal<string | null>(null);
  readonly progress = signal<number>(0);
  readonly isStreaming = signal<boolean>(false);
  readonly lastError = signal<string | null>(null);
  readonly connectedRunId = signal<string | null>(null);
  
  // Collaboration signals
  readonly collaborationEvents = signal<CollaborationEvent[]>([]);
  readonly activeUsers = signal<string[]>([]);
  readonly cursorPositions = signal<Map<string, string>>(new Map());
  readonly isCollaborationConnected = signal<boolean>(false);
  readonly connectionHealth = signal<{
    latency: number;
    reconnectionCount: number;
    messageDeliveryRate: number;
    isHealthy: boolean;
  }>({ latency: 0, reconnectionCount: 0, messageDeliveryRate: 1.0, isHealthy: false });
  readonly queuedMessageCount = signal<number>(0);
  readonly usingFallback = signal<boolean>(false);

  // Computed signals
  readonly stepTimeline = computed(() =>
    this.events().filter(e =>
      e.eventType === 'STEP_START' || e.eventType === 'STEP_COMPLETE'
    )
  );

  readonly llmCalls = computed(() =>
    this.events().filter(e =>
      e.eventType === 'LLM_CALL_START' || e.eventType === 'LLM_CALL_END'
    )
  );

  readonly tokenConsumption = computed(() =>
    this.events()
      .filter(e => e.eventType === 'LLM_CALL_END')
      .reduce((sum, e) => sum + (e.tokensUsed || 0), 0)
  );

  readonly schemaValidations = computed(() =>
    this.events().filter(e => e.eventType === 'SCHEMA_VALIDATION')
  );

  readonly completedSteps = computed(() =>
    this.events().filter(e => e.eventType === 'STEP_COMPLETE').length
  );

  readonly hasError = computed(() =>
    this.events().some(e => e.eventType === 'WORKFLOW_ERROR')
  );

  readonly isEscalated = computed(() =>
    this.events().some(e => e.eventType === 'ESCALATION_RAISED')
  );

  constructor(
    private sseService: SseService,
    private collaborationService: CollaborationWebSocketService
  ) {
    // Subscribe to collaboration events
    this.collaborationSubscription = this.collaborationService.events$.subscribe(event => {
      if (event) {
        this.collaborationEvents.update(events => [...events, event]);
        this.handleCollaborationEvent(event);
      }
    });

    // Subscribe to presence updates
    this.collaborationService.presence$.subscribe(presence => {
      this.activeUsers.set(presence.activeUsers);
      this.cursorPositions.set(presence.cursors);
    });

    // Subscribe to connection status
    this.collaborationService.connected$.subscribe(connected => {
      this.isCollaborationConnected.set(connected);
    });

    // Subscribe to health metrics
    this.collaborationService.health$.subscribe(health => {
      this.connectionHealth.set(health);
    });

    // Poll queue and fallback status
    setInterval(() => {
      this.queuedMessageCount.set(this.collaborationService.getQueuedMessageCount());
      this.usingFallback.set(this.collaborationService.isUsingFallback());
    }, 1000);
  }

  connectToRun(runId: string, enableCollaboration: boolean = true): void {
    this.disconnect();
    this.reset();
    this.retryCount = 0;
    this.currentRunId = runId;
    this.connectInternal(runId);
    
    // Connect to collaboration WebSocket
    if (enableCollaboration) {
      this.collaborationService.connect(runId);
    }
  }

  private connectInternal(runId: string): void {
    this.connectedRunId.set(runId);
    this.isStreaming.set(true);

    this.subscription = this.sseService.connectToRun(runId).subscribe({
      next: (event: WorkflowEvent) => {
        this.events.update(events => [...events, event]);
        this.processEvent(event);
      },
      error: (err) => {
        this.lastError.set(err?.message || 'SSE connection lost');
        this.isStreaming.set(false);
        this.scheduleRetry(runId);
      },
      complete: () => {
        this.isStreaming.set(false);
      }
    });
  }

  private scheduleRetry(runId: string): void {
    if (this.retryCount >= this.MAX_RETRIES || this.currentRunId !== runId) return;
    const delay = this.RETRY_DELAYS[this.retryCount];
    this.retryCount++;
    this.lastError.set(`Connection lost — retrying in ${delay / 1000}s (attempt ${this.retryCount}/${this.MAX_RETRIES})…`);
    this.retryTimeout = setTimeout(() => {
      if (this.currentRunId === runId) {
        this.connectInternal(runId);
      }
    }, delay);
  }

  disconnect(): void {
    this.currentRunId = null;
    if (this.retryTimeout !== null) {
      clearTimeout(this.retryTimeout);
      this.retryTimeout = null;
    }
    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = null;
    }
    this.sseService.disconnect();
    this.collaborationService.disconnect();
    this.isStreaming.set(false);
  }

  reset(): void {
    this.events.set([]);
    this.currentAgent.set(null);
    this.progress.set(0);
    this.lastError.set(null);
    this.connectedRunId.set(null);
    this.collaborationEvents.set([]);
  }

  private processEvent(event: WorkflowEvent): void {
    switch (event.eventType) {
      case 'WORKFLOW_STATUS':
        this.currentAgent.set(event.currentAgent || null);
        this.progress.set(event.progressPercent || 0);
        break;
      case 'STEP_START':
        this.currentAgent.set(event.agentName || null);
        break;
      case 'WORKFLOW_ERROR':
        this.lastError.set(event.message || 'Unknown error');
        break;
      case 'ESCALATION_RAISED':
        this.lastError.set(`Escalation: ${event.reason || 'Unknown reason'}`);
        break;
    }
  }

  private handleCollaborationEvent(event: CollaborationEvent): void {
    // Handle collaboration events to sync state
    // This is where operational transformation logic would apply
    console.log('Collaboration event received:', event);
  }

  // Collaboration methods
  sendGraft(after: string, agentName: string): void {
    this.collaborationService.sendGraft(after, agentName);
  }

  sendPrune(stepId: string, isPruned: boolean): void {
    this.collaborationService.sendPrune(stepId, isPruned);
  }

  sendFlag(stepId: string, note?: string): void {
    this.collaborationService.sendFlag(stepId, note);
  }

  sendCursorMove(nodeId: string): void {
    this.collaborationService.sendCursorMove(nodeId);
  }

  getCurrentUserId(): string {
    return this.collaborationService.getUserId();
  }
}
