import { Injectable, OnDestroy, signal, computed, effect } from '@angular/core';
import { interval, Subscription } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { OrchestratorService } from './orchestrator.service';
import { AuthService } from './auth.service';
import { RunResponse, ArtifactResponse } from '../models/orchestrator.model';

@Injectable({ providedIn: 'root' })
export class EscalationService implements OnDestroy {
  private pollSub: Subscription | null = null;

  readonly escalatedRuns = signal<RunResponse[]>([]);
  readonly escalationCount = computed(() => this.escalatedRuns().length);

  // Cached last-artifact per run id
  readonly lastArtifacts = signal<Record<string, ArtifactResponse | null>>({});

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
        this.escalatedRuns.set([]);
      }
    });
  }

  private startPolling() {
    // Immediate first fetch + poll every 10s
    this.fetchEscalated();
    this.pollSub = interval(10_000).subscribe(() => this.fetchEscalated());
  }

  private stopPolling() {
    this.pollSub?.unsubscribe();
    this.pollSub = null;
  }

  private fetchEscalated() {
    this.orchestratorService.getRuns().subscribe({
      next: (runs) => {
        const escalated = runs.filter(r => r.status === 'ESCALATED');
        setTimeout(() => {
          this.escalatedRuns.set(escalated);
        });
        // Fetch last artifact for any newly seen escalated run
        escalated.forEach(run => {
          const cached = this.lastArtifacts();
          if (!(run.id in cached)) {
            this.fetchLastArtifact(run.id);
          }
        });
      },
      error: () => {}
    });
  }

  private fetchLastArtifact(runId: string) {
    this.orchestratorService.getArtifacts(runId).subscribe({
      next: (artifacts) => {
        const last = artifacts.length ? artifacts[artifacts.length - 1] : null;
        setTimeout(() => {
          this.lastArtifacts.update(m => ({ ...m, [runId]: last }));
        });
      },
      error: () => {
        setTimeout(() => {
          this.lastArtifacts.update(m => ({ ...m, [runId]: null }));
        });
      }
    });
  }

  /** Force a refresh (call after resolving an escalation) */
  refresh() {
    this.fetchEscalated();
    // Clear artifact cache so they reload
    this.lastArtifacts.set({});
  }

  ngOnDestroy() {
    this.stopPolling();
  }
}
