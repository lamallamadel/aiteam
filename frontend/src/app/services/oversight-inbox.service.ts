import { Injectable, OnDestroy, signal, computed, effect } from '@angular/core';
import { interval, Subscription } from 'rxjs';
import { OrchestratorService } from './orchestrator.service';
import { AuthService } from './auth.service';
import { PendingInterrupt, InterruptDecisionRequest } from '../models/orchestrator.model';

@Injectable({ providedIn: 'root' })
export class OversightInboxService implements OnDestroy {
  private pollSub: Subscription | null = null;

  readonly pendingInterrupts = signal<PendingInterrupt[]>([]);
  readonly interruptCount = computed(() => this.pendingInterrupts().length);

  constructor(
    private orchestratorService: OrchestratorService,
    private authService: AuthService
  ) {
    // Only start polling if user is logged in
    effect(() => {
      const user = this.authService.currentUser();
      if (user) {
        if (!this.pollSub) {
          this.startPolling();
        }
      } else {
        this.stopPolling();
        this.pendingInterrupts.set([]);
      }
    });
  }

  private startPolling() {
    this.fetchPending();
    this.pollSub = interval(5_000).subscribe(() => this.fetchPending());
  }

  private stopPolling() {
    this.pollSub?.unsubscribe();
    this.pollSub = null;
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
    this.stopPolling();
  }
}
