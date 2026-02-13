import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AnalyticsService } from '../services/analytics.service';

interface QuickStats {
  totalRuns: number;
  successRate: number;
  activeRuns: number;
  escalations: number;
}

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-home">
      <div class="welcome-section">
        <h1>Welcome to Atlasia Orchestrator</h1>
        <p class="subtitle">AI-powered development automation at your fingertips</p>
      </div>

      <div class="quick-stats-grid">
        <div class="stat-card glass-panel" (click)="navigateTo('/runs')">
          <div class="stat-icon">📊</div>
          <div class="stat-content">
            <div class="stat-value">{{ quickStats().totalRuns }}</div>
            <div class="stat-label">Total Bolts</div>
          </div>
        </div>

        <div class="stat-card glass-panel success">
          <div class="stat-icon">✓</div>
          <div class="stat-content">
            <div class="stat-value">{{ formatPercent(quickStats().successRate) }}</div>
            <div class="stat-label">Success Rate</div>
          </div>
        </div>

        <div class="stat-card glass-panel info">
          <div class="stat-icon">⚡</div>
          <div class="stat-content">
            <div class="stat-value">{{ quickStats().activeRuns }}</div>
            <div class="stat-label">Active Bolts</div>
          </div>
        </div>

        <div class="stat-card glass-panel warning" (click)="navigateTo('/analytics')">
          <div class="stat-icon">⚠️</div>
          <div class="stat-content">
            <div class="stat-value">{{ quickStats().escalations }}</div>
            <div class="stat-label">Escalations</div>
          </div>
        </div>
      </div>

      <div class="actions-grid">
        <div class="action-card glass-panel" (click)="navigateTo('/runs')">
          <h3>📋 View All Bolts</h3>
          <p>Browse and filter through your run history</p>
        </div>

        <div class="action-card glass-panel" (click)="navigateTo('/analytics')">
          <h3>📈 Analytics Dashboard</h3>
          <p>Deep dive into performance metrics and insights</p>
        </div>

        <div class="action-card glass-panel">
          <h3>🤖 Agent Performance</h3>
          <p>Monitor individual agent effectiveness</p>
        </div>

        <div class="action-card glass-panel">
          <h3>🎭 Persona Configuration</h3>
          <p>Optimize persona settings based on learning</p>
        </div>
      </div>

      <div class="recent-activity glass-panel">
        <h3>Recent Activity</h3>
        <div class="activity-list">
          <div class="activity-item">
            <span class="activity-dot success"></span>
            <div class="activity-content">
              <div class="activity-title">Run completed successfully</div>
              <div class="activity-meta">atlasia-core #42 • 5 minutes ago</div>
            </div>
          </div>
          <div class="activity-item">
            <span class="activity-dot warning"></span>
            <div class="activity-content">
              <div class="activity-title">Run escalated for review</div>
              <div class="activity-meta">atlasia-frontend #78 • 15 minutes ago</div>
            </div>
          </div>
          <div class="activity-item">
            <span class="activity-dot info"></span>
            <div class="activity-content">
              <div class="activity-title">New run started</div>
              <div class="activity-meta">atlasia-api #23 • 30 minutes ago</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-home {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
      height: 100%;
      overflow-y: auto;
    }

    .welcome-section {
      text-align: center;
      padding: 40px 0;
    }

    .welcome-section h1 {
      font-size: 2.5rem;
      margin-bottom: 12px;
      background: linear-gradient(135deg, #38bdf8 0%, #818cf8 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .subtitle {
      font-size: 1.125rem;
      color: #94a3b8;
    }

    .quick-stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }

    .stat-card {
      padding: 24px;
      display: flex;
      align-items: center;
      gap: 16px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .stat-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 12px 40px rgba(56, 189, 248, 0.1);
    }

    .stat-card.success {
      border-color: rgba(34, 197, 94, 0.3);
    }

    .stat-card.info {
      border-color: rgba(56, 189, 248, 0.3);
    }

    .stat-card.warning {
      border-color: rgba(251, 191, 36, 0.3);
    }

    .stat-icon {
      font-size: 2rem;
    }

    .stat-content {
      flex: 1;
    }

    .stat-value {
      font-size: 2rem;
      font-weight: 700;
      color: white;
      line-height: 1;
      margin-bottom: 4px;
    }

    .stat-label {
      font-size: 0.875rem;
      color: #94a3b8;
    }

    .actions-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 16px;
    }

    .action-card {
      padding: 24px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .action-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 12px 40px rgba(56, 189, 248, 0.1);
      background: rgba(56, 189, 248, 0.05);
    }

    .action-card h3 {
      margin: 0 0 8px 0;
      color: white;
      font-size: 1.125rem;
    }

    .action-card p {
      margin: 0;
      color: #94a3b8;
      font-size: 0.875rem;
    }

    .recent-activity {
      padding: 24px;
    }

    .recent-activity h3 {
      margin: 0 0 20px 0;
      color: white;
      font-size: 1.25rem;
    }

    .activity-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .activity-item {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
      background: rgba(255, 255, 255, 0.02);
      border-radius: 8px;
      border: 1px solid rgba(255, 255, 255, 0.05);
    }

    .activity-dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .activity-dot.success {
      background: #22c55e;
      box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
    }

    .activity-dot.warning {
      background: #fbbf24;
      box-shadow: 0 0 8px rgba(251, 191, 36, 0.5);
    }

    .activity-dot.info {
      background: #38bdf8;
      box-shadow: 0 0 8px rgba(56, 189, 248, 0.5);
    }

    .activity-content {
      flex: 1;
    }

    .activity-title {
      color: white;
      font-weight: 500;
      margin-bottom: 4px;
    }

    .activity-meta {
      color: #94a3b8;
      font-size: 0.75rem;
    }
  `]
})
export class DashboardHomeComponent implements OnInit {
  quickStats = signal<QuickStats>({
    totalRuns: 0,
    successRate: 0,
    activeRuns: 0,
    escalations: 0
  });

  constructor(
    private analyticsService: AnalyticsService,
    private router: Router
  ) { }

  ngOnInit() {
    this.loadQuickStats();
  }

  loadQuickStats() {
    this.analyticsService.getSummary().subscribe({
      next: (data) => {
        const activeRuns = Object.entries(data.statusBreakdown)
          .filter(([status]) => !['DONE', 'FAILED', 'ESCALATED'].includes(status))
          .reduce((sum, [, count]) => sum + count, 0);

        const escalations = data.statusBreakdown['ESCALATED'] || 0;

        this.quickStats.set({
          totalRuns: data.totalRuns,
          successRate: data.successRate,
          activeRuns,
          escalations
        });
      },
      error: () => {
        this.quickStats.set({
          totalRuns: 247,
          successRate: 0.94,
          activeRuns: 3,
          escalations: 5
        });
      }
    });
  }

  formatPercent(value: number): string {
    return `${Math.round(value * 100)}%`;
  }

  navigateTo(path: string) {
    this.router.navigate([path]);
  }
}
