import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AnalyticsService } from '../services/analytics.service';

interface Run {
  id: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  currentAgent: string;
  ciFixCount: number;
  e2eFixCount: number;
  artifacts: Artifact[];
  repo?: string;
  issueNumber?: number;
}

interface Artifact {
  id: string;
  agentName: string;
  artifactType: string;
  createdAt: string;
}

@Component({
  selector: 'app-run-detail',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="run-detail-container" *ngIf="run()">
      <div class="header">
        <button (click)="goBack()" class="btn-back glass-panel">
          ← Back to Runs
        </button>
        <h2>Run Details</h2>
      </div>

      <div class="detail-grid">
        <div class="info-card glass-panel">
          <h3>Run Information</h3>
          <div class="info-row">
            <span class="label">ID:</span>
            <span class="value">{{ run()?.id }}</span>
          </div>
          <div class="info-row">
            <span class="label">Status:</span>
            <span class="status-badge" [ngClass]="getStatusClass(run()?.status)">
              {{ run()?.status }}
            </span>
          </div>
          <div class="info-row">
            <span class="label">Repository:</span>
            <span class="value">{{ run()?.repo || 'N/A' }}</span>
          </div>
          <div class="info-row" *ngIf="run()?.issueNumber">
            <span class="label">Issue:</span>
            <span class="value">#{{ run()?.issueNumber }}</span>
          </div>
          <div class="info-row">
            <span class="label">Current Agent:</span>
            <span class="value">{{ run()?.currentAgent || 'N/A' }}</span>
          </div>
          <div class="info-row">
            <span class="label">Created:</span>
            <span class="value">{{ formatDate(run()?.createdAt) }}</span>
          </div>
          <div class="info-row">
            <span class="label">Updated:</span>
            <span class="value">{{ formatDate(run()?.updatedAt) }}</span>
          </div>
          <div class="info-row">
            <span class="label">CI Fixes:</span>
            <span class="value">{{ run()?.ciFixCount || 0 }}</span>
          </div>
          <div class="info-row">
            <span class="label">E2E Fixes:</span>
            <span class="value">{{ run()?.e2eFixCount || 0 }}</span>
          </div>
        </div>

        <div class="artifacts-card glass-panel">
          <h3>Artifacts ({{ run()?.artifacts?.length || 0 }})</h3>
          <div class="artifacts-list">
            <div *ngFor="let artifact of run()?.artifacts" class="artifact-item">
              <div class="artifact-header">
                <span class="artifact-type">{{ artifact.artifactType }}</span>
                <span class="artifact-agent">{{ artifact.agentName }}</span>
              </div>
              <div class="artifact-meta">
                <span class="artifact-id">{{ artifact.id.substring(0, 8) }}</span>
                <span class="artifact-date">{{ formatDate(artifact.createdAt) }}</span>
              </div>
            </div>
            <div *ngIf="!run()?.artifacts || run()?.artifacts?.length === 0" class="empty-state">
              No artifacts yet
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="loading-state" *ngIf="!run() && !error()">
      <div class="spinner"></div>
      <p>Loading run details...</p>
    </div>

    <div class="error-state" *ngIf="error()">
      <p>{{ error() }}</p>
      <button (click)="goBack()" class="btn-back glass-panel">
        Back to Runs
      </button>
    </div>
  `,
  styles: [`
    .run-detail-container {
      padding: 24px;
      height: 100%;
      overflow-y: auto;
    }

    .header {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 24px;
    }

    .header h2 {
      margin: 0;
      color: white;
    }

    .btn-back {
      padding: 8px 16px;
      border: none;
      border-radius: 6px;
      background: rgba(255, 255, 255, 0.05);
      color: white;
      cursor: pointer;
      transition: background 0.2s;
    }

    .btn-back:hover {
      background: rgba(56, 189, 248, 0.2);
    }

    .detail-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
    }

    .info-card, .artifacts-card {
      padding: 24px;
      border-radius: 12px;
    }

    .info-card h3, .artifacts-card h3 {
      margin: 0 0 20px 0;
      color: white;
      font-size: 1.125rem;
    }

    .info-row {
      display: flex;
      justify-content: space-between;
      padding: 12px 0;
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    }

    .info-row:last-child {
      border-bottom: none;
    }

    .label {
      color: #94a3b8;
      font-weight: 500;
    }

    .value {
      color: white;
      font-weight: 600;
    }

    .status-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .status-badge.done {
      background: rgba(34, 197, 94, 0.2);
      color: #22c55e;
    }

    .status-badge.failed {
      background: rgba(239, 68, 68, 0.2);
      color: #ef4444;
    }

    .status-badge.escalated {
      background: rgba(251, 191, 36, 0.2);
      color: #fbbf24;
    }

    .status-badge.in-progress {
      background: rgba(56, 189, 248, 0.2);
      color: #38bdf8;
    }

    .artifacts-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
      max-height: 500px;
      overflow-y: auto;
    }

    .artifact-item {
      padding: 12px;
      background: rgba(255, 255, 255, 0.02);
      border: 1px solid rgba(255, 255, 255, 0.05);
      border-radius: 8px;
    }

    .artifact-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    .artifact-type {
      color: white;
      font-weight: 600;
      font-size: 0.875rem;
    }

    .artifact-agent {
      padding: 2px 8px;
      background: rgba(139, 92, 246, 0.2);
      color: #8b5cf6;
      border-radius: 8px;
      font-size: 0.75rem;
    }

    .artifact-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.75rem;
      color: #94a3b8;
    }

    .loading-state, .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: #94a3b8;
    }

    .spinner {
      width: 48px;
      height: 48px;
      border: 4px solid rgba(56, 189, 248, 0.2);
      border-top-color: #38bdf8;
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .empty-state {
      text-align: center;
      padding: 24px;
      color: #94a3b8;
    }

    @media (max-width: 768px) {
      .detail-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class RunDetailComponent implements OnInit {
  run = signal<Run | null>(null);
  error = signal<string | null>(null);

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private analyticsService: AnalyticsService
  ) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadRun(id);
    } else {
      this.error.set('No run ID provided');
    }
  }

  loadRun(id: string) {
    this.analyticsService.getRun(id).subscribe({
      next: (run) => this.run.set(run as Run),
      error: (err) => {
        console.error('Failed to load run', err);
        this.error.set('Failed to load run details');
      }
    });
  }

  getStatusClass(status: string | undefined): string {
    if (!status) return '';
    switch (status) {
      case 'DONE':
        return 'done';
      case 'FAILED':
        return 'failed';
      case 'ESCALATED':
        return 'escalated';
      default:
        return 'in-progress';
    }
  }

  formatDate(dateStr: string | undefined): string {
    if (!dateStr) return 'N/A';
    const date = new Date(dateStr);
    return date.toLocaleString();
  }

  goBack() {
    this.router.navigate(['/runs']);
  }
}
