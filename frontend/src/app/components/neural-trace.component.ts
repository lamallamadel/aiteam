import {
  Component, Input, Output, EventEmitter,
  OnChanges, SimpleChanges, signal, computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { WorkflowEvent } from '../models/orchestrator.model';

// ── Pipeline definition ───────────────────────────────────────────────────────

const PIPELINE = [
  { id: 'PM',        label: 'PM',        icon: '📋', desc: 'Ticket analysis' },
  { id: 'QUALIFIER', label: 'Qualifier', icon: '🔬', desc: 'Estimation' },
  { id: 'ARCHITECT', label: 'Architect', icon: '🏗️', desc: 'System design' },
  { id: 'DEVELOPER', label: 'Developer', icon: '💻', desc: 'Code generation' },
  { id: 'REVIEW',    label: 'Review',    icon: '👁️', desc: 'Code review' },
  { id: 'TESTER',    label: 'Tester',    icon: '🧪', desc: 'CI validation' },
  { id: 'WRITER',    label: 'Writer',    icon: '📝', desc: 'Documentation' },
] as const;

type PipelineId = typeof PIPELINE[number]['id'];

export type NodeStatus = 'pending' | 'active' | 'done' | 'failed' | 'flagged' | 'pruned' | 'grafted';

export interface GraftEvent { after: PipelineId; agentName: string; }

// Available agents for grafting (matches A2A registry)
const GRAFTABLE_AGENTS = [
  'pm-v1', 'qualifier-v1', 'architect-v1', 'developer-v1',
  'reviewer-v1', 'tester-v1', 'writer-v1', 'judge-v1'
];

// ── Component ─────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-neural-trace',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="pipeline-container">
      <div class="pipeline-header">
        <span class="header-label">
          <span class="pulse-dot" [class.live]="isLive()"></span>
          AGENT PIPELINE
        </span>
        <div class="header-right">
          <span class="step-count">{{ doneCount() }}/{{ PIPELINE.length }} complete</span>
          <div class="presence-indicator" *ngIf="showPresence && activeUsers.length > 0">
            <span class="presence-icon">👥</span>
            <span class="presence-count">{{ activeUsers.length }}</span>
            <div class="presence-tooltip">
              <div *ngFor="let user of activeUsers" class="presence-user">
                <span class="user-avatar" [style.background]="getUserColor(user)">
                  {{ getUserInitial(user) }}
                </span>
                <span class="user-name">{{ user }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="pipeline-track">
        <ng-container *ngFor="let step of PIPELINE; let last = last; let i = index">
          <!-- Node -->
          <div class="node-wrap">
            <div class="pipeline-node"
                 [ngClass]="nodeClass(step.id)"
                 (mouseenter)="onNodeHover(step.id)"
                 (mouseleave)="hoveredNode = null">

              <!-- Icon -->
              <div class="node-icon">{{ step.icon }}</div>

              <!-- Label + duration -->
              <div class="node-label">{{ step.label }}</div>
              <div class="node-duration" *ngIf="getDuration(step.id) as dur">
                {{ formatDuration(dur) }}
              </div>

              <!-- Status bar (bottom accent) -->
              <div class="status-bar" [ngClass]="nodeClass(step.id)"></div>

              <!-- Interaction buttons (interactive mode only) -->
              <div class="node-actions" *ngIf="interactive && hoveredNode === step.id">
                <button class="action-btn flag-btn"
                        [class.flagged]="flaggedNodes.has(step.id)"
                        (click)="toggleFlag(step.id); $event.stopPropagation()"
                        title="Flag for review">
                  ⚑
                </button>
                <button class="action-btn prune-btn"
                        [class.pruned]="prunedNodes.has(step.id)"
                        (click)="togglePrune(step.id); $event.stopPropagation()"
                        title="Prune (skip) this step">
                  ⊘
                </button>
              </div>

              <!-- Pruned overlay -->
              <div class="pruned-overlay" *ngIf="prunedNodes.has(step.id)">
                <span>PRUNED</span>
              </div>

              <!-- Collaboration cursors -->
              <div class="collab-cursors" *ngIf="showPresence">
                <div *ngFor="let user of getUsersAtNode(step.id)" 
                     class="collab-cursor"
                     [style.background]="getUserColor(user)"
                     [title]="user">
                  {{ getUserInitial(user) }}
                </div>
              </div>
            </div>

            <!-- Flag indicator (always visible when flagged) -->
            <div class="flag-indicator" *ngIf="flaggedNodes.has(step.id)">⚑ Flagged</div>
          </div>

          <!-- Connector + Graft button -->
          <div *ngIf="!last" class="connector-wrap">
            <div class="connector-line"
                 [ngClass]="connectorClass(step.id, PIPELINE[i+1].id)">
            </div>

            <!-- Graft button (interactive only) -->
            <button *ngIf="interactive"
                    class="graft-btn"
                    [class.open]="graftPickerAt === step.id"
                    (click)="openGraftPicker(step.id)"
                    title="Inject step here">
              +
            </button>

            <!-- Graft picker -->
            <div *ngIf="graftPickerAt === step.id" class="graft-picker glass-panel">
              <div class="picker-label">Inject agent after {{ step.label }}</div>
              <div class="picker-agents">
                <button *ngFor="let agent of GRAFTABLE_AGENTS"
                        class="picker-agent"
                        (click)="confirmGraft(step.id, agent)">
                  {{ agent }}
                </button>
              </div>
              <button class="picker-cancel" (click)="graftPickerAt = null">Cancel</button>
            </div>
          </div>
        </ng-container>
      </div>

      <!-- Legend (interactive mode) -->
      <div *ngIf="interactive" class="legend">
        <span class="legend-item"><span class="dot pending"></span> Pending</span>
        <span class="legend-item"><span class="dot active"></span> Active</span>
        <span class="legend-item"><span class="dot done"></span> Done</span>
        <span class="legend-item"><span class="dot failed"></span> Failed</span>
        <span class="legend-item"><span class="dot flagged"></span> Flagged</span>
        <span class="legend-item"><span class="dot pruned"></span> Pruned</span>
      </div>
    </div>
  `,
  styles: [`
    /* ── Container ──────────────────────────────────────────────── */
    .pipeline-container {
      padding: 14px 16px;
      margin-bottom: 16px;
      background: var(--surface);
      flex-shrink: 0;
    }
    .pipeline-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }
    .header-label {
      font-size: 0.65rem;
      font-weight: 800;
      color: #38bdf8;
      letter-spacing: 0.1em;
      display: flex;
      align-items: center;
      gap: 7px;
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .pulse-dot {
      width: 6px; height: 6px;
      border-radius: 50%;
      background: #64748b;
    }
    .pulse-dot.live {
      background: #22c55e;
      animation: blink 1s infinite;
    }
    @keyframes blink { 0%,100% { opacity:1; } 50% { opacity:0.3; } }
    .step-count { font-size: 0.65rem; color: #64748b; font-weight: 600; }

    /* ── Track ───────────────────────────────────────────────────── */
    .pipeline-track {
      display: flex;
      align-items: center;
      overflow-x: auto;
      padding-bottom: 6px;
      gap: 1px;
      background: var(--border);
    }

    /* ── Node ────────────────────────────────────────────────────── */
    .node-wrap {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      flex-shrink: 0;
    }
    .pipeline-node {
      position: relative;
      width: 72px;
      padding: 10px 6px 8px;
      border-radius: 0;
      border: none;
      border-right: 1px solid var(--border);
      background: var(--surface);
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      cursor: default;
      transition: background 0.2s;
    }
    .pipeline-node:first-child {
      border-left: 1px solid var(--border);
    }

    /* Status variants */
    .pipeline-node.status-pending  { background: var(--surface); }
    .pipeline-node.status-active   { background: rgba(56,189,248,0.08); }
    .pipeline-node.status-done     { background: rgba(34,197,94,0.05); }
    .pipeline-node.status-failed   { background: rgba(239,68,68,0.06); }
    .pipeline-node.status-flagged  { background: rgba(234,179,8,0.07); }
    .pipeline-node.status-pruned   { opacity: 0.45; }

    /* Status bar (bottom accent - 2px) */
    .status-bar {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 2px;
      background: transparent;
    }
    .status-bar.status-pending  { background: transparent; }
    .status-bar.status-active   { background: #38bdf8; }
    .status-bar.status-done     { background: #22c55e; }
    .status-bar.status-failed   { background: #ef4444; }
    .status-bar.status-flagged  { background: #eab308; }
    .status-bar.status-pruned   { background: #475569; }

    .node-icon { font-size: 1.1rem; line-height: 1; }
    .node-label { font-size: 0.68rem; font-weight: 700; color: rgba(255,255,255,0.7); text-align: center; }
    .status-active .node-label  { color: #38bdf8; }
    .status-done   .node-label  { color: #22c55e; }
    .status-failed .node-label  { color: #ef4444; }
    .status-flagged .node-label { color: #eab308; }

    .node-duration {
      font-size: 0.6rem;
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
      color: rgba(255,255,255,0.3);
    }
    .status-done .node-duration { color: rgba(34,197,94,0.6); }

    /* Interaction buttons */
    .node-actions {
      position: absolute;
      top: -28px;
      left: 50%;
      transform: translateX(-50%);
      display: flex;
      gap: 3px;
      background: rgba(15,23,42,0.95);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 6px;
      padding: 3px 4px;
      z-index: 10;
      white-space: nowrap;
    }
    .action-btn {
      background: transparent;
      border: none;
      cursor: pointer;
      font-size: 0.75rem;
      padding: 1px 5px;
      border-radius: 4px;
      color: rgba(255,255,255,0.4);
      transition: all 0.15s;
    }
    .action-btn:hover { background: rgba(255,255,255,0.08); color: white; }
    .flag-btn.flagged { color: #eab308; }
    .prune-btn.pruned { color: #ef4444; }

    /* Pruned overlay */
    .pruned-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.55rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      color: rgba(239,68,68,0.5);
      background: repeating-linear-gradient(
        -45deg,
        transparent,
        transparent 4px,
        rgba(239,68,68,0.06) 4px,
        rgba(239,68,68,0.06) 5px
      );
      border-radius: 9px;
      pointer-events: none;
    }

    /* Flag indicator below node */
    .flag-indicator {
      font-size: 0.6rem;
      color: #eab308;
      font-weight: 700;
      letter-spacing: 0.04em;
    }

    /* ── Connector ───────────────────────────────────────────────── */
    .connector-wrap {
      position: relative;
      display: flex;
      align-items: center;
      flex-shrink: 0;
      width: 0;
      visibility: hidden;
    }
    .connector-line {
      height: 2px;
      width: 100%;
      background: var(--border);
      transition: background 0.3s;
    }
    .connector-line.conn-active { background: var(--border); }
    .connector-line.conn-done   {
      background: var(--border);
      animation: flow 2s linear infinite;
      background-size: 200% 100%;
      background-image: linear-gradient(90deg, var(--border) 0%, rgba(34,197,94,0.6) 50%, var(--border) 100%);
    }
    @keyframes flow { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

    /* Graft button */
    .graft-btn {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 16px; height: 16px;
      border-radius: 50%;
      background: rgba(15,23,42,0.9);
      border: 1px solid rgba(56,189,248,0.2);
      color: rgba(56,189,248,0.4);
      font-size: 0.7rem;
      line-height: 1;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      transition: opacity 0.2s, border-color 0.2s, color 0.2s;
      z-index: 5;
      padding: 0;
    }
    .connector-wrap:hover .graft-btn,
    .graft-btn.open { opacity: 1; border-color: #38bdf8; color: #38bdf8; }
    .graft-btn.open { background: rgba(56,189,248,0.15); }

    /* Graft picker */
    .graft-picker {
      position: absolute;
      top: 24px;
      left: 50%;
      transform: translateX(-50%);
      width: 180px;
      padding: 10px;
      border-radius: 8px;
      z-index: 50;
      display: flex;
      flex-direction: column;
      gap: 6px;
      border: 1px solid rgba(56,189,248,0.3);
      background: rgba(15,23,42,0.97);
    }
    .picker-label {
      font-size: 0.68rem;
      color: rgba(255,255,255,0.5);
      font-weight: 600;
    }
    .picker-agents { display: flex; flex-direction: column; gap: 3px; }
    .picker-agent {
      padding: 4px 8px;
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 5px;
      color: rgba(255,255,255,0.7);
      font-size: 0.72rem;
      font-family: monospace;
      cursor: pointer;
      text-align: left;
      transition: all 0.15s;
    }
    .picker-agent:hover { border-color: rgba(56,189,248,0.4); color: #38bdf8; background: rgba(56,189,248,0.08); }
    .picker-cancel {
      padding: 4px;
      background: transparent;
      border: none;
      color: rgba(255,255,255,0.25);
      font-size: 0.68rem;
      cursor: pointer;
      text-align: center;
    }
    .picker-cancel:hover { color: rgba(255,255,255,0.5); }

    /* ── Legend ──────────────────────────────────────────────────── */
    .legend {
      display: flex;
      gap: 12px;
      margin-top: 8px;
      padding-top: 8px;
      border-top: 1px solid rgba(255,255,255,0.04);
      flex-wrap: wrap;
    }
    .legend-item {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 0.62rem;
      color: rgba(255,255,255,0.3);
    }
    .dot {
      width: 6px; height: 6px;
      border-radius: 50%;
    }
    .dot.pending  { background: #334155; }
    .dot.active   { background: #38bdf8; }
    .dot.done     { background: #22c55e; }
    .dot.failed   { background: #ef4444; }
    .dot.flagged  { background: #eab308; }
    .dot.pruned   { background: #475569; }

    /* ── Presence ────────────────────────────────────────────────── */
    .presence-indicator {
      position: relative;
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 3px 8px;
      background: rgba(59,130,246,0.1);
      border: 1px solid rgba(59,130,246,0.2);
      border-radius: 12px;
      cursor: pointer;
      font-size: 0.7rem;
      color: rgba(255,255,255,0.7);
    }
    .presence-icon { font-size: 0.8rem; }
    .presence-count { font-weight: 700; color: #3b82f6; }
    
    .presence-tooltip {
      position: absolute;
      top: 100%;
      right: 0;
      margin-top: 6px;
      padding: 8px;
      background: rgba(15,23,42,0.97);
      border: 1px solid rgba(59,130,246,0.3);
      border-radius: 8px;
      min-width: 150px;
      z-index: 100;
      display: none;
      flex-direction: column;
      gap: 6px;
    }
    .presence-indicator:hover .presence-tooltip {
      display: flex;
    }
    
    .presence-user {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 4px;
      border-radius: 4px;
    }
    .presence-user:hover {
      background: rgba(255,255,255,0.05);
    }
    
    .user-avatar {
      width: 22px;
      height: 22px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.65rem;
      font-weight: 700;
      color: white;
      flex-shrink: 0;
    }
    .user-name {
      font-size: 0.72rem;
      color: rgba(255,255,255,0.8);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* ── Collaboration Cursors ───────────────────────────────────── */
    .collab-cursors {
      position: absolute;
      bottom: -10px;
      left: 50%;
      transform: translateX(-50%);
      display: flex;
      gap: 2px;
      z-index: 20;
    }
    .collab-cursor {
      width: 16px;
      height: 16px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.55rem;
      font-weight: 700;
      color: white;
      border: 2px solid rgba(15,23,42,1);
      animation: cursor-pulse 1.5s infinite;
    }
    @keyframes cursor-pulse {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(1.1); }
    }
  `]
})
export class NeuralTraceComponent implements OnChanges {
  @Input() steps: any[] = [];
  @Input() interactive = false;
  @Input() showPresence = false;
  @Input() activeUsers: string[] = [];
  @Input() cursorPositions: Map<string, string> = new Map();
  @Output() flagged  = new EventEmitter<string>();
  @Output() pruned   = new EventEmitter<string>();
  @Output() grafted  = new EventEmitter<GraftEvent>();
  @Output() cursorMoved = new EventEmitter<string>();

  readonly PIPELINE = PIPELINE;
  readonly GRAFTABLE_AGENTS = GRAFTABLE_AGENTS;

  hoveredNode: string | null = null;
  graftPickerAt: string | null = null;

  flaggedNodes = new Set<string>();
  prunedNodes  = new Set<string>();
  
  private userColorCache = new Map<string, string>();

  // Derived state from steps input
  private statusMap = signal<Map<string, NodeStatus>>(new Map());
  private durationMap = signal<Map<string, number>>(new Map());

  isLive = computed(() =>
    this.steps.some(s => s?.eventType === 'STEP_START' && !this.steps.some(
      c => c?.eventType === 'STEP_COMPLETE' && c?.agentName === s?.agentName
    ))
  );

  doneCount = computed(() => {
    let count = 0;
    for (const step of PIPELINE) {
      if (this.getStatus(step.id) === 'done') count++;
    }
    return count;
  });

  ngOnChanges(changes: SimpleChanges) {
    if (changes['steps']) {
      this.buildMaps();
    }
  }

  private buildMaps() {
    const statusMap = new Map<string, NodeStatus>();
    const durationMap = new Map<string, number>();

    for (const event of this.steps) {
      if (!event) continue;

      // WorkflowEvent shape
      const agentRaw: string = event.agentName || event.orchestrationStep || '';
      const agentName = agentRaw.toUpperCase();
      const pipelineId = this.normalizeAgent(agentName);
      if (!pipelineId) continue;

      const type = event.eventType;
      if (type === 'STEP_START') {
        if (!statusMap.has(pipelineId) || statusMap.get(pipelineId) === 'pending') {
          statusMap.set(pipelineId, 'active');
        }
      } else if (type === 'STEP_COMPLETE') {
        statusMap.set(pipelineId, 'done');
        if (event.durationMs) durationMap.set(pipelineId, event.durationMs);
      } else if (type === 'WORKFLOW_ERROR') {
        if (statusMap.get(pipelineId) === 'active') {
          statusMap.set(pipelineId, 'failed');
        }
      }
      // Legacy message shape: has orchestrationStep but no eventType
      else if (!type && agentRaw) {
        if (!statusMap.has(pipelineId)) {
          statusMap.set(pipelineId, 'active');
        }
      }
    }

    this.statusMap.set(statusMap);
    this.durationMap.set(durationMap);
  }

  private normalizeAgent(agent: string): PipelineId | null {
    // Map backend agent names → pipeline IDs
    const map: Record<string, PipelineId> = {
      'PM': 'PM', 'QUALIFIER': 'QUALIFIER', 'ARCHITECT': 'ARCHITECT',
      'DEVELOPER': 'DEVELOPER', 'REVIEW': 'REVIEW', 'REVIEWER': 'REVIEW',
      'TESTER': 'TESTER', 'WRITER': 'WRITER',
    };
    return map[agent] ?? null;
  }

  getStatus(id: string): NodeStatus {
    if (this.flaggedNodes.has(id)) return 'flagged';
    if (this.prunedNodes.has(id))  return 'pruned';
    return this.statusMap().get(id) ?? 'pending';
  }

  getDuration(id: string): number | null {
    return this.durationMap().get(id) ?? null;
  }

  nodeClass(id: string): string {
    return 'status-' + this.getStatus(id);
  }

  connectorClass(fromId: string, toId: string): string {
    const fromStatus = this.getStatus(fromId);
    const toStatus   = this.getStatus(toId);
    if (fromStatus === 'done' && (toStatus === 'done' || toStatus === 'active')) return 'conn-done';
    if (toStatus === 'active') return 'conn-active';
    return '';
  }

  formatDuration(ms: number): string {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${Math.floor(ms / 60000)}m${Math.floor((ms % 60000) / 1000)}s`;
  }

  toggleFlag(id: string) {
    if (this.flaggedNodes.has(id)) {
      this.flaggedNodes.delete(id);
    } else {
      this.flaggedNodes.add(id);
      this.prunedNodes.delete(id);
      this.flagged.emit(id);
    }
    this.buildMaps(); // refresh computed
  }

  togglePrune(id: string) {
    if (this.prunedNodes.has(id)) {
      this.prunedNodes.delete(id);
    } else {
      this.prunedNodes.add(id);
      this.flaggedNodes.delete(id);
      this.pruned.emit(id);
    }
    this.buildMaps();
  }

  openGraftPicker(afterId: string) {
    this.graftPickerAt = this.graftPickerAt === afterId ? null : afterId;
  }

  confirmGraft(after: string, agentName: string) {
    this.graftPickerAt = null;
    this.grafted.emit({ after: after as PipelineId, agentName });
  }

  onNodeHover(nodeId: string) {
    this.hoveredNode = nodeId;
    if (this.showPresence) {
      this.cursorMoved.emit(nodeId);
    }
  }

  getUsersAtNode(nodeId: string): string[] {
    const users: string[] = [];
    this.cursorPositions.forEach((cursorNodeId, userId) => {
      if (cursorNodeId === nodeId) {
        users.push(userId);
      }
    });
    return users;
  }

  getUserColor(userId: string): string {
    if (this.userColorCache.has(userId)) {
      return this.userColorCache.get(userId)!;
    }

    const colors = [
      '#ef4444', '#f59e0b', '#10b981', '#3b82f6', 
      '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16'
    ];
    
    const hash = userId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    const color = colors[hash % colors.length];
    this.userColorCache.set(userId, color);
    return color;
  }

  getUserInitial(userId: string): string {
    return userId.substring(0, 1).toUpperCase();
  }
}
