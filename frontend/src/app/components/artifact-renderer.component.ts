import { Component, Input, OnChanges, SimpleChanges, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ArtifactResponse } from '../models/orchestrator.model';
import { ConfidenceSignalComponent, JudgeVerdict } from './confidence-signal.component';

// ── Open-ended GenUI layout types ────────────────────────────────────────────

export type UiLayoutType = 'table' | 'key_value' | 'diff' | 'list' | 'metric_grid';

export interface UiLayout {
  _ui_layout: UiLayoutType;
  _ui_title?: string;
  // table
  headers?: string[];
  rows?: (string | number)[][];
  // key_value
  pairs?: { key: string; value: string | number }[];
  // diff
  lines?: { type: 'add' | 'del' | 'ctx'; text: string }[];
  // list
  items?: string[];
  // metric_grid
  metrics?: { label: string; value: string | number; trend?: 'up' | 'down' | 'flat' }[];
}

// ── Typed shapes for each schema ────────────────────────────────────────────

interface TicketPlan {
  issueId?: string | number;
  title?: string;
  summary?: string;
  priority?: 'P0' | 'P1' | 'P2' | 'P3';
  acceptanceCriteria?: string[];
  userStories?: string[];
  risks?: string[];
  labelsToApply?: string[];
}

interface WorkTask {
  id?: string;
  area?: 'backend' | 'frontend' | 'infra' | 'docs';
  description?: string;
  filesLikely?: string[];
  tests?: string[];
  dependsOn?: string[];
}

interface WorkPlan {
  branchName?: string;
  tasks?: WorkTask[];
  commands?: { backendVerify?: string; frontendLint?: string; frontendTest?: string; e2e?: string };
  definitionOfDone?: string[];
}

interface CiSuite { status?: string; details?: string[]; }

interface TestReport {
  prUrl?: string;
  ciStatus?: 'GREEN' | 'RED';
  backend?: CiSuite;
  frontend?: CiSuite;
  e2e?: CiSuite;
  notes?: string[];
}

// ── Component ────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-artifact-renderer',
  standalone: true,
  imports: [CommonModule, ConfidenceSignalComponent],
  template: `
    <!-- ticket_plan ───────────────────────────────────────────────────────── -->
    <ng-container *ngIf="type() === 'ticket_plan' && ticketPlan() as tp">
      <div class="widget ticket-plan">
        <div class="widget-row space-between">
          <span class="widget-title">{{ tp.title || 'Ticket Plan' }}</span>
          <span *ngIf="tp.priority" class="priority-badge" [ngClass]="'p-' + tp.priority.toLowerCase()">
            {{ tp.priority }}
          </span>
        </div>

        <p *ngIf="tp.summary" class="summary-text">{{ tp.summary }}</p>

        <div *ngIf="tp.acceptanceCriteria?.length" class="sub-section">
          <h6 class="sub-title">Acceptance Criteria</h6>
          <ul class="check-list">
            <li *ngFor="let ac of tp.acceptanceCriteria">
              <span class="check-icon">✓</span> {{ ac }}
            </li>
          </ul>
        </div>

        <div *ngIf="tp.userStories?.length" class="sub-section">
          <h6 class="sub-title">User Stories</h6>
          <ul class="story-list">
            <li *ngFor="let us of tp.userStories">{{ us }}</li>
          </ul>
        </div>

        <div *ngIf="tp.risks?.length" class="sub-section">
          <h6 class="sub-title">Risks</h6>
          <ul class="risk-list">
            <li *ngFor="let r of tp.risks">
              <span class="risk-icon">⚠</span> {{ r }}
            </li>
          </ul>
        </div>

        <div *ngIf="tp.labelsToApply?.length" class="labels-row">
          <span *ngFor="let lbl of tp.labelsToApply" class="label-chip">{{ lbl }}</span>
        </div>
      </div>
    </ng-container>

    <!-- work_plan ─────────────────────────────────────────────────────────── -->
    <ng-container *ngIf="type() === 'work_plan' && workPlan() as wp">
      <div class="widget work-plan">
        <div class="widget-row space-between">
          <span class="widget-title">Work Plan</span>
          <span *ngIf="wp.branchName" class="mono branch-chip">{{ wp.branchName }}</span>
        </div>

        <div *ngIf="wp.tasks?.length" class="sub-section">
          <h6 class="sub-title">Tasks ({{ wp.tasks!.length }})</h6>
          <div class="tasks-list">
            <div *ngFor="let t of wp.tasks" class="task-item">
              <div class="task-header">
                <span *ngIf="t.id" class="task-id mono">#{{ t.id }}</span>
                <span class="area-badge" *ngIf="t.area" [ngClass]="'area-' + t.area">{{ t.area }}</span>
                <span class="task-desc">{{ t.description }}</span>
              </div>
              <div *ngIf="t.filesLikely?.length" class="file-chips">
                <span *ngFor="let f of t.filesLikely" class="file-chip mono">{{ f }}</span>
              </div>
              <div *ngIf="t.dependsOn?.length" class="deps-row">
                <span class="meta-label">depends on:</span>
                <span *ngFor="let d of t.dependsOn" class="dep-chip mono">{{ d }}</span>
              </div>
            </div>
          </div>
        </div>

        <div *ngIf="wp.commands" class="sub-section">
          <h6 class="sub-title">Commands</h6>
          <div class="commands-grid">
            <ng-container *ngIf="wp.commands.backendVerify">
              <span class="cmd-label">backend</span>
              <code class="cmd-value">{{ wp.commands.backendVerify }}</code>
            </ng-container>
            <ng-container *ngIf="wp.commands.frontendLint">
              <span class="cmd-label">lint</span>
              <code class="cmd-value">{{ wp.commands.frontendLint }}</code>
            </ng-container>
            <ng-container *ngIf="wp.commands.frontendTest">
              <span class="cmd-label">test</span>
              <code class="cmd-value">{{ wp.commands.frontendTest }}</code>
            </ng-container>
            <ng-container *ngIf="wp.commands.e2e">
              <span class="cmd-label">e2e</span>
              <code class="cmd-value">{{ wp.commands.e2e }}</code>
            </ng-container>
          </div>
        </div>

        <div *ngIf="wp.definitionOfDone?.length" class="sub-section">
          <h6 class="sub-title">Definition of Done</h6>
          <ul class="check-list dod">
            <li *ngFor="let dod of wp.definitionOfDone">
              <span class="check-icon">□</span> {{ dod }}
            </li>
          </ul>
        </div>
      </div>
    </ng-container>

    <!-- judge_verdict ─────────────────────────────────────────────────────── -->
    <ng-container *ngIf="type() === 'judge_verdict' && judgeVerdict()">
      <div class="widget judge-widget">
        <div class="widget-row">
          <span class="widget-title">Judge Verdict</span>
          <span *ngIf="judgeVerdict()?.checkpoint" class="checkpoint-label">{{ judgeVerdict()!.checkpoint }}</span>
        </div>
        <app-confidence-signal [verdict]="judgeVerdict()!"></app-confidence-signal>
      </div>
    </ng-container>

    <!-- test_report ───────────────────────────────────────────────────────── -->
    <ng-container *ngIf="type() === 'test_report' && testReport() as tr">
      <div class="widget test-report">
        <div class="widget-row space-between">
          <span class="widget-title">Test Report</span>
          <span class="ci-status-badge" [ngClass]="tr.ciStatus === 'GREEN' ? 'ci-green' : 'ci-red'">
            {{ tr.ciStatus === 'GREEN' ? '✓ CI GREEN' : '✕ CI RED' }}
          </span>
        </div>

        <div *ngIf="tr.prUrl" class="pr-url-row">
          <span class="meta-label">PR:</span>
          <span class="mono pr-url">{{ tr.prUrl }}</span>
        </div>

        <div class="suites-grid">
          <div *ngIf="tr.backend" class="suite-card" [ngClass]="suiteClass(tr.backend.status)">
            <div class="suite-name">Backend</div>
            <div class="suite-status" [ngClass]="suiteClass(tr.backend.status)">
              {{ suiteIcon(tr.backend.status) }} {{ tr.backend.status || '—' }}
            </div>
            <ul *ngIf="tr.backend.details?.length" class="suite-details">
              <li *ngFor="let d of tr.backend.details">{{ d }}</li>
            </ul>
          </div>

          <div *ngIf="tr.frontend" class="suite-card" [ngClass]="suiteClass(tr.frontend.status)">
            <div class="suite-name">Frontend</div>
            <div class="suite-status" [ngClass]="suiteClass(tr.frontend.status)">
              {{ suiteIcon(tr.frontend.status) }} {{ tr.frontend.status || '—' }}
            </div>
            <ul *ngIf="tr.frontend.details?.length" class="suite-details">
              <li *ngFor="let d of tr.frontend.details">{{ d }}</li>
            </ul>
          </div>

          <div *ngIf="tr.e2e" class="suite-card" [ngClass]="suiteClass(tr.e2e.status)">
            <div class="suite-name">E2E</div>
            <div class="suite-status" [ngClass]="suiteClass(tr.e2e.status)">
              {{ suiteIcon(tr.e2e.status) }} {{ tr.e2e.status || '—' }}
            </div>
            <ul *ngIf="tr.e2e.details?.length" class="suite-details">
              <li *ngFor="let d of tr.e2e.details">{{ d }}</li>
            </ul>
          </div>
        </div>

        <div *ngIf="tr.notes?.length" class="notes-list">
          <p *ngFor="let note of tr.notes" class="note-item">{{ note }}</p>
        </div>
      </div>
    </ng-container>

    <!-- _ui_layout: open-ended GenUI ──────────────────────────────────────── -->
    <ng-container *ngIf="type() === 'ui_layout' && uiLayout() as ul">
      <div class="widget ui-widget">
        <div *ngIf="ul._ui_title" class="widget-row">
          <span class="widget-title">{{ ul._ui_title }}</span>
          <span class="layout-type-chip">{{ ul._ui_layout }}</span>
        </div>

        <!-- table ──────────────────────────────────────────────────── -->
        <ng-container *ngIf="ul._ui_layout === 'table'">
          <div class="ui-table-wrap">
            <table class="ui-table">
              <thead *ngIf="ul.headers?.length">
                <tr>
                  <th *ngFor="let h of ul.headers">{{ h }}</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let row of ul.rows">
                  <td *ngFor="let cell of row">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </ng-container>

        <!-- key_value ──────────────────────────────────────────────── -->
        <ng-container *ngIf="ul._ui_layout === 'key_value'">
          <div class="kv-grid">
            <ng-container *ngFor="let p of ul.pairs">
              <span class="kv-key">{{ p.key }}</span>
              <span class="kv-value">{{ p.value }}</span>
            </ng-container>
          </div>
        </ng-container>

        <!-- diff ───────────────────────────────────────────────────── -->
        <ng-container *ngIf="ul._ui_layout === 'diff'">
          <div class="diff-block">
            <div *ngFor="let line of ul.lines" class="diff-line" [ngClass]="'diff-' + line.type">
              <span class="diff-gutter">{{ line.type === 'add' ? '+' : line.type === 'del' ? '-' : ' ' }}</span>
              <span class="diff-text">{{ line.text }}</span>
            </div>
          </div>
        </ng-container>

        <!-- list ───────────────────────────────────────────────────── -->
        <ng-container *ngIf="ul._ui_layout === 'list'">
          <ul class="ui-list">
            <li *ngFor="let item of ul.items">{{ item }}</li>
          </ul>
        </ng-container>

        <!-- metric_grid ────────────────────────────────────────────── -->
        <ng-container *ngIf="ul._ui_layout === 'metric_grid'">
          <div class="metric-grid">
            <div *ngFor="let m of ul.metrics" class="metric-card">
              <span class="metric-label">{{ m.label }}</span>
              <span class="metric-value">{{ m.value }}</span>
              <span *ngIf="m.trend" class="metric-trend" [ngClass]="'trend-' + m.trend">
                {{ m.trend === 'up' ? '▲' : m.trend === 'down' ? '▼' : '→' }}
              </span>
            </div>
          </div>
        </ng-container>
      </div>
    </ng-container>

    <!-- Fallback: raw JSON ──────────────────────────────────────────────────  -->
    <ng-container *ngIf="type() === 'raw'">
      <pre class="payload-pre">{{ prettyPayload() }}</pre>
    </ng-container>
  `,
  styles: [`
    /* ── Shared widget base ─────────────────────────────────────── */
    .widget {
      padding: 12px 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .widget-row {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .space-between { justify-content: space-between; }
    .widget-title {
      font-size: 0.9rem;
      font-weight: 700;
      color: white;
    }
    .sub-section { display: flex; flex-direction: column; gap: 6px; }
    .sub-title {
      margin: 0;
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 0.07em;
      color: rgba(255,255,255,0.35);
      font-weight: 700;
    }
    .summary-text { margin: 0; font-size: 0.84rem; color: rgba(255,255,255,0.7); line-height: 1.5; }
    .mono { font-family: monospace; }
    .meta-label { font-size: 0.7rem; color: rgba(255,255,255,0.35); }

    /* ── Ticket Plan ────────────────────────────────────────────── */
    .priority-badge {
      padding: 3px 10px;
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 800;
      text-transform: uppercase;
    }
    .p-p0 { background: rgba(239,68,68,0.2);  color: #ef4444; }
    .p-p1 { background: rgba(234,179,8,0.2);  color: #eab308; }
    .p-p2 { background: rgba(56,189,248,0.2); color: #38bdf8; }
    .p-p3 { background: rgba(148,163,184,0.15); color: #94a3b8; }

    .check-list, .story-list, .risk-list {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .check-list li, .story-list li, .risk-list li {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.7);
      display: flex;
      align-items: flex-start;
      gap: 6px;
      line-height: 1.4;
    }
    .check-icon { color: #22c55e; font-weight: 700; flex-shrink: 0; }
    .risk-icon  { color: #eab308; flex-shrink: 0; }
    .check-list.dod li { color: rgba(255,255,255,0.5); }
    .check-list.dod .check-icon { color: rgba(255,255,255,0.2); }

    .labels-row { display: flex; gap: 6px; flex-wrap: wrap; }
    .label-chip {
      padding: 2px 8px;
      background: rgba(139,92,246,0.15);
      color: #8b5cf6;
      border-radius: 8px;
      font-size: 0.7rem;
      font-weight: 600;
    }

    /* ── Work Plan ──────────────────────────────────────────────── */
    .branch-chip {
      padding: 2px 8px;
      background: rgba(56,189,248,0.1);
      color: rgba(56,189,248,0.7);
      border-radius: 6px;
      font-size: 0.72rem;
    }
    .tasks-list { display: flex; flex-direction: column; gap: 6px; }
    .task-item {
      padding: 8px 10px;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.04);
      border-radius: 6px;
      display: flex;
      flex-direction: column;
      gap: 5px;
    }
    .task-header { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
    .task-id { font-size: 0.7rem; color: rgba(255,255,255,0.3); }
    .task-desc { font-size: 0.8rem; color: rgba(255,255,255,0.75); flex: 1; }
    .area-badge {
      font-size: 0.65rem;
      font-weight: 800;
      text-transform: uppercase;
      padding: 1px 6px;
      border-radius: 6px;
    }
    .area-backend  { background: rgba(56,189,248,0.15); color: #38bdf8; }
    .area-frontend { background: rgba(139,92,246,0.15); color: #8b5cf6; }
    .area-infra    { background: rgba(234,179,8,0.15);  color: #eab308; }
    .area-docs     { background: rgba(148,163,184,0.1); color: #94a3b8; }

    .file-chips { display: flex; gap: 4px; flex-wrap: wrap; }
    .file-chip {
      font-size: 0.68rem;
      padding: 1px 6px;
      background: rgba(0,0,0,0.25);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 4px;
      color: rgba(255,255,255,0.45);
    }
    .deps-row { display: flex; gap: 4px; align-items: center; }
    .dep-chip {
      font-size: 0.68rem;
      padding: 1px 5px;
      background: rgba(234,179,8,0.08);
      color: rgba(234,179,8,0.6);
      border-radius: 4px;
    }

    .commands-grid {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 4px 10px;
      align-items: center;
    }
    .cmd-label { font-size: 0.7rem; color: rgba(255,255,255,0.35); }
    .cmd-value {
      font-size: 0.72rem;
      font-family: monospace;
      color: #22c55e;
      background: rgba(0,0,0,0.3);
      padding: 2px 6px;
      border-radius: 4px;
      overflow-x: auto;
      white-space: nowrap;
    }

    /* ── Judge Verdict ──────────────────────────────────────────── */
    .judge-widget .checkpoint-label {
      font-size: 0.7rem;
      color: rgba(255,255,255,0.3);
      font-family: monospace;
    }

    /* ── Test Report ────────────────────────────────────────────── */
    .ci-status-badge {
      padding: 3px 10px;
      border-radius: 10px;
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.04em;
    }
    .ci-green { background: rgba(34,197,94,0.2);  color: #22c55e; }
    .ci-red   { background: rgba(239,68,68,0.2);  color: #ef4444; }
    .pr-url-row { display: flex; gap: 8px; align-items: center; }
    .pr-url { font-size: 0.72rem; color: rgba(56,189,248,0.6); word-break: break-all; }

    .suites-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(100px, 1fr));
      gap: 8px;
    }
    .suite-card {
      padding: 10px;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.05);
      border-radius: 8px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .suite-name { font-size: 0.72rem; color: rgba(255,255,255,0.4); font-weight: 600; text-transform: uppercase; }
    .suite-status {
      font-size: 0.78rem;
      font-weight: 700;
    }
    .suite-status.pass { color: #22c55e; }
    .suite-status.fail { color: #ef4444; }
    .suite-status.skip { color: #94a3b8; }
    .suite-details {
      margin: 2px 0 0;
      padding: 0 0 0 8px;
      list-style: disc;
      font-size: 0.7rem;
      color: rgba(255,255,255,0.45);
    }
    .notes-list { display: flex; flex-direction: column; gap: 3px; }
    .note-item { margin: 0; font-size: 0.78rem; color: rgba(255,255,255,0.5); }

    /* ── Open-ended GenUI Layouts ───────────────────────────────── */
    .ui-widget { display: flex; flex-direction: column; gap: 10px; padding: 12px 14px; }
    .layout-type-chip {
      margin-left: auto;
      font-size: 0.65rem;
      font-family: monospace;
      padding: 1px 6px;
      background: rgba(255,255,255,0.05);
      color: rgba(255,255,255,0.3);
      border-radius: 6px;
    }
    /* table */
    .ui-table-wrap { overflow-x: auto; }
    .ui-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.78rem;
    }
    .ui-table th {
      text-align: left;
      padding: 5px 10px;
      color: rgba(255,255,255,0.35);
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      border-bottom: 1px solid rgba(255,255,255,0.06);
    }
    .ui-table td {
      padding: 6px 10px;
      color: rgba(255,255,255,0.7);
      border-bottom: 1px solid rgba(255,255,255,0.03);
    }
    .ui-table tr:last-child td { border-bottom: none; }
    .ui-table tbody tr:hover td { background: rgba(255,255,255,0.02); }
    /* key_value */
    .kv-grid {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 4px 14px;
      align-items: baseline;
    }
    .kv-key {
      font-size: 0.72rem;
      color: rgba(255,255,255,0.35);
      font-weight: 600;
      white-space: nowrap;
    }
    .kv-value {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.75);
      font-family: monospace;
      word-break: break-all;
    }
    /* diff */
    .diff-block {
      font-family: monospace;
      font-size: 0.75rem;
      border-radius: 6px;
      overflow: hidden;
      border: 1px solid rgba(255,255,255,0.05);
    }
    .diff-line {
      display: flex;
      align-items: flex-start;
      padding: 2px 8px;
      gap: 8px;
    }
    .diff-add { background: rgba(34,197,94,0.08); color: #86efac; }
    .diff-del { background: rgba(239,68,68,0.08);  color: #fca5a5; }
    .diff-ctx { color: rgba(255,255,255,0.35); }
    .diff-gutter { flex-shrink: 0; width: 10px; opacity: 0.6; }
    .diff-text { white-space: pre-wrap; word-break: break-all; }
    /* list */
    .ui-list {
      margin: 0;
      padding: 0 0 0 16px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .ui-list li { font-size: 0.8rem; color: rgba(255,255,255,0.7); line-height: 1.4; }
    /* metric_grid */
    .metric-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
      gap: 8px;
    }
    .metric-card {
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 8px;
      padding: 10px;
      display: flex;
      flex-direction: column;
      gap: 4px;
      align-items: flex-start;
    }
    .metric-label { font-size: 0.65rem; color: rgba(255,255,255,0.35); text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600; }
    .metric-value { font-size: 1.1rem; font-weight: 700; color: white; line-height: 1; }
    .metric-trend { font-size: 0.7rem; font-weight: 700; }
    .trend-up   { color: #22c55e; }
    .trend-down { color: #ef4444; }
    .trend-flat { color: #94a3b8; }

    /* ── Fallback raw JSON ──────────────────────────────────────── */
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
  `]
})
export class ArtifactRendererComponent implements OnChanges {
  @Input({ required: true }) artifact!: ArtifactResponse;

  private _parsed = signal<any>(null);
  type = signal<'ticket_plan' | 'work_plan' | 'judge_verdict' | 'test_report' | 'ui_layout' | 'raw'>('raw');

  ticketPlan = signal<TicketPlan | null>(null);
  workPlan   = signal<WorkPlan   | null>(null);
  judgeVerdict = signal<JudgeVerdict | null>(null);
  testReport = signal<TestReport | null>(null);
  uiLayout   = signal<UiLayout   | null>(null);

  ngOnChanges(changes: SimpleChanges) {
    if (changes['artifact'] && this.artifact) {
      this.parse();
    }
  }

  private parse() {
    const t = this.artifact.artifactType?.toLowerCase().replace(/-/g, '_');
    let parsed: any = null;

    try { parsed = JSON.parse(this.artifact.payload); } catch { /* not JSON */ }

    if (t === 'ticket_plan' && parsed) {
      this.type.set('ticket_plan');
      this.ticketPlan.set(parsed as TicketPlan);
    } else if (t === 'work_plan' && parsed) {
      this.type.set('work_plan');
      this.workPlan.set(parsed as WorkPlan);
    } else if ((t === 'judge_verdict' || t === 'verdict') && parsed) {
      this.type.set('judge_verdict');
      this.judgeVerdict.set(parsed as JudgeVerdict);
    } else if ((t === 'test_report' || t === 'ci_report') && parsed) {
      this.type.set('test_report');
      this.testReport.set(parsed as TestReport);
    } else if (parsed && parsed['_ui_layout']) {
      this.type.set('ui_layout');
      this.uiLayout.set(parsed as UiLayout);
    } else {
      this.type.set('raw');
      this._parsed.set(parsed);
    }
  }

  prettyPayload(): string {
    try { return JSON.stringify(JSON.parse(this.artifact.payload), null, 2); }
    catch { return this.artifact.payload; }
  }

  suiteClass(status?: string): string {
    if (!status) return 'skip';
    const s = status.toUpperCase();
    if (s === 'GREEN' || s === 'PASS' || s === 'PASSED') return 'pass';
    if (s === 'RED'   || s === 'FAIL' || s === 'FAILED') return 'fail';
    return 'skip';
  }

  suiteIcon(status?: string): string {
    const cls = this.suiteClass(status);
    return cls === 'pass' ? '✓' : cls === 'fail' ? '✕' : '—';
  }
}
