import { Component, OnInit, signal, CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { AnalyticsService } from '../services/analytics.service';

Chart.register(...registerables);

interface AnalyticsSummary {
  totalRuns: number;
  successRate: number;
  failureRate: number;
  escalationRate: number;
  statusBreakdown: Record<string, number>;
  repoBreakdown: Record<string, number>;
}

interface AgentPerformance {
  avgDurationByAgent: Record<string, number>;
  errorRateByAgent: Record<string, number>;
  avgFixCountByStatus: Record<string, number>;
}

interface PersonaEffectiveness {
  personaMetrics: Record<string, PersonaMetrics>;
  configurationRecommendations: string[];
}

interface PersonaMetrics {
  reviewsCount: number;
  criticalFindings: number;
  effectivenessScore: number;
  falsePositives: number;
}

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <div class="analytics-container">
      <div class="metrics-grid">
        <div class="metric-card glass-panel">
          <div class="metric-label">Total Runs</div>
          <div class="metric-value">{{ summary()?.totalRuns || 0 }}</div>
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
      </div>

      <div class="charts-grid">
        <div class="chart-card glass-panel">
          <h3>Success Rate Trend</h3>
          <div class="chart-wrapper">
            <canvas 
              baseChart
              [data]="successRateChartData"
              [options]="successRateChartOptions"
              [type]="'line'"
            ></canvas>
          </div>
        </div>

        <div class="chart-card glass-panel">
          <h3>Agent Performance</h3>
          <div class="chart-wrapper">
            <canvas 
              baseChart
              [data]="agentPerformanceChartData"
              [options]="agentPerformanceChartOptions"
              [type]="'bar'"
            ></canvas>
          </div>
        </div>

        <div class="chart-card glass-panel">
          <h3>Status Distribution</h3>
          <div class="chart-wrapper">
            <canvas 
              baseChart
              [data]="statusDistributionChartData"
              [options]="statusDistributionChartOptions"
              [type]="'doughnut'"
            ></canvas>
          </div>
        </div>

        <div class="chart-card glass-panel">
          <h3>Persona Effectiveness</h3>
          <div class="persona-list">
            <div *ngFor="let persona of getPersonasList()" class="persona-item">
              <div class="persona-header">
                <span class="persona-name">{{ persona.name }}</span>
                <span class="effectiveness-score" [ngClass]="getScoreClass(persona.score)">
                  {{ formatPercent(persona.score) }}
                </span>
              </div>
              <div class="persona-stats">
                <span class="stat">{{ persona.reviews }} reviews</span>
                <span class="stat critical">{{ persona.critical }} critical</span>
                <span class="stat warning">{{ persona.falsePositives }} false positives</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="recommendations glass-panel" *ngIf="personaEffectiveness()">
        <h3>Configuration Recommendations</h3>
        <ul>
          <li *ngFor="let rec of personaEffectiveness()?.configurationRecommendations">
            {{ rec }}
          </li>
        </ul>
      </div>
    </div>
  `,
  styles: [`
    .analytics-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }

    .metric-card {
      padding: 20px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.1);
    }

    .metric-card.success {
      border-color: rgba(34, 197, 94, 0.3);
    }

    .metric-card.warning {
      border-color: rgba(251, 191, 36, 0.3);
    }

    .metric-card.danger {
      border-color: rgba(239, 68, 68, 0.3);
    }

    .metric-label {
      font-size: 0.875rem;
      color: #94a3b8;
      margin-bottom: 8px;
    }

    .metric-value {
      font-size: 2rem;
      font-weight: 700;
      color: white;
    }

    .charts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
      gap: 24px;
    }

    .chart-card {
      padding: 24px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid rgba(255, 255, 255, 0.1);
    }

    .chart-card h3 {
      margin: 0 0 20px 0;
      color: white;
      font-size: 1.125rem;
    }

    .chart-wrapper {
      position: relative;
      height: 300px;
    }

    .persona-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .persona-item {
      padding: 16px;
      background: rgba(255, 255, 255, 0.02);
      border-radius: 8px;
      border: 1px solid rgba(255, 255, 255, 0.05);
    }

    .persona-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    .persona-name {
      font-weight: 600;
      color: white;
    }

    .effectiveness-score {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.875rem;
      font-weight: 600;
    }

    .effectiveness-score.high {
      background: rgba(34, 197, 94, 0.2);
      color: #22c55e;
    }

    .effectiveness-score.medium {
      background: rgba(251, 191, 36, 0.2);
      color: #fbbf24;
    }

    .effectiveness-score.low {
      background: rgba(239, 68, 68, 0.2);
      color: #ef4444;
    }

    .persona-stats {
      display: flex;
      gap: 16px;
      font-size: 0.875rem;
      color: #94a3b8;
    }

    .stat.critical {
      color: #ef4444;
    }

    .stat.warning {
      color: #fbbf24;
    }

    .recommendations {
      padding: 24px;
      border-radius: 12px;
      background: rgba(56, 189, 248, 0.05);
      border: 1px solid rgba(56, 189, 248, 0.2);
    }

    .recommendations h3 {
      margin: 0 0 16px 0;
      color: white;
    }

    .recommendations ul {
      margin: 0;
      padding-left: 24px;
      color: #94a3b8;
    }

    .recommendations li {
      margin-bottom: 8px;
    }
  `]
})
export class AnalyticsDashboardComponent implements OnInit {
  summary = signal<AnalyticsSummary | null>(null);
  agentPerformance = signal<AgentPerformance | null>(null);
  personaEffectiveness = signal<PersonaEffectiveness | null>(null);

  successRateChartData: ChartConfiguration['data'] = {
    labels: ['Week 1', 'Week 2', 'Week 3', 'Week 4'],
    datasets: [
      {
        label: 'Success Rate',
        data: [85, 88, 92, 94],
        borderColor: '#22c55e',
        backgroundColor: 'rgba(34, 197, 94, 0.1)',
        tension: 0.4,
        fill: true
      }
    ]
  };

  successRateChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false
      }
    },
    scales: {
      y: {
        beginAtZero: true,
        max: 100,
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        ticks: {
          color: '#94a3b8'
        }
      },
      x: {
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        ticks: {
          color: '#94a3b8'
        }
      }
    }
  };

  agentPerformanceChartData: ChartConfiguration['data'] = {
    labels: ['PM', 'Qualifier', 'Developer', 'Tester', 'Writer'],
    datasets: [
      {
        label: 'Avg Duration (min)',
        data: [2.5, 1.8, 15.2, 8.4, 3.1],
        backgroundColor: [
          'rgba(56, 189, 248, 0.8)',
          'rgba(139, 92, 246, 0.8)',
          'rgba(34, 197, 94, 0.8)',
          'rgba(251, 191, 36, 0.8)',
          'rgba(239, 68, 68, 0.8)'
        ]
      }
    ]
  };

  agentPerformanceChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false
      }
    },
    scales: {
      y: {
        beginAtZero: true,
        grid: {
          color: 'rgba(255, 255, 255, 0.1)'
        },
        ticks: {
          color: '#94a3b8'
        }
      },
      x: {
        grid: {
          display: false
        },
        ticks: {
          color: '#94a3b8'
        }
      }
    }
  };

  statusDistributionChartData: ChartConfiguration['data'] = {
    labels: ['Done', 'Failed', 'Escalated', 'In Progress'],
    datasets: [
      {
        data: [65, 15, 12, 8],
        backgroundColor: [
          'rgba(34, 197, 94, 0.8)',
          'rgba(239, 68, 68, 0.8)',
          'rgba(251, 191, 36, 0.8)',
          'rgba(56, 189, 248, 0.8)'
        ],
        borderWidth: 0
      }
    ]
  };

  statusDistributionChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'right',
        labels: {
          color: '#94a3b8',
          padding: 16
        }
      }
    }
  };

  constructor(private analyticsService: AnalyticsService) {}

  ngOnInit() {
    this.loadAnalytics();
  }

  loadAnalytics() {
    this.analyticsService.getSummary().subscribe({
      next: (data) => {
        this.summary.set(data);
        this.updateStatusChart(data.statusBreakdown);
      },
      error: (err) => {
        console.error('Failed to load analytics summary', err);
        this.loadMockData();
      }
    });

    this.analyticsService.getAgentPerformance().subscribe({
      next: (data) => {
        this.agentPerformance.set(data);
        this.updateAgentChart(data);
      },
      error: (err) => console.error('Failed to load agent performance', err)
    });

    this.analyticsService.getPersonaEffectiveness().subscribe({
      next: (data) => this.personaEffectiveness.set(data),
      error: (err) => console.error('Failed to load persona effectiveness', err)
    });
  }

  loadMockData() {
    const mockSummary: AnalyticsSummary = {
      totalRuns: 247,
      successRate: 0.94,
      failureRate: 0.04,
      escalationRate: 0.02,
      statusBreakdown: {
        'DONE': 232,
        'FAILED': 10,
        'ESCALATED': 5,
        'DEVELOPER': 0
      },
      repoBreakdown: {}
    };
    this.summary.set(mockSummary);
  }

  updateStatusChart(breakdown: Record<string, number>) {
    if (!breakdown) return;
    
    const labels: string[] = [];
    const data: number[] = [];
    
    Object.entries(breakdown).forEach(([status, count]) => {
      labels.push(status);
      data.push(count);
    });

    this.statusDistributionChartData = {
      ...this.statusDistributionChartData,
      labels,
      datasets: [{
        ...this.statusDistributionChartData.datasets[0],
        data
      }]
    };
  }

  updateAgentChart(performance: AgentPerformance) {
    if (!performance.avgDurationByAgent) return;

    const labels: string[] = [];
    const data: number[] = [];

    Object.entries(performance.avgDurationByAgent).forEach(([agent, duration]) => {
      labels.push(agent);
      data.push(duration);
    });

    this.agentPerformanceChartData = {
      ...this.agentPerformanceChartData,
      labels,
      datasets: [{
        ...this.agentPerformanceChartData.datasets[0],
        data
      }]
    };
  }

  getPersonasList() {
    if (!this.personaEffectiveness()?.personaMetrics) return [];

    return Object.entries(this.personaEffectiveness()!.personaMetrics).map(([name, metrics]) => ({
      name,
      score: metrics.effectivenessScore,
      reviews: metrics.reviewsCount,
      critical: metrics.criticalFindings,
      falsePositives: metrics.falsePositives
    }));
  }

  formatPercent(value: number | undefined): string {
    if (value === undefined) return '0%';
    return `${Math.round(value * 100)}%`;
  }

  getScoreClass(score: number): string {
    if (score >= 0.8) return 'high';
    if (score >= 0.5) return 'medium';
    return 'low';
  }
}
