import { Component, OnInit, OnDestroy, signal, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { WorkflowStreamStore } from '../../services/workflow-stream.store';

interface WaterfallSpan {
  id: string;
  parentEventId?: string;
  eventType: string;
  agentName: string;
  label: string;
  startTime: string;
  endTime?: string;
  durationMs?: number;
  tokensUsed?: number;
  metadata?: string;
  children: WaterfallSpan[];
  expanded?: boolean;
  depth?: number;
}

@Component({
  selector: 'app-waterfall-trace',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="waterfall-container">
      <div class="header">
        <a [routerLink]="['/runs', runId()]" class="back-link">&larr; Back to Bolt</a>
        <h2>Waterfall Trace</h2>
        @if (streamStore.isStreaming()) {
          <span class="live-badge">LIVE</span>
        }
      </div>

      @if (loading()) {
        <div class="loading glass-panel">Loading trace data...</div>
      }

      @if (!loading() && flatSpans().length === 0) {
        <div class="empty glass-panel">No trace events recorded yet.</div>
      }

      @if (!loading() && flatSpans().length > 0) {
        <div class="waterfall glass-panel">
          <div class="timeline-header">
            <div class="col-label">Span</div>
            <div class="col-timeline">Timeline</div>
            <div class="col-duration">Duration</div>
            <div class="col-tokens">Tokens</div>
          </div>

          <div class="timeline-body">
            @for (span of flatSpans(); track span.id) {
              <div class="span-row" [class]="'type-' + span.eventType.toLowerCase()">
                <div class="col-label" [style.padding-left.px]="(span.depth || 0) * 20 + 8">
                  @if (span.children.length > 0) {
                    <button class="expand-btn" (click)="toggleExpand(span)">
                      {{ span.expanded ? '▼' : '▶' }}
                    </button>
                  }
                  <span class="type-indicator" [class]="'indicator-' + span.eventType.toLowerCase()"></span>
                  <span class="span-label">{{ span.label }}</span>
                </div>
                <div class="col-timeline">
                  <div class="bar-container">
                    <div class="duration-bar"
                         [class]="'bar-' + span.eventType.toLowerCase()"
                         [style.left.%]="getBarLeft(span)"
                         [style.width.%]="getBarWidth(span)">
                    </div>
                  </div>
                </div>
                <div class="col-duration mono">
                  {{ formatDuration(span.durationMs) }}
                </div>
                <div class="col-tokens mono">
                  {{ span.tokensUsed || '-' }}
                </div>
              </div>
            }
          </div>
        </div>

        <div class="summary glass-panel">
          <div class="summary-item">
            <span class="summary-label">Total Spans</span>
            <span class="summary-value">{{ totalSpans() }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Total Duration</span>
            <span class="summary-value">{{ formatDuration(totalDuration()) }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Total Tokens</span>
            <span class="summary-value">{{ totalTokens() }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">LLM Calls</span>
            <span class="summary-value">{{ llmCallCount() }}</span>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .waterfall-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      height: 100%;
      overflow-y: auto;
    }
    .header { display: flex; align-items: center; gap: 16px; }
    .header h2 { flex: 1; }
    .back-link { color: #38bdf8; text-decoration: none; font-size: 0.9rem; }
    .live-badge {
      background: #22c55e;
      color: white;
      padding: 2px 8px;
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 700;
      animation: pulse 2s infinite;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.5; }
    }
    .loading, .empty { padding: 24px; text-align: center; color: rgba(255,255,255,0.5); }
    .waterfall { padding: 0; overflow: hidden; }
    .timeline-header {
      display: grid;
      grid-template-columns: 280px 1fr 90px 80px;
      gap: 8px;
      padding: 10px 12px;
      background: rgba(255,255,255,0.05);
      font-size: 0.75rem;
      font-weight: 600;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .timeline-body { max-height: 600px; overflow-y: auto; }
    .span-row {
      display: grid;
      grid-template-columns: 280px 1fr 90px 80px;
      gap: 8px;
      padding: 6px 12px;
      align-items: center;
      border-bottom: 1px solid rgba(255,255,255,0.03);
      font-size: 0.82rem;
    }
    .span-row:hover { background: rgba(255,255,255,0.02); }
    .col-label {
      display: flex;
      align-items: center;
      gap: 6px;
      overflow: hidden;
    }
    .expand-btn {
      background: none;
      border: none;
      color: rgba(255,255,255,0.4);
      cursor: pointer;
      font-size: 0.65rem;
      padding: 0;
      width: 14px;
    }
    .type-indicator {
      width: 8px;
      height: 8px;
      border-radius: 2px;
      flex-shrink: 0;
    }
    .indicator-step { background: #38bdf8; }
    .indicator-llm_call { background: #818cf8; }
    .indicator-schema_validation { background: #22c55e; }
    .indicator-error { background: #ef4444; }
    .indicator-escalation { background: #eab308; }
    .indicator-workflow_status { background: rgba(255,255,255,0.3); }
    .span-label {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      color: rgba(255,255,255,0.8);
    }
    .col-timeline { position: relative; height: 18px; }
    .bar-container {
      position: absolute;
      inset: 0;
      background: rgba(255,255,255,0.02);
      border-radius: 3px;
    }
    .duration-bar {
      position: absolute;
      top: 2px;
      bottom: 2px;
      border-radius: 2px;
      min-width: 2px;
    }
    .bar-step { background: rgba(56,189,248,0.6); }
    .bar-llm_call { background: rgba(129,140,248,0.6); }
    .bar-schema_validation { background: rgba(34,197,94,0.6); }
    .bar-error { background: rgba(239,68,68,0.6); }
    .bar-escalation { background: rgba(234,179,8,0.6); }
    .bar-workflow_status { background: rgba(255,255,255,0.15); }
    .mono {
      font-family: monospace;
      font-size: 0.78rem;
      color: rgba(255,255,255,0.5);
    }
    .summary {
      display: flex;
      justify-content: space-around;
      padding: 16px;
    }
    .summary-item { text-align: center; }
    .summary-label {
      display: block;
      font-size: 0.75rem;
      color: rgba(255,255,255,0.4);
      margin-bottom: 4px;
    }
    .summary-value {
      font-size: 1.2rem;
      font-weight: 600;
      color: white;
      font-family: monospace;
    }
  `]
})
export class WaterfallTraceComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  streamStore = inject(WorkflowStreamStore);

  runId = signal('');
  waterfallData = signal<WaterfallSpan[]>([]);
  flatSpans = signal<WaterfallSpan[]>([]);
  loading = signal(true);

  // Computed summary signals
  totalSpans = signal(0);
  totalDuration = signal(0);
  totalTokens = signal(0);
  llmCallCount = signal(0);

  private timelineStart = 0;
  private timelineEnd = 0;

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.runId.set(id);

    this.http.get<WaterfallSpan[]>(`/api/traces/${id}/waterfall`).subscribe({
      next: (data) => {
        this.processWaterfall(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    this.streamStore.connectToRun(id);
  }

  ngOnDestroy() {
    this.streamStore.disconnect();
  }

  private processWaterfall(data: WaterfallSpan[]) {
    // Set expansion default
    const annotated = this.annotateDepth(data, 0, true);
    this.waterfallData.set(annotated);

    // Compute timeline bounds
    const allSpans = this.flattenAll(annotated);
    if (allSpans.length > 0) {
      this.timelineStart = Math.min(...allSpans.map(s => new Date(s.startTime).getTime()));
      this.timelineEnd = Math.max(...allSpans.map(s =>
        s.endTime ? new Date(s.endTime).getTime() : new Date(s.startTime).getTime() + (s.durationMs || 0)
      ));
    }

    this.updateFlatList();
    this.updateSummary(allSpans);
  }

  private annotateDepth(spans: WaterfallSpan[], depth: number, expanded: boolean): WaterfallSpan[] {
    return spans.map(s => ({
      ...s,
      depth,
      expanded,
      children: this.annotateDepth(s.children || [], depth + 1, true)
    }));
  }

  private flattenAll(spans: WaterfallSpan[]): WaterfallSpan[] {
    const result: WaterfallSpan[] = [];
    for (const s of spans) {
      result.push(s);
      if (s.children) {
        result.push(...this.flattenAll(s.children));
      }
    }
    return result;
  }

  private updateFlatList() {
    const flat: WaterfallSpan[] = [];
    this.flattenVisible(this.waterfallData(), flat);
    this.flatSpans.set(flat);
  }

  private flattenVisible(spans: WaterfallSpan[], result: WaterfallSpan[]) {
    for (const s of spans) {
      result.push(s);
      if (s.expanded && s.children) {
        this.flattenVisible(s.children, result);
      }
    }
  }

  private updateSummary(allSpans: WaterfallSpan[]) {
    this.totalSpans.set(allSpans.length);
    this.totalDuration.set(
      this.timelineEnd > this.timelineStart ? this.timelineEnd - this.timelineStart : 0
    );
    this.totalTokens.set(
      allSpans.reduce((sum, s) => sum + (s.tokensUsed || 0), 0)
    );
    this.llmCallCount.set(
      allSpans.filter(s => s.eventType === 'LLM_CALL').length
    );
  }

  toggleExpand(span: WaterfallSpan) {
    span.expanded = !span.expanded;
    this.updateFlatList();
  }

  getBarLeft(span: WaterfallSpan): number {
    if (this.timelineEnd <= this.timelineStart) return 0;
    const start = new Date(span.startTime).getTime();
    return ((start - this.timelineStart) / (this.timelineEnd - this.timelineStart)) * 100;
  }

  getBarWidth(span: WaterfallSpan): number {
    if (this.timelineEnd <= this.timelineStart) return 2;
    const duration = span.durationMs || 0;
    const width = (duration / (this.timelineEnd - this.timelineStart)) * 100;
    return Math.max(width, 0.5);
  }

  formatDuration(ms?: number): string {
    if (!ms && ms !== 0) return '-';
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}m`;
  }
}
