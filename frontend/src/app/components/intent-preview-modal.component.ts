import { Component, Input, Output, EventEmitter, OnInit, OnChanges, SimpleChanges, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrchestratorService } from '../services/orchestrator.service';
import { RunRequest, AgentCard } from '../models/orchestrator.model';

// Pipeline step definitions — fixed order
const PIPELINE_STEPS = [
  { role: 'PM',        label: 'PM',         icon: '📋', agentName: 'pm-v1',         desc: 'Ticket analysis & acceptance criteria' },
  { role: 'QUALIFIER', label: 'Qualifier',  icon: '🔬', agentName: 'qualifier-v1',  desc: 'Work estimation & scope analysis' },
  { role: 'ARCHITECT', label: 'Architect',  icon: '🏗️', agentName: 'architect-v1',  desc: 'System design & tech selection' },
  { role: 'DEVELOPER', label: 'Developer',  icon: '💻', agentName: 'developer-v1',  desc: 'Code generation & multi-file commit' },
  { role: 'REVIEW',    label: 'Review',     icon: '👁️', agentName: 'reviewer-v1',   desc: 'Code review & security audit' },
  { role: 'TESTER',    label: 'Tester',     icon: '🧪', agentName: 'tester-v1',     desc: 'CI validation & test generation' },
  { role: 'WRITER',    label: 'Writer',     icon: '📝', agentName: 'writer-v1',     desc: 'Documentation & changelog' },
];

export type AutonomyLevel = 'observe' | 'confirm' | 'autonomous';

export interface IntentConfirmation {
  request: RunRequest;
  autonomy: AutonomyLevel;
}

@Component({
  selector: 'app-intent-preview-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="modal-backdrop" *ngIf="visible" (click)="onBackdropClick($event)">
      <div class="modal-panel glass-panel" role="dialog" aria-modal="true">

        <!-- Modal header -->
        <div class="modal-header">
          <div class="header-icon">🚀</div>
          <div class="header-text">
            <h2>Launch Orchestration</h2>
            <p>Review the agent pipeline before starting</p>
          </div>
          <button class="btn-close" (click)="cancel()">✕</button>
        </div>

        <!-- Run summary -->
        <div class="run-summary">
          <div class="summary-field">
            <span class="field-label">Repository</span>
            <span class="field-value mono">{{ request.repo }}</span>
          </div>
          <div class="summary-field">
            <span class="field-label">Issue</span>
            <span class="field-value mono">#{{ request.issueNumber }}</span>
          </div>
          <div class="summary-field">
            <span class="field-label">Mode</span>
            <select class="mode-select" [ngModel]="request.mode" (ngModelChange)="updateMode($event)">
              <option value="PLANNING">Planning</option>
              <option value="EXECUTION">Execution</option>
            </select>
          </div>
        </div>

        <!-- Pipeline visualization -->
        <div class="pipeline-section">
          <h4 class="section-label">Agent Pipeline</h4>
          <div class="pipeline-scroll">
            <div class="pipeline-steps">
              <ng-container *ngFor="let step of pipelineSteps; let last = last">
                <div class="pipeline-step" [ngClass]="getStepCardClass(step.agentName)">
                  <div class="step-icon">{{ step.icon }}</div>
                  <div class="step-info">
                    <span class="step-label">{{ step.label }}</span>
                    <span class="step-agent-name mono" *ngIf="getCard(step.agentName) as card">
                      {{ card.name }}
                    </span>
                    <span class="step-desc">{{ step.desc }}</span>
                  </div>
                  <div class="step-caps" *ngIf="getCard(step.agentName) as card">
                    <span *ngFor="let cap of card.capabilities.slice(0,2)" class="cap-chip">{{ cap }}</span>
                    <span *ngIf="card.capabilities.length > 2" class="cap-more">
                      +{{ card.capabilities.length - 2 }}
                    </span>
                  </div>
                  <div class="step-status" *ngIf="getCard(step.agentName) as card">
                    <span class="status-dot" [ngClass]="'dot-' + card.status"></span>
                    <span class="status-text">{{ card.status }}</span>
                  </div>
                </div>
                <div *ngIf="!last" class="pipeline-arrow">→</div>
              </ng-container>

              <!-- Loading skeleton while cards load -->
              <ng-container *ngIf="loadingCards()">
                <div *ngFor="let i of [1,2,3,4,5,6,7]" class="pipeline-step skeleton">
                  <div class="skeleton-icon"></div>
                  <div class="skeleton-text"></div>
                </div>
              </ng-container>
            </div>
          </div>
        </div>

        <!-- Autonomy dial -->
        <div class="autonomy-section">
          <h4 class="section-label">
            Autonomy Level
            <span class="autonomy-hint">{{ autonomyHint() }}</span>
          </h4>
          <div class="autonomy-options">
            <button *ngFor="let opt of autonomyOptions"
                    class="autonomy-btn"
                    [class.active]="autonomy() === opt.value"
                    (click)="setAutonomy(opt.value)">
              <span class="autonomy-icon">{{ opt.icon }}</span>
              <span class="autonomy-label">{{ opt.label }}</span>
            </button>
          </div>
          <div class="autonomy-track">
            <div class="autonomy-fill" [ngClass]="'fill-' + autonomy()"></div>
          </div>
        </div>

        <!-- Footer actions -->
        <div class="modal-footer">
          <button class="btn-cancel" (click)="cancel()">Cancel</button>
          <button class="btn-start" (click)="confirm()">
            Launch {{ request.mode === 'PLANNING' ? 'Planning' : 'Execution' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    /* ── Backdrop ──────────────────────────────────────────────── */
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.7);
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
      backdrop-filter: blur(4px);
    }
    .modal-panel {
      width: 100%;
      max-width: 760px;
      max-height: 90vh;
      overflow-y: auto;
      border-radius: 16px;
      padding: 28px;
      display: flex;
      flex-direction: column;
      gap: 20px;
      border: 1px solid rgba(56,189,248,0.2);
      background: rgba(15, 23, 42, 0.95);
      backdrop-filter: blur(16px);
    }

    /* ── Header ─────────────────────────────────────────────────── */
    .modal-header {
      display: flex;
      align-items: flex-start;
      gap: 16px;
    }
    .header-icon {
      font-size: 1.8rem;
      width: 52px;
      height: 52px;
      background: rgba(56,189,248,0.1);
      border: 1px solid rgba(56,189,248,0.2);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .header-text { flex: 1; }
    .header-text h2 { margin: 0; font-size: 1.25rem; color: white; }
    .header-text p  { margin: 4px 0 0; font-size: 0.85rem; color: #94a3b8; }
    .btn-close {
      background: transparent;
      border: none;
      color: #64748b;
      font-size: 1.1rem;
      cursor: pointer;
      padding: 4px 8px;
      border-radius: 6px;
      transition: color 0.2s;
    }
    .btn-close:hover { color: white; }

    /* ── Run summary ─────────────────────────────────────────────── */
    .run-summary {
      display: flex;
      gap: 20px;
      padding: 12px 16px;
      border-radius: 10px;
      flex-wrap: wrap;
      background: var(--surface);
      border: 1px solid var(--border);
    }
    .summary-field { display: flex; align-items: center; gap: 8px; }
    .field-label { font-size: 0.8rem; color: #94a3b8; white-space: nowrap; }
    .field-value { font-size: 0.9rem; color: white; font-weight: 600; }
    .mono { font-family: monospace; }
    .mode-select {
      background: rgba(255,255,255,0.06);
      border: 1px solid rgba(255,255,255,0.1);
      color: white;
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 0.85rem;
      cursor: pointer;
      outline: none;
    }

    /* ── Section label ───────────────────────────────────────────── */
    .section-label {
      margin: 0 0 12px 0;
      font-size: 0.72rem;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: rgba(255,255,255,0.4);
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    /* ── Pipeline ────────────────────────────────────────────────── */
    .pipeline-section { overflow: hidden; }
    .pipeline-scroll { overflow-x: auto; padding-bottom: 4px; }
    .pipeline-steps {
      display: flex;
      align-items: stretch;
      gap: 4px;
      min-width: max-content;
    }
    .pipeline-arrow {
      color: rgba(255,255,255,0.2);
      font-size: 1.2rem;
      display: flex;
      align-items: center;
      padding: 0 2px;
      flex-shrink: 0;
    }
    .pipeline-step {
      padding: 10px 12px;
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 10px;
      display: flex;
      flex-direction: column;
      gap: 5px;
      min-width: 120px;
      max-width: 140px;
      transition: border-color 0.2s, background 0.2s;
    }
    .pipeline-step.active-card {
      border-color: rgba(56,189,248,0.35);
      background: rgba(56,189,248,0.06);
    }
    .step-icon { font-size: 1.2rem; }
    .step-info { display: flex; flex-direction: column; gap: 2px; }
    .step-label { font-size: 0.82rem; font-weight: 700; color: white; }
    .step-agent-name { font-size: 0.67rem; color: rgba(56,189,248,0.6); }
    .step-desc { font-size: 0.68rem; color: rgba(255,255,255,0.4); line-height: 1.3; }
    .step-caps { display: flex; gap: 3px; flex-wrap: wrap; }
    .cap-chip {
      font-size: 0.6rem;
      padding: 1px 5px;
      background: rgba(139,92,246,0.12);
      color: rgba(139,92,246,0.7);
      border-radius: 5px;
    }
    .cap-more { font-size: 0.6rem; color: rgba(255,255,255,0.25); }
    .step-status { display: flex; align-items: center; gap: 4px; }
    .status-dot { width: 5px; height: 5px; border-radius: 50%; }
    .dot-active { background: #22c55e; }
    .dot-inactive { background: #64748b; }
    .dot-error  { background: #ef4444; }
    .status-text { font-size: 0.6rem; color: rgba(255,255,255,0.3); }

    /* Skeleton */
    .pipeline-step.skeleton { opacity: 0.3; animation: shimmer 1.2s infinite ease-in-out; }
    .skeleton-icon { width: 24px; height: 24px; background: rgba(255,255,255,0.1); border-radius: 4px; }
    .skeleton-text { width: 80px; height: 10px; background: rgba(255,255,255,0.08); border-radius: 4px; }
    @keyframes shimmer { 0%,100% { opacity: 0.3; } 50% { opacity: 0.5; } }

    /* ── Autonomy dial ───────────────────────────────────────────── */
    .autonomy-hint { font-size: 0.72rem; color: rgba(56,189,248,0.6); text-transform: none; letter-spacing: 0; font-weight: 400; }
    .autonomy-options { display: flex; gap: 8px; margin-bottom: 10px; }
    .autonomy-btn {
      flex: 1;
      padding: 10px 12px;
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.07);
      border-radius: 8px;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 5px;
      transition: all 0.2s;
      color: rgba(255,255,255,0.5);
    }
    .autonomy-btn:hover { border-color: rgba(56,189,248,0.25); background: rgba(56,189,248,0.04); }
    .autonomy-btn.active {
      border-color: rgba(56,189,248,0.5);
      background: rgba(56,189,248,0.1);
      color: white;
    }
    .autonomy-icon  { font-size: 1.2rem; }
    .autonomy-label { font-size: 0.75rem; font-weight: 600; }

    .autonomy-track {
      height: 3px;
      background: rgba(255,255,255,0.05);
      border-radius: 2px;
      overflow: hidden;
    }
    .autonomy-fill {
      height: 100%;
      border-radius: 2px;
      transition: width 0.4s ease, background 0.3s;
    }
    .fill-observe    { width: 20%;  background: #94a3b8; }
    .fill-confirm    { width: 55%;  background: #38bdf8; }
    .fill-autonomous { width: 100%; background: linear-gradient(90deg, #38bdf8, #22c55e); }

    /* ── Footer ─────────────────────────────────────────────────── */
    .modal-footer { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
    .btn-cancel {
      padding: 10px 20px;
      background: transparent;
      border: 1px solid rgba(255,255,255,0.1);
      color: #94a3b8;
      border-radius: 8px;
      cursor: pointer;
      font-size: 0.9rem;
      transition: all 0.2s;
    }
    .btn-cancel:hover { border-color: rgba(255,255,255,0.2); color: white; }
    .btn-start {
      padding: 10px 28px;
      border: none;
      border-radius: 8px;
      background: var(--accent-active);
      color: white;
      font-size: 0.9rem;
      font-weight: 700;
      cursor: pointer;
      transition: opacity 0.2s;
    }
    .btn-start:hover { opacity: 0.85; }
  `]
})
export class IntentPreviewModalComponent implements OnInit, OnChanges {
  @Input() visible = false;
  @Input({ required: true }) request!: RunRequest;
  @Output() confirmed = new EventEmitter<IntentConfirmation>();
  @Output() cancelled = new EventEmitter<void>();

  pipelineSteps = PIPELINE_STEPS;
  loadingCards  = signal(false);
  private agentCards = signal<AgentCard[]>([]);
  autonomy = signal<AutonomyLevel>('confirm');

  autonomyOptions: { value: AutonomyLevel; label: string; icon: string }[] = [
    { value: 'observe',    label: 'Observe',    icon: '👁️' },
    { value: 'confirm',    label: 'Confirm',    icon: '🤝' },
    { value: 'autonomous', label: 'Autonomous', icon: '⚡' },
  ];

  constructor(private orchestratorService: OrchestratorService) {}

  ngOnInit() {
    this.fetchCards();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['visible']?.currentValue === true && this.agentCards().length === 0) {
      this.fetchCards();
    }
  }

  private fetchCards() {
    this.loadingCards.set(true);
    this.orchestratorService.listAgents().subscribe({
      next: (cards) => { this.agentCards.set(cards); this.loadingCards.set(false); },
      error: ()      => { this.loadingCards.set(false); }
    });
  }

  getCard(agentName: string): AgentCard | undefined {
    return this.agentCards().find(c => c.name === agentName);
  }

  getStepCardClass(agentName: string): string {
    const card = this.getCard(agentName);
    return card?.status === 'active' ? 'active-card' : '';
  }

  setAutonomy(level: AutonomyLevel) { this.autonomy.set(level); }

  autonomyHint(): string {
    switch (this.autonomy()) {
      case 'observe':    return 'Human approves every step';
      case 'confirm':    return 'Human reviews before commit';
      case 'autonomous': return 'Fully automated pipeline';
    }
  }

  updateMode(mode: string) {
    // Request is an input — emit back through confirmed, or just mutate locally for preview
    this.request = { ...this.request, mode };
  }

  confirm() {
    this.confirmed.emit({ request: this.request, autonomy: this.autonomy() });
  }

  cancel() {
    this.cancelled.emit();
  }

  onBackdropClick(event: MouseEvent) {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.cancel();
    }
  }
}
