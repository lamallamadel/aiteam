import { Injectable, OnDestroy, signal, computed } from '@angular/core';
import { interval, Subscription } from 'rxjs';
import { OrchestratorService } from './orchestrator.service';
import { PendingInterrupt, InterruptDecisionRequest } from '../models/orchestrator.model';

@Injectable({ providedIn: 'root' })
export class OversightInboxService implements OnDestroy {
  private pollSub: Subscription | null = null;

  readonly pendingInterrupts = signal<PendingInterrupt[]>([]);
  readonly interruptCount = computed(() => this.pendingInterrupts().length);

  constructor(private orchestratorService: OrchestratorService) {
    this.startPolling();
  }

  private startPolling() {
    this.fetchPending();
    this.pollSub = interval(5_000).subscribe(() => this.fetchPending());
  }

  private fetchPending() {
    this.orchestratorService.getPendingInterrupts().subscribe({
      next: (interrupts) => {
        setTimeout(() => {
          this.pendingInterrupts.set(interrupts);
        });
      },
      error: () => {}
    });
  }

  resolve(runId: string, decision: string, decidedBy: string, reason: string) {
    const request: InterruptDecisionRequest = { decision, decidedBy, reason };
    this.orchestratorService.resolveInterrupt(runId, request).subscribe({
      next: () => {
        this.refresh();
      },
      error: () => {}
    });
  }

  refresh() {
    this.fetchPending();
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }
}
