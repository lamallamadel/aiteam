import { Component, OnInit, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OrchestratorService } from '../../services/orchestrator.service';
import { EscalationService } from '../../services/escalation.service';
import { ArtifactRendererComponent } from '../artifact-renderer.component';
import { RunResponse, ArtifactResponse } from '../../models/orchestrator.model';

interface EnvContext {
  branchName?: string;
  ticketSummary?: string;
  architectureNotes?: string;
  capturedAt?: string;
  prUrl?: string;
}

// Suggested guidance templates per agent
const GUIDANCE_TEMPLATES: Record<string, string[]> = {
  PM:        ['Please clarify the acceptance criteria for this ticket.',
              'The user stories seem ambiguous — which persona is the primary user?'],
  QUALIFIER: ['The scope estimate seems too large. Break it into smaller tasks.',
              'Please confirm the priority level before committing engineering time.'],
  ARCHITECT: ['Please reconsider the technology choice and justify the tradeoff.',
              'The architecture has circular dependencies — please resolve before coding.'],
  DEVELOPER: ['The implementation should strictly follow the architecture notes.',
              'This change touches auth logic — please add extra security tests.',
              'Please use the existing abstractions rather than introducing new ones.'],
  REVIEW:    ['The security concern in the diff should be resolved before merging.',
              'Please add inline documentation for the public API surface.',
              'Address the performance regression flagged in the review report.'],
  TESTER:    ['Please investigate the flaky test before marking CI as green.',
              'Add regression tests for the edge cases listed in the ticket.'],
  WRITER:    ['The changelog entry is missing a breaking-change notice.',
              'Please add OpenAPI doc-comments to the new endpoints.'],
};

@Component({
  selector: 'app-escalation-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, ArtifactRendererComponent],
  template: `
    <div class="oversight-container">
      <div class="header">
        <div class="header-row">
          <h2>Oversight Panel</h2>
          <span *ngIf="escalationService.escalationCount() > 0"
                class="count-badge">
            {{ escalationService.escalationCount() }}
          </span>
        </div>
        <p class="subtitle">Bolts requiring human intervention</p>
        <div class="header-actions">
          <button class="btn-refresh" (click)="refresh()" [class.spinning]="refreshing()">⟳ Refresh</button>
        </div>
      </div>

      <div *ngIf="loading()" class="loading-state glass-panel">Loading escalated bolts...</div>

      <div *ngIf="!loading() && escalationService.escalatedRuns().length === 0"
           class="empty-state glass-panel">
        <div class="empty-icon">✓</div>
        <h3>All clear</h3>
        <p>No bolts require human intervention right now.</p>
        <p class="poll-hint">Auto-refreshing every 10s</p>
      </div>

      <div *ngFor="let run of escalationService.escalatedRuns()" class="escalation-card glass-panel">
        <!-- Card header -->
        <div class="escalation-header">
          <div class="run-info">
            <span class="status-badge escalated">ESCALATED</span>
            <span class="run-id">{{ run.repo }} <span class="issue-num">#{{ run.issueNumber }}</span></span>
          </div>
          <div class="header-right">
            <span class="agent-badge">{{ run.currentAgent || 'Unknown' }}</span>
            <span class="run-time">{{ formatAge(run.updatedAt) }}</span>
          </div>
        </div>

        <!-- Context: last artifact -->
        <div class="context-section" *ngIf="getLastArtifact(run.id) as artifact">
          <div class="context-header">
            <span class="context-label">Last artifact</span>
            <span class="artifact-type-badge">{{ artifact.artifactType }}</span>
          </div>
          <div class="artifact-wrap">
            <app-artifact-renderer [artifact]="artifact"></app-artifact-renderer>
          </div>
        </div>

        <!-- Environment snapshot context -->
        <div class="env-context-section" *ngIf="getEnvContext(run.id) as env">
          <div class="context-header">
            <span class="context-label">Environment snapshot</span>
            <span *ngIf="env.capturedAt" class="snapshot-age">{{ formatAge(env.capturedAt) }}</span>
          </div>
          <div class="env-grid">
            <ng-container *ngIf="env.branchName">
              <span class="env-key">Branch</span>
              <span class="env-val mono">{{ env.branchName }}</span>
            </ng-container>
            <ng-container *ngIf="env.prUrl">
              <span class="env-key">PR</span>
              <span class="env-val mono pr-link">{{ env.prUrl }}</span>
            </ng-container>
            <ng-container *ngIf="env.ticketSummary">
              <span class="env-key">Ticket</span>
              <span class="env-val">{{ env.ticketSummary }}</span>
            </ng-container>
            <ng-container *ngIf="env.architectureNotes">
              <span class="env-key">Arch notes</span>
              <span class="env-val arch-notes">{{ env.architectureNotes | slice:0:200 }}{{ env.architectureNotes.length > 200 ? '…' : '' }}</span>
            </ng-container>
          </div>
        </div>

        <!-- Escalation reason -->
        <div class="reason-section">
          <span class="reason-icon">⚠</span>
          <p class="escalation-reason">
            Agent <strong>{{ run.currentAgent }}</strong> requires a decision to proceed.
          </p>
        </div>

        <!-- Guidance section -->
        <div class="guidance-section">
          <div class="guidance-header">
            <label>Guidance</label>
            <!-- Template chips -->
            <div class="template-chips" *ngIf="getTemplates(run.currentAgent).length">
              <button *ngFor="let tpl of getTemplates(run.currentAgent)"
                      class="template-chip"
                      (click)="applyTemplate(run.id, tpl)">
                {{ tpl.substring(0, 40) }}{{ tpl.length > 40 ? '…' : '' }}
              </button>
            </div>
          </div>
          <textarea
            class="guidance-input glass-panel"
            [(ngModel)]="guidance[run.id]"
            placeholder="Provide context or instructions for the agent..."
            rows="3"
          ></textarea>
        </div>

        <!-- Actions -->
        <div class="action-buttons">
          <button class="btn btn-proceed"
                  [disabled]="resolving[run.id]"
                  (click)="resolveEscalation(run.id, 'PROCEED')">
            {{ resolving[run.id] ? 'Sending…' : 'Proceed' }}
          </button>
          <button class="btn btn-abort"
                  [disabled]="resolving[run.id]"
                  (click)="resolveEscalation(run.id, 'ABORT')">
            Abort Bolt
          </button>
          <button class="btn btn-detail" (click)="viewDetail(run.id)">
            View Details
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .oversight-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      height: 100%;
      overflow-y: auto;
    }

    /* Header */
    .header-row { display: flex; align-items: center; gap: 10px; }
    .header h2 { font-size: 1.8rem; margin-bottom: 4px; }
    .subtitle { color: rgba(255,255,255,0.5); font-size: 0.9rem; }
    .header-actions { margin-top: 8px; }
    .count-badge {
      background: rgba(234,179,8,0.2);
      color: #eab308;
      border: 1px solid rgba(234,179,8,0.3);
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.8rem;
      font-weight: 700;
    }
    .btn-refresh {
      background: transparent;
      border: 1px solid rgba(255,255,255,0.1);
      color: rgba(255,255,255,0.5);
      padding: 5px 12px;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.8rem;
      transition: all 0.2s;
    }
    .btn-refresh:hover { border-color: rgba(56,189,248,0.3); color: #38bdf8; }
    .btn-refresh.spinning { animation: spin 0.8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

    /* Empty / Loading states */
    .empty-state { text-align: center; padding: 48px 24px; }
    .empty-icon { font-size: 3rem; margin-bottom: 12px; }
    .empty-state h3 { margin-bottom: 8px; }
    .empty-state p { color: rgba(255,255,255,0.5); }
    .poll-hint { font-size: 0.75rem; margin-top: 8px; }
    .loading-state { padding: 24px; text-align: center; color: rgba(255,255,255,0.5); }

    /* Escalation card */
    .escalation-card { padding: 20px; display: flex; flex-direction: column; gap: 14px; }
    .escalation-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      flex-wrap: wrap;
      gap: 8px;
    }
    .run-info { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .header-right { display: flex; align-items: center; gap: 8px; }
    .status-badge {
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .status-badge.escalated { background: rgba(234,179,8,0.2); color: #eab308; }
    .run-id { font-weight: 600; font-size: 0.95rem; }
    .issue-num { color: #38bdf8; }
    .agent-badge {
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      padding: 3px 10px;
      border-radius: 8px;
      font-size: 0.78rem;
      font-weight: 700;
    }
    .run-time { font-size: 0.72rem; color: rgba(255,255,255,0.3); }

    /* Context section */
    .context-section {
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.05);
      border-radius: 8px;
      overflow: hidden;
    }
    .context-header {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      border-bottom: 1px solid rgba(255,255,255,0.04);
      background: rgba(255,255,255,0.02);
    }
    .context-label { font-size: 0.7rem; color: rgba(255,255,255,0.35); font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; }
    .artifact-type-badge {
      font-size: 0.68rem;
      padding: 1px 6px;
      background: rgba(139,92,246,0.15);
      color: #8b5cf6;
      border-radius: 5px;
    }
    .artifact-wrap { max-height: 280px; overflow-y: auto; }

    /* Environment context */
    .env-context-section {
      background: rgba(56,189,248,0.03);
      border: 1px solid rgba(56,189,248,0.1);
      border-radius: 8px;
      overflow: hidden;
    }
    .snapshot-age { margin-left: auto; font-size: 0.68rem; color: rgba(255,255,255,0.25); }
    .env-grid {
      display: grid;
      grid-template-columns: 90px 1fr;
      gap: 5px 12px;
      padding: 10px 12px;
      align-items: baseline;
    }
    .env-key {
      font-size: 0.68rem;
      font-weight: 700;
      color: rgba(255,255,255,0.3);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      white-space: nowrap;
    }
    .env-val {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.7);
      line-height: 1.4;
    }
    .env-val.mono { font-family: monospace; color: #38bdf8; font-size: 0.75rem; }
    .env-val.pr-link { color: rgba(56,189,248,0.7); word-break: break-all; }
    .env-val.arch-notes { color: rgba(255,255,255,0.55); font-size: 0.75rem; }

    /* Reason */
    .reason-section { display: flex; align-items: flex-start; gap: 10px; }
    .reason-icon { font-size: 1.1rem; color: #eab308; flex-shrink: 0; }
    .escalation-reason { margin: 0; color: rgba(255,255,255,0.7); font-size: 0.9rem; }
    .escalation-reason strong { color: white; }

    /* Guidance */
    .guidance-section { display: flex; flex-direction: column; gap: 6px; }
    .guidance-header {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }
    .guidance-header label {
      font-size: 0.82rem;
      color: rgba(255,255,255,0.5);
      white-space: nowrap;
    }
    .template-chips { display: flex; gap: 4px; flex-wrap: wrap; }
    .template-chip {
      padding: 3px 8px;
      background: rgba(56,189,248,0.06);
      border: 1px solid rgba(56,189,248,0.15);
      border-radius: 6px;
      color: rgba(56,189,248,0.7);
      font-size: 0.68rem;
      cursor: pointer;
      transition: all 0.15s;
      text-align: left;
    }
    .template-chip:hover { background: rgba(56,189,248,0.12); border-color: rgba(56,189,248,0.3); color: #38bdf8; }
    .guidance-input {
      width: 100%;
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 8px;
      padding: 10px;
      color: white;
      font-size: 0.88rem;
      resize: vertical;
      box-sizing: border-box;
      transition: border-color 0.2s;
    }
    .guidance-input:focus { border-color: rgba(56,189,248,0.4); outline: none; }

    /* Action buttons */
    .action-buttons { display: flex; gap: 8px; flex-wrap: wrap; }
    .btn {
      padding: 8px 18px;
      border: none;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 700;
      font-size: 0.85rem;
      transition: opacity 0.2s;
    }
    .btn:hover:not(:disabled) { opacity: 0.85; }
    .btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .btn-proceed { background: #22c55e; color: white; }
    .btn-abort   { background: #ef4444; color: white; }
    .btn-detail  {
      background: rgba(255,255,255,0.06);
      color: rgba(255,255,255,0.7);
      border: 1px solid rgba(255,255,255,0.1);
    }
  `]
})
export class EscalationPanelComponent implements OnInit {
  readonly escalationService = inject(EscalationService);
  private orchestratorService = inject(OrchestratorService);
  private router = inject(Router);

  loading = signal(true);
  refreshing = signal(false);
  guidance: Record<string, string> = {};
  resolving: Record<string, boolean> = {};
  private envContexts = signal<Record<string, EnvContext | null>>({});

  ngOnInit() {
    setTimeout(() => {
      this.loading.set(false);
      this.fetchEnvContexts();
    }, 500);
  }

  getLastArtifact(runId: string): ArtifactResponse | null {
    return this.escalationService.lastArtifacts()[runId] ?? null;
  }

  getEnvContext(runId: string): EnvContext | null {
    return this.envContexts()[runId] ?? null;
  }

  private fetchEnvContexts() {
    this.escalationService.escalatedRuns().forEach(run => {
      this.orchestratorService.getEnvironment(run.id).subscribe({
        next: (json) => {
          try {
            const snap = JSON.parse(json);
            const ctx: EnvContext = {
              branchName: snap.branchName,
              ticketSummary: snap.ticketPlan
                ? snap.ticketPlan.split('\n')[0].substring(0, 120)
                : undefined,
              architectureNotes: snap.architectureNotes,
              capturedAt: snap.capturedAt,
              prUrl: snap.prUrl,
            };
            this.envContexts.update(m => ({ ...m, [run.id]: ctx }));
          } catch { /* no valid snapshot */ }
        },
        error: () => { /* no checkpoint yet — skip */ }
      });
    });
  }

  getTemplates(agentName: string | undefined): string[] {
    if (!agentName) return [];
    return GUIDANCE_TEMPLATES[agentName.toUpperCase()] ?? [];
  }

  applyTemplate(runId: string, tpl: string) {
    this.guidance[runId] = tpl;
  }

  refresh() {
    this.refreshing.set(true);
    this.escalationService.refresh();
    setTimeout(() => {
      this.refreshing.set(false);
      this.fetchEnvContexts();
    }, 800);
  }

  resolveEscalation(runId: string, decision: string) {
    this.resolving[runId] = true;
    this.orchestratorService.resolveEscalation(runId, decision, this.guidance[runId]).subscribe({
      next: () => {
        this.resolving[runId] = false;
        delete this.guidance[runId];
        this.escalationService.refresh();
      },
      error: () => { this.resolving[runId] = false; }
    });
  }

  viewDetail(runId: string) {
    this.router.navigate(['/runs', runId]);
  }

  formatAge(dateStr: string | undefined): string {
    if (!dateStr) return '';
    const diff = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1)  return 'just now';
    if (mins < 60) return `${mins}m ago`;
    return `${Math.floor(mins / 60)}h ago`;
  }
}
