import { Component, OnInit, OnDestroy, signal, inject, computed } from '@angular/core';
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

interface TokenSummaryDto {
  totalTokens: number;
  llmCallCount: number;
  tokensByAgent: Record<string, number>;
}

type EventTypeFilter = 'STEP' | 'LLM_CALL' | 'SCHEMA_VALIDATION' | 'ERROR' | 'ESCALATION' | 'GRAFT';

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
        <!-- Token Consumption Summary -->
        <div class="token-summary glass-panel">
          <h3>Token Consumption</h3>
          <div class="summary-stats">
            <div class="stat-item">
              <span class="stat-label">Total Tokens</span>
              <span class="stat-value mono tabular">{{ tokenSummary()?.totalTokens || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">LLM Calls</span>
              <span class="stat-value mono tabular">{{ tokenSummary()?.llmCallCount || 0 }}</span>
            </div>
          </div>
          
          @if (tokenSummary()?.tokensByAgent) {
            <div class="tokens-by-agent">
              <h4>Tokens by Agent</h4>
              <div class="agent-bars">
                @for (agent of getAgentList(); track agent.name) {
                  <div class="agent-bar-row">
                    <span class="agent-name">{{ agent.name }}</span>
                    <div class="bar-track">
                      <div class="bar-fill" 
                           [style.width.%]="(agent.tokens / (tokenSummary()?.totalTokens || 1)) * 100"
                           [style.background]="getAgentColor($index)">
                      </div>
                    </div>
                    <span class="agent-tokens mono tabular">{{ agent.tokens }}</span>
                  </div>
                }
              </div>
            </div>
          }
        </div>

        <!-- Event Type Filters -->
        <div class="filter-section glass-panel">
          <span class="filter-label">Event Types:</span>
          <div class="filter-toggles">
            @for (type of eventTypes; track type) {
              <button 
                class="filter-toggle"
                [class.active]="activeFilters().has(type)"
                [class]="'toggle-' + type.toLowerCase()"
                (click)="toggleFilter(type)">
                <span class="indicator" [class]="'indicator-' + type.toLowerCase()"></span>
                {{ type }}
              </button>
            }
          </div>
        </div>

        <div class="waterfall glass-panel">
          <div class="timeline-header">
            <div class="col-label">Span</div>
            <div class="col-timeline">Timeline</div>
            <div class="col-duration">Duration</div>
            <div class="col-tokens">Tokens</div>
          </div>

          <div class="timeline-body">
            @for (span of filteredSpans(); track span.id) {
              <div class="span-row" 
                   [class]="'type-' + span.eventType.toLowerCase()"
                   [class.selected]="selectedSpan()?.id === span.id"
                   (click)="selectSpan(span)">
                <div class="col-label" [style.padding-left.px]="(span.depth || 0) * 20 + 8">
                  @if (span.children.length > 0) {
                    <button class="expand-btn" (click)="toggleExpand(span); $event.stopPropagation()">
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
                <div class="col-duration mono tabular">
                  {{ formatDuration(span.durationMs) }}
                </div>
                <div class="col-tokens mono tabular">
                  {{ span.tokensUsed || '-' }}
                </div>
              </div>
            }
          </div>
        </div>

        <div class="summary glass-panel">
          <div class="summary-item">
            <span class="summary-label">Total Spans</span>
            <span class="summary-value mono tabular">{{ totalSpans() }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Total Duration</span>
            <span class="summary-value mono tabular">{{ formatDuration(totalDuration()) }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Total Tokens</span>
            <span class="summary-value mono tabular">{{ totalTokens() }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">LLM Calls</span>
            <span class="summary-value mono tabular">{{ llmCallCount() }}</span>
          </div>
        </div>
      }

      <!-- Detail Drawer -->
      @if (selectedSpan()) {
        <div class="detail-drawer" (click)="closeDrawer($event)">
          <div class="drawer-content" (click)="$event.stopPropagation()">
            <div class="drawer-header">
              <h3>Span Details</h3>
              <button class="close-btn" (click)="closeDrawer($event)">&times;</button>
            </div>
            
            <div class="drawer-body">
              <!-- Chain of Thought Label -->
              <div class="detail-section">
                <h4>Description</h4>
                <p class="cot-label">{{ selectedSpan()!.label }}</p>
              </div>

              <!-- Duration Bar -->
              <div class="detail-section">
                <h4>Duration</h4>
                <div class="detail-duration-bar">
                  <div class="duration-indicator"
                       [class]="'bar-' + selectedSpan()!.eventType.toLowerCase()"
                       [style.width.%]="100">
                  </div>
                </div>
                <p class="duration-text mono tabular">{{ formatDuration(selectedSpan()!.durationMs) }}</p>
              </div>

              <!-- Token Count -->
              @if (selectedSpan()!.tokensUsed) {
                <div class="detail-section">
                  <h4>Tokens Used</h4>
                  <p class="token-count mono tabular">{{ selectedSpan()!.tokensUsed }}</p>
                </div>
              }

              <!-- Metadata JSON -->
              <div class="detail-section">
                <h4>Metadata</h4>
                <div class="metadata-kv">
                  @for (kv of getMetadataEntries(); track kv.key) {
                    <div class="kv-row">
                      <span class="kv-key mono">{{ kv.key }}</span>
                      <span class="kv-value mono">{{ kv.value }}</span>
                    </div>
                  }
                  @if (getMetadataEntries().length === 0) {
                    <p class="no-metadata">No metadata available</p>
                  }
                </div>
              </div>
            </div>
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

    /* Token Summary */
    .token-summary {
      padding: 20px;
    }
    .token-summary h3 {
      margin: 0 0 16px 0;
      font-size: 1rem;
      color: rgba(255,255,255,0.9);
    }
    .token-summary h4 {
      margin: 16px 0 12px 0;
      font-size: 0.85rem;
      color: rgba(255,255,255,0.7);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .summary-stats {
      display: flex;
      gap: 32px;
      margin-bottom: 24px;
    }
    .stat-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .stat-label {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .stat-value {
      font-size: 1.5rem;
      font-weight: 600;
      color: #38bdf8;
    }
    .tokens-by-agent {
      margin-top: 20px;
    }
    .agent-bars {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .agent-bar-row {
      display: grid;
      grid-template-columns: 140px 1fr 80px;
      gap: 12px;
      align-items: center;
      font-size: 0.85rem;
    }
    .agent-name {
      color: rgba(255,255,255,0.7);
      font-size: 0.82rem;
    }
    .bar-track {
      height: 20px;
      background: rgba(255,255,255,0.05);
      border-radius: 4px;
      overflow: hidden;
    }
    .bar-fill {
      height: 100%;
      border-radius: 4px;
      transition: width 0.3s ease;
    }
    .agent-tokens {
      text-align: right;
      color: rgba(255,255,255,0.6);
    }

    /* Filter Section */
    .filter-section {
      padding: 12px 16px;
      display: flex;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }
    .filter-label {
      font-size: 0.85rem;
      color: rgba(255,255,255,0.6);
      font-weight: 600;
    }
    .filter-toggles {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .filter-toggle {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      background: rgba(255,255,255,0.05);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 6px;
      color: rgba(255,255,255,0.5);
      cursor: pointer;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.3px;
      transition: all 0.2s;
    }
    .filter-toggle:hover {
      background: rgba(255,255,255,0.08);
      border-color: rgba(255,255,255,0.2);
    }
    .filter-toggle.active {
      background: rgba(56,189,248,0.15);
      border-color: #38bdf8;
      color: #38bdf8;
    }
    .filter-toggle .indicator {
      width: 8px;
      height: 8px;
      border-radius: 2px;
    }

    /* Waterfall */
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
      cursor: pointer;
      transition: background 0.15s;
    }
    .span-row:hover { background: rgba(255,255,255,0.04); }
    .span-row.selected {
      background: rgba(56,189,248,0.12);
      border-left: 3px solid #38bdf8;
    }
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
    .indicator-graft { background: #f97316; }
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
    .bar-graft { background: rgba(249,115,22,0.6); }
    .bar-workflow_status { background: rgba(255,255,255,0.15); }

    /* Typography utilities */
    .mono {
      font-family: var(--font-mono);
      font-size: 0.78rem;
      color: rgba(255,255,255,0.5);
    }
    .tabular {
      font-variant-numeric: tabular-nums;
    }

    /* Summary */
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
    }

    /* Detail Drawer */
    .detail-drawer {
      position: fixed;
      top: 0;
      right: 0;
      bottom: 0;
      left: 0;
      background: rgba(0,0,0,0.5);
      z-index: 1000;
      display: flex;
      justify-content: flex-end;
      animation: fadeIn 0.2s;
    }
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    .drawer-content {
      width: 500px;
      background: var(--surface-elevated);
      border-left: 1px solid var(--border);
      display: flex;
      flex-direction: column;
      animation: slideIn 0.25s ease-out;
      overflow: hidden;
    }
    @keyframes slideIn {
      from { transform: translateX(100%); }
      to { transform: translateX(0); }
    }
    .drawer-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px 24px;
      border-bottom: 1px solid var(--border);
    }
    .drawer-header h3 {
      margin: 0;
      font-size: 1.1rem;
      color: var(--text-primary);
    }
    .close-btn {
      background: none;
      border: none;
      color: rgba(255,255,255,0.5);
      font-size: 2rem;
      cursor: pointer;
      padding: 0;
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
      transition: color 0.15s;
    }
    .close-btn:hover {
      color: rgba(255,255,255,0.9);
    }
    .drawer-body {
      flex: 1;
      overflow-y: auto;
      padding: 24px;
    }
    .detail-section {
      margin-bottom: 24px;
    }
    .detail-section h4 {
      margin: 0 0 12px 0;
      font-size: 0.8rem;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      letter-spacing: 0.5px;
      font-weight: 600;
    }
    .cot-label {
      margin: 0;
      color: rgba(255,255,255,0.9);
      line-height: 1.6;
      font-size: 0.95rem;
    }
    .detail-duration-bar {
      height: 32px;
      background: rgba(255,255,255,0.05);
      border-radius: 6px;
      overflow: hidden;
      margin-bottom: 8px;
    }
    .duration-indicator {
      height: 100%;
      border-radius: 6px;
    }
    .duration-text {
      margin: 0;
      font-size: 1.1rem;
      color: rgba(255,255,255,0.7);
    }
    .token-count {
      margin: 0;
      font-size: 1.5rem;
      color: #818cf8;
      font-weight: 600;
    }
    .metadata-kv {
      background: rgba(0,0,0,0.3);
      border: 1px solid rgba(255,255,255,0.05);
      border-radius: 6px;
      overflow: hidden;
    }
    .kv-row {
      display: grid;
      grid-template-columns: 140px 1fr;
      gap: 16px;
      padding: 10px 16px;
      border-bottom: 1px solid rgba(255,255,255,0.05);
      font-size: 0.85rem;
    }
    .kv-row:last-child {
      border-bottom: none;
    }
    .kv-key {
      color: rgba(255,255,255,0.5);
      font-weight: 500;
    }
    .kv-value {
      color: rgba(255,255,255,0.8);
      word-break: break-word;
    }
    .no-metadata {
      padding: 16px;
      text-align: center;
      color: rgba(255,255,255,0.3);
      font-size: 0.85rem;
      margin: 0;
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
  selectedSpan = signal<WaterfallSpan | null>(null);
  tokenSummary = signal<TokenSummaryDto | null>(null);
  activeFilters = signal<Set<EventTypeFilter>>(new Set(['STEP', 'LLM_CALL', 'SCHEMA_VALIDATION', 'ERROR', 'ESCALATION', 'GRAFT']));

  eventTypes: EventTypeFilter[] = ['STEP', 'LLM_CALL', 'SCHEMA_VALIDATION', 'ERROR', 'ESCALATION', 'GRAFT'];

  // Computed summary signals
  totalSpans = signal(0);
  totalDuration = signal(0);
  totalTokens = signal(0);
  llmCallCount = signal(0);

  private timelineStart = 0;
  private timelineEnd = 0;

  // Computed filtered spans based on active filters
  filteredSpans = computed(() => {
    const filters = this.activeFilters();
    return this.flatSpans().filter(span => {
      const eventType = span.eventType.toUpperCase();
      // Map event types to filter types
      if (eventType.includes('STEP')) return filters.has('STEP');
      if (eventType.includes('LLM_CALL')) return filters.has('LLM_CALL');
      if (eventType.includes('SCHEMA_VALIDATION')) return filters.has('SCHEMA_VALIDATION');
      if (eventType.includes('ERROR')) return filters.has('ERROR');
      if (eventType.includes('ESCALATION')) return filters.has('ESCALATION');
      if (eventType.includes('GRAFT')) return filters.has('GRAFT');
      return true; // Show other types by default
    });
  });

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.runId.set(id);

    // Load waterfall data
    this.http.get<WaterfallSpan[]>(`/api/traces/${id}/waterfall`).subscribe({
      next: (data) => {
        this.processWaterfall(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    // Load token summary
    this.http.get<TokenSummaryDto>(`/api/analytics/traces/summary?runId=${id}`).subscribe({
      next: (summary) => {
        this.tokenSummary.set(summary);
      },
      error: (err) => {
        console.error('Failed to load token summary:', err);
      }
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

  selectSpan(span: WaterfallSpan) {
    this.selectedSpan.set(span);
  }

  closeDrawer(event: Event) {
    this.selectedSpan.set(null);
  }

  toggleFilter(type: EventTypeFilter) {
    const current = new Set(this.activeFilters());
    if (current.has(type)) {
      current.delete(type);
    } else {
      current.add(type);
    }
    this.activeFilters.set(current);
  }

  getMetadataEntries(): { key: string; value: string }[] {
    const span = this.selectedSpan();
    if (!span || !span.metadata) return [];
    
    try {
      const parsed = JSON.parse(span.metadata);
      return Object.entries(parsed).map(([key, value]) => ({
        key,
        value: typeof value === 'object' ? JSON.stringify(value) : String(value)
      }));
    } catch {
      return [{ key: 'raw', value: span.metadata }];
    }
  }

  getAgentList(): { name: string; tokens: number }[] {
    const summary = this.tokenSummary();
    if (!summary?.tokensByAgent) return [];
    
    return Object.entries(summary.tokensByAgent)
      .map(([name, tokens]) => ({ name, tokens }))
      .sort((a, b) => b.tokens - a.tokens);
  }

  getAgentColor(index: number): string {
    const colors = [
      '#38bdf8', // cyan
      '#818cf8', // indigo
      '#22c55e', // green
      '#f97316', // orange
      '#eab308', // yellow
      '#ec4899', // pink
      '#8b5cf6', // purple
      '#14b8a6', // teal
    ];
    return colors[index % colors.length];
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
