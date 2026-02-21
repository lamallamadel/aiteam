import { Injectable, signal, computed } from '@angular/core';
import { Subscription } from 'rxjs';
import { SseService } from './sse.service';
import { WorkflowEvent } from '../models/orchestrator.model';

@Injectable({ providedIn: 'root' })
export class WorkflowStreamStore {
  private subscription: Subscription | null = null;
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

  constructor(private sseService: SseService) {}

  connectToRun(runId: string): void {
    this.disconnect();
    this.reset();
    this.retryCount = 0;
    this.currentRunId = runId;
    this.connectInternal(runId);
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
    this.isStreaming.set(false);
  }

  reset(): void {
    this.events.set([]);
    this.currentAgent.set(null);
    this.progress.set(0);
    this.lastError.set(null);
    this.connectedRunId.set(null);
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
}
