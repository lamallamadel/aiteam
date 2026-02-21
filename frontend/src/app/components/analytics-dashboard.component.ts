import { Component, OnInit, signal, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import {
  AnalyticsService,
  RunsSummaryDto, AgentsPerformanceDto, PersonasFindingsDto,
  FixLoopsDto, LatencyPoint
} from '../services/analytics.service';

Chart.register(...registerables);

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <div class="analytics-container">

      <!-- KPI row ─────────────────────────────────────────────────────── -->
      <div class="metrics-grid">
        <div class="metric-card glass-panel">
          <div class="metric-label">Total Bolts</div>
          <div class="metric-value">{{ summary()?.totalRuns ?? '—' }}</div>
        </div>
        <div class="metric-card glass-panel success">
          <div class="metric-label">Success Rate</div>
          <div class="metric-value">{{ formatPercent(summary()?.successRate) }}</div>
        </div>
        <div class="metric-card glass-panel warning">
          <div class="metric-label">Escalation Rate</div>
          <div class="metric-value">{{ formatPercent(summary()?.escalationRate) }}</div>
        </div>
        <div class="metric-card glass-panel danger">
          <div class="metric-label">Failure Rate</div>
          <div class="metric-value">{{ formatPercent(summary()?.failureRate) }}</div>
        </div>
        <div class="metric-card glass-panel">
          <div class="metric-label">Avg CI Loops</div>
          <div class="metric-value">{{ fixLoops()?.averageCiIterations?.toFixed(1) ?? '—' }}</div>
        </div>
        <div class="metric-card glass-panel">
          <div class="metric-label">Avg E2E Loops</div>
          <div class="metric-value">{{ fixLoops()?.averageE2eIterations?.toFixed(1) ?? '—' }}</div>
        </div>
      </div>

      <!-- Charts row ───────────────────────────────────────────────────── -->
      <div class="charts-grid">

        <!-- Status distribution donut -->
        <div class="chart-card glass-panel">
          <h3>Status Distribution</h3>
          <div class="chart-wrapper">
            <canvas baseChart
              [data]="statusChartData"
              [options]="donutOptions"
              type="doughnut">
            </canvas>
          </div>
        </div>

        <!-- Agent avg duration bar -->
        <div class="chart-card glass-panel">
          <h3>Agent Avg Duration (ms)</h3>
          <div class="chart-wrapper">
            <canvas baseChart
              [data]="agentDurationChartData"
              [options]="barOptions"
              type="bar">
            </canvas>
          </div>
        </div>

        <!-- Latency trend bar -->
        <div class="chart-card glass-panel">
          <h3>LLM Latency by Agent (ms)</h3>
          <div class="chart-wrapper">
            <canvas baseChart
              [data]="latencyChartData"
              [options]="barOptions"
              type="bar">
            </canvas>
          </div>
        </div>

        <!-- Persona findings -->
        <div class="chart-card glass-panel">
          <h3>Persona Findings</h3>
          <div class="persona-list">
            @if (personasFindings()?.personaStatistics?.length) {
              @for (p of personasFindings()!.personaStatistics; track p.personaName) {
                <div class="persona-item">
                  <div class="persona-header">
                    <span class="persona-name">{{ p.personaName }}</span>
                    <span class="role-chip">{{ p.personaRole }}</span>
                  </div>
                  <div class="findings-bar-row">
                    <span class="finding-chip critical">{{ p.criticalFindings }} crit</span>
                    <span class="finding-chip high">{{ p.highFindings }} high</span>
                    <span class="finding-chip medium">{{ p.mediumFindings }} med</span>
                    <span class="finding-chip low">{{ p.lowFindings }} low</span>
                    <span class="per-run">{{ p.averageFindingsPerRun.toFixed(1) }}/run</span>
                  </div>
                </div>
              }
            } @else {
              <div class="empty-msg">No persona data yet</div>
            }
          </div>
        </div>
      </div>

      <!-- Agent performance table ──────────────────────────────────────── -->
      <div class="table-card glass-panel" *ngIf="agentPerformance()?.agentMetrics?.length">
        <h3>Agent Performance</h3>
        <table class="perf-table">
          <thead>
            <tr>
              <th>Agent</th>
              <th>Runs</th>
              <th>Avg Duration</th>
              <th>Success</th>
              <th>Error Rate</th>
              <th>Avg CI Fixes</th>
              <th>Avg E2E Fixes</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of agentPerformance()!.agentMetrics">
              <td class="agent-name">{{ m.agentName }}</td>
              <td>{{ m.totalRuns }}</td>
              <td>{{ m.averageDuration | number:'1.0-0' }} ms</td>
              <td class="success-cell">{{ formatPercent(m.successRate) }}</td>
              <td class="error-cell">{{ formatPercent(m.errorRate) }}</td>
              <td>{{ m.averageCiFixCount | number:'1.1-1' }}</td>
              <td>{{ m.averageE2eFixCount | number:'1.1-1' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Fix loop patterns ────────────────────────────────────────────── -->
      <div class="table-card glass-panel" *ngIf="fixLoops()?.patterns?.length">
        <h3>Fix Loop Patterns (recent {{ fixLoops()!.patterns.length }} runs)</h3>
        <table class="perf-table">
          <thead>
            <tr>
              <th>Repo</th>
              <th>Issue</th>
              <th>CI Fixes</th>
              <th>E2E Fixes</th>
              <th>Total Iter.</th>
              <th>Status</th>
              <th>Pattern</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of fixLoops()!.patterns">
              <td class="agent-name">{{ p.repo }}</td>
              <td>#{{ p.issueNumber }}</td>
              <td>{{ p.ciFixCount }}</td>
              <td>{{ p.e2eFixCount }}</td>
              <td>{{ p.totalIterations }}</td>
              <td><span class="status-chip" [ngClass]="p.status.toLowerCase()">{{ p.status }}</span></td>
              <td class="pattern-cell">{{ p.pattern }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="error-notice" *ngIf="loadError()">
        {{ loadError() }}
      </div>
    </div>
  `,
  styles: [`
    .analytics-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 20px;
      height: 100%;
      overflow-y: auto;
    }
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
      gap: 14px;
    }
    .metric-card {
      padding: 18px;
      border-radius: 12px;
      border: 1px solid rgba(255,255,255,0.08);
    }
    .metric-card.success { border-color: rgba(34,197,94,0.3); }
    .metric-card.warning { border-color: rgba(251,191,36,0.3); }
    .metric-card.danger  { border-color: rgba(239,68,68,0.3); }
    .metric-label { font-size: 0.78rem; color: #94a3b8; margin-bottom: 8px; }
    .metric-value { font-size: 1.9rem; font-weight: 700; color: white; }

    .charts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
      gap: 20px;
    }
    .chart-card {
      padding: 20px;
      border-radius: 12px;
      border: 1px solid rgba(255,255,255,0.08);
    }
    .chart-card h3, .table-card h3 {
      margin: 0 0 16px 0;
      color: white;
      font-size: 0.95rem;
      font-weight: 600;
    }
    .chart-wrapper { position: relative; height: 260px; }

    /* Persona list */
    .persona-list { display: flex; flex-direction: column; gap: 10px; }
    .persona-item {
      padding: 10px 12px;
      background: rgba(255,255,255,0.02);
      border-radius: 8px;
      border: 1px solid rgba(255,255,255,0.04);
    }
    .persona-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
    .persona-name { font-weight: 600; color: white; font-size: 0.85rem; }
    .role-chip {
      font-size: 0.65rem;
      padding: 1px 6px;
      background: rgba(139,92,246,0.15);
      color: #8b5cf6;
      border-radius: 6px;
      font-weight: 700;
      text-transform: uppercase;
    }
    .findings-bar-row { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
    .finding-chip {
      font-size: 0.68rem;
      padding: 1px 6px;
      border-radius: 6px;
      font-weight: 600;
    }
    .finding-chip.critical { background: rgba(239,68,68,0.15); color: #ef4444; }
    .finding-chip.high { background: rgba(234,179,8,0.15); color: #eab308; }
    .finding-chip.medium { background: rgba(56,189,248,0.12); color: #38bdf8; }
    .finding-chip.low { background: rgba(148,163,184,0.12); color: #94a3b8; }
    .per-run { margin-left: auto; font-size: 0.68rem; color: rgba(255,255,255,0.3); }
    .empty-msg { color: rgba(255,255,255,0.35); font-size: 0.85rem; padding: 12px 0; }

    /* Tables */
    .table-card {
      padding: 20px;
      border-radius: 12px;
      border: 1px solid rgba(255,255,255,0.08);
    }
    .perf-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.82rem;
    }
    .perf-table th {
      text-align: left;
      padding: 8px 12px;
      color: rgba(255,255,255,0.35);
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      border-bottom: 1px solid rgba(255,255,255,0.07);
    }
    .perf-table td {
      padding: 9px 12px;
      color: rgba(255,255,255,0.7);
      border-bottom: 1px solid rgba(255,255,255,0.03);
    }
    .perf-table tr:last-child td { border-bottom: none; }
    .perf-table tbody tr:hover td { background: rgba(255,255,255,0.02); }
    .agent-name { font-weight: 600; color: #38bdf8; }
    .success-cell { color: #22c55e; font-weight: 600; }
    .error-cell { color: #ef4444; }
    .pattern-cell { font-family: monospace; font-size: 0.72rem; color: rgba(255,255,255,0.45); }
    .status-chip {
      padding: 2px 8px;
      border-radius: 8px;
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .status-chip.done     { background: rgba(34,197,94,0.15);  color: #22c55e; }
    .status-chip.failed   { background: rgba(239,68,68,0.15);  color: #ef4444; }
    .status-chip.escalated{ background: rgba(234,179,8,0.15);  color: #eab308; }

    .error-notice {
      padding: 12px 16px;
      background: rgba(239,68,68,0.1);
      border: 1px solid rgba(239,68,68,0.2);
      border-radius: 8px;
      color: #fca5a5;
      font-size: 0.82rem;
    }
  `]
})
export class AnalyticsDashboardComponent implements OnInit {
  summary         = signal<RunsSummaryDto | null>(null);
  agentPerformance= signal<AgentsPerformanceDto | null>(null);
  personasFindings= signal<PersonasFindingsDto | null>(null);
  fixLoops        = signal<FixLoopsDto | null>(null);
  latency         = signal<LatencyPoint[]>([]);
  loadError       = signal<string | null>(null);

  // ── Charts ──────────────────────────────────────────────────────────────
  statusChartData: ChartConfiguration['data'] = {
    labels: [],
    datasets: [{ data: [], backgroundColor: ['rgba(34,197,94,0.8)','rgba(239,68,68,0.8)','rgba(251,191,36,0.8)','rgba(56,189,248,0.8)'], borderWidth: 0 }]
  };

  agentDurationChartData: ChartConfiguration['data'] = {
    labels: [],
    datasets: [{ label: 'Avg Duration (ms)', data: [], backgroundColor: 'rgba(56,189,248,0.7)' }]
  };

  latencyChartData: ChartConfiguration['data'] = {
    labels: [],
    datasets: [{ label: 'Avg LLM Latency (ms)', data: [], backgroundColor: 'rgba(139,92,246,0.7)' }]
  };

  readonly donutOptions: ChartConfiguration['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { position: 'right', labels: { color: '#94a3b8', padding: 14 } } }
  };

  readonly barOptions: ChartConfiguration['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.08)' }, ticks: { color: '#94a3b8' } },
      x: { grid: { display: false }, ticks: { color: '#94a3b8' } }
    }
  };

  constructor(private analyticsService: AnalyticsService) {}

  ngOnInit() { this.load(); }

  load() {
    this.analyticsService.getSummary().subscribe({
      next: (d) => { this.summary.set(d); this.updateStatusChart(d.statusBreakdown); },
      error: () => this.loadError.set('Could not reach analytics API — backend may be offline.')
    });

    this.analyticsService.getAgentPerformance().subscribe({
      next: (d) => { this.agentPerformance.set(d); this.updateAgentDurationChart(d); }
    });

    this.analyticsService.getPersonasFindings().subscribe({
      next: (d) => this.personasFindings.set(d)
    });

    this.analyticsService.getFixLoops().subscribe({
      next: (d) => this.fixLoops.set(d)
    });

    this.analyticsService.getLatencyTrend().subscribe({
      next: (pts) => { this.latency.set(pts); this.updateLatencyChart(pts); }
    });
  }

  private updateStatusChart(breakdown: Record<string, number>) {
    const entries = Object.entries(breakdown);
    this.statusChartData = {
      labels: entries.map(([k]) => k),
      datasets: [{
        data: entries.map(([, v]) => v),
        backgroundColor: ['rgba(34,197,94,0.8)','rgba(239,68,68,0.8)','rgba(251,191,36,0.8)','rgba(56,189,248,0.8)','rgba(148,163,184,0.8)'],
        borderWidth: 0
      }]
    };
  }

  private updateAgentDurationChart(perf: AgentsPerformanceDto) {
    const metrics = perf.agentMetrics ?? [];
    this.agentDurationChartData = {
      labels: metrics.map(m => m.agentName),
      datasets: [{ label: 'Avg Duration (ms)', data: metrics.map(m => m.averageDuration), backgroundColor: 'rgba(56,189,248,0.7)' }]
    };
  }

  private updateLatencyChart(pts: LatencyPoint[]) {
    this.latencyChartData = {
      labels: pts.map(p => p.agentName),
      datasets: [{ label: 'Avg LLM Latency (ms)', data: pts.map(p => p.avgLatencyMs), backgroundColor: 'rgba(139,92,246,0.7)' }]
    };
  }

  formatPercent(v: number | undefined): string {
    if (v === undefined || v === null) return '—';
    return `${Math.round(v * 100)}%`;
  }
}
