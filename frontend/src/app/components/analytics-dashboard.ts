import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-analytics-dashboard',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="analytics-wrapper glass-panel">
      <h3>Agent Performance</h3>
      <div class="chart-placeholder">
        <div class="bar-chart">
          <div class="bar" style="height: 80%" title="PmStep"></div>
          <div class="bar" style="height: 60%" title="QualifierStep"></div>
          <div class="bar" style="height: 95%" title="DeveloperStep"></div>
          <div class="bar" style="height: 40%" title="TesterStep"></div>
        </div>
        <div class="labels">
            <span>PM</span><span>QUAL</span><span>DEV</span><span>TEST</span>
        </div>
      </div>
      <div class="findings-list">
        <h4>Recent Escalations</h4>
        <div class="finding-item">
            <span class="severity critical">CRITICAL</span>
            <span>Timeout in E2E tests for repo: Atlasia-core</span>
        </div>
      </div>
    </div>
  `,
    styles: [`
    .analytics-wrapper { padding: 20px; margin-top: 20px; }
    .chart-placeholder { height: 200px; display: flex; flex-direction: column; justify-content: flex-end; gap: 10px; padding: 20px 0; }
    .bar-chart { display: flex; align-items: flex-end; gap: 20px; height: 100%; border-bottom: 1px solid rgba(255,255,255,0.1); }
    .bar { flex: 1; background: linear-gradient(to top, #38bdf8, #818cf8); border-radius: 4px 4px 0 0; position: relative; }
    .labels { display: flex; justify-content: space-around; font-size: 0.7rem; color: #94a3b8; }
    .findings-list { margin-top: 20px; }
    .severity { padding: 2px 6px; border-radius: 4px; font-size: 0.7rem; margin-right: 10px; }
    .severity.critical { background: #ef4444; color: white; }
    .finding-item { padding: 8px 0; border-bottom: 1px solid rgba(255,255,255,0.05); font-size: 0.9rem; }
  `]
})
export class AnalyticsDashboardComponent { }
