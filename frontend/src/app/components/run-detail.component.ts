import { Component, OnInit, OnDestroy, signal, computed, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { WorkflowStreamStore } from '../services/workflow-stream.store';
import { NeuralTraceComponent, GraftEvent } from './neural-trace.component';
import { RunResponse, ArtifactResponse, EnvironmentLifecycle } from '../models/orchestrator.model';
import { ArtifactRendererComponent } from './artifact-renderer.component';
import { CollaborationNotificationsComponent } from './collaboration-notifications.component';

@Component({
  selector: 'app-run-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, NeuralTraceComponent, ArtifactRendererComponent, CollaborationNotificationsComponent],
  template: `
    <app-collaboration-notifications></app-collaboration-notifications>
    <div class="run-detail-container" *ngIf="run()">
      <div class="header">
        <button (click)="goBack()" class="btn-back">← Back to Runs</button>
        <div class="header-title">
          <h2>{{ run()?.repo }} <span class="issue-num">#{{ run()?.issueNumber }}</span></h2>
          <span class="status-badge" [ngClass]="getStatusClass(run()?.status)">
            {{ run()?.status }}
          </span>
        </div>
        <div class="header-actions">
          <a [routerLink]="['/runs', run()?.id, 'trace']" class="btn-action">
            Waterfall Trace
          </a>
          <a [routerLink]="['/runs', run()?.id, 'log']" class="btn-action">
            Activity Log
          </a>
          <button
            *ngIf="isResumeAvailable()"
            class="btn-resume"
            [disabled]="resuming()"
            (click)="resumeRun()">
            {{ resuming() ? 'Resuming...' : 'Resume Run' }}
          </button>
        </div>
      </div>

      <!-- Live neural trace — feeds directly from SSE stream -->
      <app-neural-trace
        [steps]="streamStore.stepTimeline()"
        [interactive]="true"
        [showPresence]="streamStore.isCollaborationConnected()"
        [activeUsers]="streamStore.activeUsers()"
        [cursorPositions]="streamStore.cursorPositions()"
        (flagged)="onNodeFlagged($event)"
        (pruned)="onNodePruned($event)"
        (grafted)="onNodeGrafted($event)"
        (cursorMoved)="onCursorMoved($event)">
      </app-neural-trace>

      <!-- Pending grafts notice -->
      <div class="grafts-panel" *ngIf="pendingGrafts().length > 0">
        <span class="grafts-label">⊕ Pending Grafts</span>
        <div class="graft-chips">
          <span *ngFor="let g of pendingGrafts()" class="graft-chip">
            after {{ g.after }}: <strong>{{ g.agentName }}</strong>
          </span>
        </div>
      </div>

      <!-- Live progress bar -->
      <div class="progress-bar-wrap" *ngIf="streamStore.isStreaming()">
        <div class="progress-label">
          <span class="pulse-dot"></span>
          <span>{{ streamStore.currentAgent() || 'Initializing' }}</span>
          <span class="progress-pct">{{ streamStore.progress() }}%</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" [style.width.%]="streamStore.progress()"></div>
        </div>
        <div class="stream-stats">
          <span>{{ streamStore.completedSteps() }} steps complete</span>
          <span>{{ streamStore.tokenConsumption() | number }} tokens</span>
          <span>{{ streamStore.llmCalls().length / 2 | number:'1.0-0' }} LLM calls</span>
        </div>
      </div>

      <!-- Escalation banner -->
      <div class="escalation-banner" *ngIf="run()?.status === 'ESCALATED'">
        <span class="escalation-icon">⚠</span>
        <div class="escalation-text">
          <strong>Human decision required</strong>
          <span>Agent <em>{{ run()?.currentAgent }}</em> is blocked and needs guidance.</span>
        </div>
        <a [routerLink]="['/oversight']" class="btn-escalation">Go to Oversight Panel</a>
      </div>

      <div class="detail-grid">
        <!-- Run info -->
        <div class="info-card">
          <h3>Run Information</h3>
          <div class="info-row">
            <span class="label">ID</span>
            <span class="value mono">{{ run()?.id?.substring(0, 8) }}…</span>
          </div>
          <div class="info-row">
            <span class="label">Status</span>
            <span class="status-badge sm" [ngClass]="getStatusClass(run()?.status)">{{ run()?.status }}</span>
          </div>
          <div class="info-row">
            <span class="label">Current Agent</span>
            <span class="value agent-chip">{{ liveAgent() || run()?.currentAgent || '—' }}</span>
          </div>
          <div class="info-row">
            <span class="label">CI Fixes</span>
            <span class="value">{{ run()?.ciFixCount || 0 }}</span>
          </div>
          <div class="info-row">
            <span class="label">E2E Fixes</span>
            <span class="value">{{ run()?.e2eFixCount || 0 }}</span>
          </div>
          <div class="info-row">
            <span class="label">Created</span>
            <span class="value">{{ formatDate(run()?.createdAt) }}</span>
          </div>
          <div class="info-row">
            <span class="label">Updated</span>
            <span class="value">{{ formatDate(run()?.updatedAt) }}</span>
          </div>

          <!-- Environment lifecycle section -->
          <div class="env-section">
            <h4>Environment</h4>
            <div class="info-row">
              <span class="label">Lifecycle</span>
              <span class="lifecycle-badge" [ngClass]="'lifecycle-' + (envLifecycle() || 'active').toLowerCase()">
                {{ envLifecycle() || 'ACTIVE' }}
              </span>
            </div>
            <div class="info-row" *ngIf="envCheckpointTime()">
              <span class="label">Checkpoint</span>
              <span class="value">{{ envCheckpointTime() }}</span>
            </div>
            <div class="env-actions" *ngIf="isResumeAvailable()">
              <p class="paused-hint">This run was paused with a saved environment checkpoint.</p>
              <button class="btn-resume inline" [disabled]="resuming()" (click)="resumeRun()">
                {{ resuming() ? 'Resuming…' : '↺ Resume from Checkpoint' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Artifacts -->
        <div class="artifacts-card">
          <h3>Artifacts ({{ allArtifacts().length }})</h3>
          <div class="artifacts-list">
            <div *ngFor="let artifact of allArtifacts()" class="artifact-item"
                 (click)="loadArtifactPayload(artifact.id)">
              <div class="artifact-header">
                <span class="artifact-type">{{ artifact.artifactType }}</span>
                <span class="artifact-agent">{{ artifact.agentName }}</span>
              </div>
              <div class="artifact-payload" *ngIf="expandedArtifact() === artifact.id && expandedArtifactObj()">
                <app-artifact-renderer [artifact]="expandedArtifactObj()!"></app-artifact-renderer>
              </div>
              <div class="artifact-meta">
                <span class="artifact-id mono">{{ artifact.id.substring(0, 8) }}</span>
                <span class="artifact-date">{{ formatDate(artifact.createdAt) }}</span>
              </div>
            </div>
            <div *ngIf="allArtifacts().length === 0" class="empty-state">
              No artifacts yet
            </div>
          </div>
        </div>
      </div>

      <!-- Token monitor row -->
      <div class="token-row" *ngIf="streamStore.tokenConsumption() > 0">
        <div class="token-stat">
          <span class="token-label">Total Tokens</span>
          <span class="token-value">{{ streamStore.tokenConsumption() | number }}</span>
        </div>
        <div class="token-stat">
          <span class="token-label">LLM Calls</span>
          <span class="token-value">{{ (streamStore.llmCalls().length / 2) | number:'1.0-0' }}</span>
        </div>
        <div class="token-stat">
          <span class="token-label">Schema Checks</span>
          <span class="token-value">{{ streamStore.schemaValidations().length }}</span>
        </div>
        <div class="token-stat">
          <span class="token-label">Steps Done</span>
          <span class="token-value">{{ streamStore.completedSteps() }}</span>
        </div>
      </div>
    </div>

    <div class="loading-state" *ngIf="!run() && !error()">
      <div class="spinner"></div>
      <p>Loading run details…</p>
    </div>

    <div class="error-state" *ngIf="error()">
      <p>{{ error() }}</p>
      <button (click)="goBack()" class="btn-back">Back to Runs</button>
    </div>
  `,
  styles: [`
    .run-detail-container {
      padding: 24px;
      height: 100%;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .header {
      display: flex;
      align-items: center;
      gap: 16px;
      flex-wrap: wrap;
    }
    .header-title {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .header-title h2 { margin: 0; color: white; font-size: 1.4rem; }
    .issue-num { color: #38bdf8; }
    .header-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .btn-back, .btn-action {
      padding: 8px 16px;
      border: 1px solid var(--border);
      border-radius: 6px;
      background: var(--surface);
      color: white;
      cursor: pointer;
      text-decoration: none;
      font-size: 0.85rem;
      transition: background 0.2s;
      white-space: nowrap;
    }
    .btn-back:hover, .btn-action:hover { background: rgba(56,189,248,0.2); }
    .btn-resume {
      padding: 8px 16px;
      border: none;
      border-radius: 6px;
      background: #22c55e;
      color: white;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.85rem;
      transition: opacity 0.2s;
    }
    .btn-resume:disabled { opacity: 0.6; cursor: not-allowed; }
    .btn-resume:not(:disabled):hover { opacity: 0.85; }
    .btn-resume.inline { margin-top: 8px; width: 100%; }

    /* Progress bar */
    .progress-bar-wrap {
      padding: 12px 16px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
    }
    .progress-label {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.85rem;
      color: #38bdf8;
      font-weight: 600;
    }
    .progress-pct { margin-left: auto; color: rgba(255,255,255,0.6); }
    .pulse-dot {
      width: 7px; height: 7px;
      background: #22c55e;
      border-radius: 50%;
      animation: blink 1s infinite;
    }
    @keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }
    .progress-track {
      height: 4px;
      background: rgba(255,255,255,0.08);
      border-radius: 2px;
      overflow: hidden;
    }
    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #38bdf8, #22c55e);
      border-radius: 2px;
      transition: width 0.4s ease;
    }
    .stream-stats {
      display: flex;
      gap: 16px;
      font-size: 0.75rem;
      color: rgba(255,255,255,0.4);
    }

    /* Escalation banner */
    .escalation-banner {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      border: 1px solid rgba(234,179,8,0.3);
      background: rgba(234,179,8,0.08);
      border-radius: 12px;
    }
    .escalation-icon { font-size: 1.5rem; color: #eab308; }
    .escalation-text { flex: 1; display: flex; flex-direction: column; gap: 2px; }
    .escalation-text strong { color: #eab308; }
    .escalation-text span { font-size: 0.85rem; color: rgba(255,255,255,0.6); }
    .btn-escalation {
      padding: 8px 16px;
      background: #eab308;
      color: #000;
      border-radius: 6px;
      text-decoration: none;
      font-weight: 600;
      font-size: 0.85rem;
    }

    /* Detail grid */
    .detail-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    .info-card, .artifacts-card {
      padding: 20px;
      border-radius: 12px;
      background: var(--surface);
      border: 1px solid var(--border);
    }
    .info-card h3, .artifacts-card h3 {
      margin: 0 0 16px 0;
      color: white;
      font-size: 1rem;
      font-weight: 600;
    }
    .info-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 0;
      border-bottom: 1px solid rgba(255,255,255,0.05);
      gap: 8px;
    }
    .info-row:last-child { border-bottom: none; }
    .label { color: #94a3b8; font-size: 0.85rem; white-space: nowrap; }
    .value { color: white; font-weight: 500; font-size: 0.85rem; text-align: right; }
    .mono { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
    .agent-chip {
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      padding: 2px 8px;
      border-radius: 8px;
      font-size: 0.8rem;
    }

    /* Status badges */
    .status-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .status-badge.sm { padding: 2px 8px; font-size: 0.7rem; }
    .done { background: rgba(34,197,94,0.2); color: #22c55e; }
    .failed { background: rgba(239,68,68,0.2); color: #ef4444; }
    .escalated { background: rgba(234,179,8,0.2); color: #eab308; }
    .in-progress { background: rgba(56,189,248,0.2); color: #38bdf8; }

    /* Lifecycle badges */
    .env-section { margin-top: 16px; padding-top: 16px; border-top: 1px solid rgba(255,255,255,0.07); }
    .env-section h4 { margin: 0 0 12px 0; color: rgba(255,255,255,0.5); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.08em; }
    .lifecycle-badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 8px;
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .lifecycle-active { background: rgba(34,197,94,0.15); color: #22c55e; }
    .lifecycle-paused { background: rgba(251,191,36,0.15); color: #fbbf24; }
    .lifecycle-handed_off { background: rgba(139,92,246,0.15); color: #8b5cf6; }
    .lifecycle-completed { background: rgba(56,189,248,0.15); color: #38bdf8; }
    .paused-hint { font-size: 0.8rem; color: rgba(255,255,255,0.5); margin: 8px 0 4px; }
    .env-actions { margin-top: 4px; }

    /* Artifacts */
    .artifacts-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-height: 480px;
      overflow-y: auto;
    }
    .artifact-item {
      padding: 10px 12px;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.05);
      border-radius: 8px;
      cursor: pointer;
      transition: border-color 0.2s;
    }
    .artifact-item:hover { border-color: rgba(56,189,248,0.3); }
    .artifact-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;
    }
    .artifact-type { color: white; font-weight: 600; font-size: 0.85rem; }
    .artifact-agent {
      padding: 2px 8px;
      background: rgba(139,92,246,0.2);
      color: #8b5cf6;
      border-radius: 8px;
      font-size: 0.72rem;
    }
    .artifact-payload { margin: 8px 0; }
    .payload-pre {
      background: rgba(0,0,0,0.3);
      border-radius: 6px;
      padding: 10px;
      font-size: 0.75rem;
      color: #94a3b8;
      overflow-x: auto;
      max-height: 200px;
      white-space: pre-wrap;
      word-break: break-all;
      margin: 0;
    }
    .artifact-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.72rem;
      color: #64748b;
    }

    /* Pending grafts panel */
    .grafts-panel {
      padding: 10px 16px;
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
    }
    .grafts-label {
      font-size: 0.75rem;
      font-weight: 700;
      color: #a78bfa;
      white-space: nowrap;
    }
    .graft-chips { display: flex; gap: 6px; flex-wrap: wrap; }
    .graft-chip {
      font-size: 0.72rem;
      padding: 2px 8px;
      background: rgba(139,92,246,0.12);
      border: 1px solid rgba(139,92,246,0.25);
      color: rgba(255,255,255,0.65);
      border-radius: 8px;
    }
    .graft-chip strong { color: #a78bfa; }

    /* Token row */
    .token-row {
      display: flex;
      justify-content: space-around;
      padding: 14px 20px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
    }
    .token-stat { text-align: center; }
    .token-label { display: block; font-size: 0.72rem; color: rgba(255,255,255,0.4); margin-bottom: 4px; }
    .token-value { font-size: 1.1rem; font-weight: 700; color: white; font-family: var(--font-mono); font-variant-numeric: tabular-nums; }

    /* Loading / error */
    .loading-state, .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: #94a3b8;
      gap: 16px;
    }
    .spinner {
      width: 40px; height: 40px;
      border: 3px solid rgba(56,189,248,0.2);
      border-top-color: #38bdf8;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .empty-state { text-align: center; padding: 24px; color: #94a3b8; }

    @media (max-width: 768px) {
      .detail-grid { grid-template-columns: 1fr; }
      .token-row { flex-wrap: wrap; gap: 16px; }
    }
  `]
})
export class RunDetailComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private orchestratorService = inject(OrchestratorService);
  streamStore = inject(WorkflowStreamStore);

  run = signal<RunResponse | null>(null);
  error = signal<string | null>(null);
  resuming = signal(false);
  envLifecycle = signal<EnvironmentLifecycle | null>(null);
  envCheckpointTime = signal<string | null>(null);
  prunedSteps = signal<string[]>([]);
  pendingGrafts = signal<{ after: string; agentName: string }[]>([]);

  constructor() {
    // Reload artifacts whenever a new step completes during a live run
    effect(() => {
      const count = this.streamStore.completedSteps();
      if (count > 0) {
        const id = this.run()?.id;
        if (id) this.loadArtifacts(id);
      }
    });
  }

  // Artifacts from REST (includes artifacts added before the stream connected)
  private restArtifacts = signal<ArtifactResponse[]>([]);
  expandedArtifact = signal<string | null>(null);
  expandedArtifactObj = signal<ArtifactResponse | null>(null);

  // Merge REST artifacts with real-time completed-step artifact summaries
  allArtifacts = computed<ArtifactResponse[]>(() => this.restArtifacts());

  // Live current agent from SSE (overrides static run data while streaming)
  liveAgent = computed(() =>
    this.streamStore.isStreaming() ? this.streamStore.currentAgent() : null
  );

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.error.set('No run ID provided'); return; }
    this.loadRun(id);
  }

  ngOnDestroy() {
    this.streamStore.disconnect();
  }

  private loadRun(id: string) {
    this.orchestratorService.getRun(id).subscribe({
      next: (run) => {
        this.run.set(run);
        this.loadArtifacts(id);
        this.loadEnvironment(id);

        // Connect SSE for live updates if run is in progress
        const activeStatuses = ['RECEIVED', 'PM', 'QUALIFIER', 'ARCHITECT', 'DEVELOPER',
                                'REVIEW', 'TESTER', 'WRITER'];
        if (activeStatuses.includes(run.status)) {
          this.streamStore.connectToRun(id);
        }
      },
      error: () => this.error.set('Failed to load run details')
    });
  }

  private loadArtifacts(id: string) {
    this.orchestratorService.getArtifacts(id).subscribe({
      next: (artifacts) => this.restArtifacts.set(artifacts),
      error: () => {}
    });
  }

  private loadEnvironment(id: string) {
    this.orchestratorService.getEnvironment(id).subscribe({
      next: (json) => {
        try {
          const snapshot = JSON.parse(json);
          this.envLifecycle.set(snapshot.lifecycle || 'ACTIVE');
          this.envCheckpointTime.set(snapshot.capturedAt
            ? this.formatDate(snapshot.capturedAt) : null);
        } catch { /* no checkpoint yet */ }
      },
      error: () => {} // 204 No Content is expected for runs with no checkpoint
    });
  }

  isResumeAvailable(): boolean {
    const status = this.run()?.status;
    return status === 'FAILED' || status === 'ESCALATED';
  }

  resumeRun() {
    const id = this.run()?.id;
    if (!id) return;
    this.resuming.set(true);
    this.orchestratorService.resumeRun(id).subscribe({
      next: (run) => {
        this.run.set(run);
        this.resuming.set(false);
        this.streamStore.connectToRun(id);
      },
      error: () => this.resuming.set(false)
    });
  }

  loadArtifactPayload(artifactId: string) {
    if (this.expandedArtifact() === artifactId) {
      this.expandedArtifact.set(null);
      this.expandedArtifactObj.set(null);
      return;
    }
    const local = this.restArtifacts().find(a => a.id === artifactId);
    if (local?.payload) {
      this.expandedArtifact.set(artifactId);
      this.expandedArtifactObj.set(local);
      return;
    }
    const runId = this.run()?.id;
    if (!runId) return;
    this.orchestratorService.getArtifacts(runId).subscribe({
      next: (artifacts) => {
        this.restArtifacts.set(artifacts);
        const found = artifacts.find(a => a.id === artifactId);
        if (found) {
          this.expandedArtifact.set(artifactId);
          this.expandedArtifactObj.set(found);
        }
      }
    });
  }

  getStatusClass(status: string | undefined): string {
    if (!status) return '';
    switch (status) {
      case 'DONE': return 'done';
      case 'FAILED': return 'failed';
      case 'ESCALATED': return 'escalated';
      default: return 'in-progress';
    }
  }

  formatDate(dateStr: string | undefined): string {
    if (!dateStr) return 'N/A';
    return new Date(dateStr).toLocaleString();
  }

  onNodeFlagged(stepId: string) {
    const id = this.run()?.id;
    if (!id) return;
    // Send via WebSocket for real-time collaboration
    this.streamStore.sendFlag(stepId);
    // Also persist via REST
    this.orchestratorService.flagNode(id, stepId).subscribe();
  }

  onNodePruned(stepId: string) {
    const id = this.run()?.id;
    if (!id) return;
    const current = this.prunedSteps();
    const isPruned = !current.includes(stepId);
    const updated = isPruned
      ? [...current, stepId]
      : current.filter(s => s !== stepId);
    this.prunedSteps.set(updated);
    // Send via WebSocket for real-time collaboration
    this.streamStore.sendPrune(stepId, isPruned);
    // Also persist via REST
    this.orchestratorService.setPrunedSteps(id, updated.join(',')).subscribe();
  }

  onNodeGrafted(event: GraftEvent) {
    const id = this.run()?.id;
    if (!id) return;
    this.pendingGrafts.update(g => [...g, { after: event.after, agentName: event.agentName }]);
    // Send via WebSocket for real-time collaboration
    this.streamStore.sendGraft(event.after, event.agentName);
    // Also persist via REST
    this.orchestratorService.addGraft(id, event.after, event.agentName).subscribe();
  }

  onCursorMoved(nodeId: string) {
    this.streamStore.sendCursorMove(nodeId);
  }

  goBack() { this.router.navigate(['/runs']); }
}
