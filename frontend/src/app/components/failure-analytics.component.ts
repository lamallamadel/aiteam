import { Component, OnInit, signal, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import {
  AnalyticsService,
  EscalationInsightDto,
  EscalationCluster,
  KeywordInsight
} from '../services/analytics.service';

Chart.register(...registerables);

@Component({
  selector: 'app-failure-analytics',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <div class="failure-analytics-container">

      <!-- KPI row ─────────────────────────────────────────────────────── -->
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-label">Total Escalations</div>
          <div class="metric-value">{{ insights()?.totalEscalationsAnalysed ?? '—' }}</div>
        </div>
        <div class="metric-card warning">
          <div class="metric-label">Top Error Pattern</div>
          <div class="metric-value metric-value-small">{{ topErrorPatternCount() }}</div>
          <div class="metric-sublabel">{{ topErrorPatternLabel() }}</div>
        </div>
        <div class="metric-card danger">
          <div class="metric-label">Top Agent Bottleneck</div>
          <div class="metric-value metric-value-small">{{ topAgentBottleneck() }}</div>
        </div>
      </div>

      <!-- Error clusters chart ──────────────────────────────────────────── -->
      <div class="chart-card">
        <h3>Error Clusters</h3>
        <div class="chart-wrapper">
          <canvas baseChart
            [data]="errorClustersChartData"
            [options]="horizontalBarOptions"
            type="bar">
          </canvas>
        </div>
      </div>

      <!-- Agent bottlenecks table ───────────────────────────────────────── -->
      <div class="table-card" *ngIf="agentBottlenecks().length">
        <h3>Agent Bottlenecks</h3>
        <table class="bottleneck-table">
          <thead>
            <tr>
              <th>Agent Name</th>
              <th>Escalation Count</th>
              <th>Percentage</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let bottleneck of agentBottlenecks()">
              <td class="agent-name">{{ bottleneck.agent }}</td>
              <td class="tabular-nums">{{ bottleneck.count }}</td>
              <td class="tabular-nums percentage-cell">{{ bottleneck.percentage }}%</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Problematic files ─────────────────────────────────────────────── -->
      <div class="card-section" *ngIf="insights()?.problematicFiles?.length">
        <h3>Problematic Files</h3>
        <div class="file-list">
          @for (file of insights()!.problematicFiles; track file; let idx = $index) {
            <div class="file-item">
              <span class="frequency-badge">{{ idx + 1 }}</span>
              <span class="file-path">{{ file }}</span>
              <span class="file-type-chip">{{ getFileExtension(file) }}</span>
            </div>
          }
        </div>
      </div>

      <!-- Keyword cloud ─────────────────────────────────────────────────── -->
      <div class="card-section" *ngIf="insights()?.keywords?.length">
        <h3>Keyword Cloud</h3>
        <div class="keyword-cloud">
          @for (kw of insights()!.keywords; track kw.keyword) {
            <span class="keyword-chip" [style.font-size.rem]="getKeywordSize(kw.frequency)">
              {{ kw.keyword }}
            </span>
          }
        </div>
      </div>

      <div class="error-notice" *ngIf="loadError()">
        {{ loadError() }}
      </div>
    </div>
  `,
  styles: [`
    .failure-analytics-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 20px;
      height: 100%;
      overflow-y: auto;
    }

    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1px;
      background: var(--border);
    }

    .metric-card {
      padding: 18px;
      border-radius: 0;
      border: none;
      background: var(--surface);
    }

    .metric-card.warning { border-left: 2px solid rgba(251,191,36,0.5); }
    .metric-card.danger  { border-left: 2px solid rgba(239,68,68,0.5); }

    .metric-label {
      font-size: 0.78rem;
      color: #94a3b8;
      margin-bottom: 8px;
    }

    .metric-value {
      font-size: 1.9rem;
      font-weight: 700;
      color: white;
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
    }

    .metric-value-small {
      font-size: 1.5rem;
    }

    .metric-sublabel {
      font-size: 0.7rem;
      color: rgba(255,255,255,0.4);
      margin-top: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .chart-card, .table-card, .card-section {
      padding: 20px;
      border-radius: 0;
      border: none;
      background: var(--surface);
    }

    .chart-card h3, .table-card h3, .card-section h3 {
      margin: 0 0 16px 0;
      color: white;
      font-size: 0.95rem;
      font-weight: 600;
    }

    .chart-wrapper {
      position: relative;
      height: 400px;
    }

    .bottleneck-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.82rem;
    }

    .bottleneck-table th {
      text-align: left;
      padding: 8px 12px;
      color: rgba(255,255,255,0.35);
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      border-bottom: 1px solid var(--border);
    }

    .bottleneck-table td {
      padding: 9px 12px;
      color: rgba(255,255,255,0.7);
      border-bottom: 1px solid var(--border);
    }

    .bottleneck-table tr:last-child td { border-bottom: none; }
    .bottleneck-table tbody tr:hover td { background: rgba(255,255,255,0.02); }

    .agent-name {
      font-weight: 600;
      color: #38bdf8;
    }

    .tabular-nums {
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
    }

    .percentage-cell {
      color: #eab308;
      font-weight: 600;
    }

    .file-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .file-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      background: rgba(255,255,255,0.02);
      border-radius: 8px;
      border: 1px solid rgba(255,255,255,0.04);
    }

    .frequency-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 28px;
      height: 28px;
      padding: 0 8px;
      background: rgba(239,68,68,0.15);
      color: #ef4444;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 700;
      font-family: var(--font-mono);
    }

    .file-path {
      flex: 1;
      font-family: monospace;
      font-size: 0.82rem;
      color: rgba(255,255,255,0.7);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .file-type-chip {
      padding: 2px 8px;
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      border-radius: 6px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .keyword-cloud {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      align-items: center;
      padding: 10px 0;
    }

    .keyword-chip {
      padding: 6px 12px;
      background: rgba(139,92,246,0.15);
      color: #8b5cf6;
      border-radius: 8px;
      font-weight: 600;
      line-height: 1.4;
      transition: all 0.2s;
    }

    .keyword-chip:hover {
      background: rgba(139,92,246,0.25);
      transform: scale(1.05);
    }

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
export class FailureAnalyticsComponent implements OnInit {
  insights = signal<EscalationInsightDto | null>(null);
  loadError = signal<string | null>(null);

  errorClustersChartData: ChartConfiguration['data'] = {
    labels: [],
    datasets: [{
      label: 'Error Count',
      data: [],
      backgroundColor: 'rgba(239,68,68,0.7)',
      borderColor: 'rgba(239,68,68,1)',
      borderWidth: 1
    }]
  };

  readonly horizontalBarOptions: ChartConfiguration['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          afterLabel: (context) => {
            const clusters = this.insights()?.clusters ?? [];
            const cluster = clusters[context.dataIndex];
            return cluster?.suggestedRootCause ? `Root cause: ${cluster.suggestedRootCause}` : '';
          }
        }
      }
    },
    scales: {
      x: {
        beginAtZero: true,
        grid: { color: 'rgba(255,255,255,0.08)' },
        ticks: { color: '#94a3b8' }
      },
      y: {
        grid: { display: false },
        ticks: { color: '#94a3b8' }
      }
    }
  };

  constructor(private analyticsService: AnalyticsService) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.analyticsService.getEscalationInsights().subscribe({
      next: (data) => {
        this.insights.set(data);
        this.updateErrorClustersChart(data.clusters);
      },
      error: () => this.loadError.set('Could not fetch escalation insights — backend may be offline.')
    });
  }

  private updateErrorClustersChart(clusters: EscalationCluster[]) {
    this.errorClustersChartData = {
      labels: clusters.map(c => c.label),
      datasets: [{
        label: 'Error Count',
        data: clusters.map(c => c.count),
        backgroundColor: 'rgba(239,68,68,0.7)',
        borderColor: 'rgba(239,68,68,1)',
        borderWidth: 1
      }]
    };
  }

  topErrorPatternCount(): string {
    const patterns = this.insights()?.topErrorPatterns;
    if (!patterns || Object.keys(patterns).length === 0) return '—';
    const entries = Object.entries(patterns);
    const max = entries.reduce((a, b) => a[1] > b[1] ? a : b);
    return max[1].toString();
  }

  topErrorPatternLabel(): string {
    const patterns = this.insights()?.topErrorPatterns;
    if (!patterns || Object.keys(patterns).length === 0) return '';
    const entries = Object.entries(patterns);
    const max = entries.reduce((a, b) => a[1] > b[1] ? a : b);
    return max[0];
  }

  topAgentBottleneck(): string {
    const bottlenecks = this.insights()?.agentBottlenecks;
    if (!bottlenecks || Object.keys(bottlenecks).length === 0) return '—';
    const entries = Object.entries(bottlenecks);
    const max = entries.reduce((a, b) => a[1] > b[1] ? a : b);
    return max[0];
  }

  agentBottlenecks(): Array<{ agent: string; count: number; percentage: string }> {
    const bottlenecks = this.insights()?.agentBottlenecks;
    if (!bottlenecks) return [];
    
    const total = Object.values(bottlenecks).reduce((sum, count) => sum + count, 0);
    return Object.entries(bottlenecks)
      .map(([agent, count]) => ({
        agent,
        count,
        percentage: ((count / total) * 100).toFixed(1)
      }))
      .sort((a, b) => b.count - a.count);
  }

  getFileExtension(filename: string): string {
    const parts = filename.split('.');
    if (parts.length === 1) return 'file';
    return parts[parts.length - 1];
  }

  getKeywordSize(frequency: number): number {
    const maxFreq = Math.max(...(this.insights()?.keywords?.map(k => k.frequency) ?? [1]));
    const minSize = 0.8;
    const maxSize = 1.8;
    const normalized = frequency / maxFreq;
    return minSize + (normalized * (maxSize - minSize));
  }
}
