import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OrchestratorService } from '../../services/orchestrator.service';
import { RunResponse } from '../../models/orchestrator.model';

@Component({
  selector: 'app-escalation-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="oversight-container">
      <div class="header">
        <h2>Oversight Panel</h2>
        <p class="subtitle">Bolts requiring human intervention</p>
      </div>

      @if (loading()) {
        <div class="loading-state glass-panel">Loading escalated bolts...</div>
      }

      @if (!loading() && escalatedRuns().length === 0) {
        <div class="empty-state glass-panel">
          <div class="empty-icon">✓</div>
          <h3>All clear</h3>
          <p>No bolts require human intervention right now.</p>
        </div>
      }

      @for (run of escalatedRuns(); track run.id) {
        <div class="escalation-card glass-panel">
          <div class="escalation-header">
            <div class="run-info">
              <span class="status-badge escalated">ESCALATED</span>
              <span class="run-id">{{ run.repo }} #{{ run.issueNumber }}</span>
            </div>
            <span class="agent-badge">{{ run.currentAgent }}</span>
          </div>

          <div class="escalation-body">
            <p class="escalation-reason">Agent <strong>{{ run.currentAgent }}</strong> requires a decision to proceed.</p>

            <div class="guidance-section">
              <label>Guidance (optional)</label>
              <textarea
                class="guidance-input glass-panel"
                [(ngModel)]="guidance[run.id]"
                placeholder="Provide context or instructions for the agent..."
                rows="3"
              ></textarea>
            </div>

            <div class="action-buttons">
              <button class="btn btn-proceed" (click)="resolveEscalation(run.id, 'PROCEED')">
                Proceed with Guidance
              </button>
              <button class="btn btn-abort" (click)="resolveEscalation(run.id, 'ABORT')">
                Abort Bolt
              </button>
              <button class="btn btn-detail" (click)="viewDetail(run.id)">
                View Details
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .oversight-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      height: 100%;
      overflow-y: auto;
    }
    .header h2 {
      font-size: 1.8rem;
      margin-bottom: 4px;
    }
    .subtitle { color: rgba(255,255,255,0.5); font-size: 0.9rem; }
    .empty-state {
      text-align: center;
      padding: 48px 24px;
    }
    .empty-icon { font-size: 3rem; margin-bottom: 12px; }
    .empty-state h3 { margin-bottom: 8px; }
    .empty-state p { color: rgba(255,255,255,0.5); }
    .loading-state { padding: 24px; text-align: center; color: rgba(255,255,255,0.5); }
    .escalation-card { padding: 20px; }
    .escalation-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }
    .run-info { display: flex; align-items: center; gap: 12px; }
    .status-badge {
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
    }
    .status-badge.escalated { background: rgba(234, 179, 8, 0.2); color: #eab308; }
    .run-id { font-weight: 500; }
    .agent-badge {
      background: rgba(56, 189, 248, 0.15);
      color: #38bdf8;
      padding: 4px 10px;
      border-radius: 8px;
      font-size: 0.8rem;
      font-weight: 600;
    }
    .escalation-reason { margin-bottom: 16px; color: rgba(255,255,255,0.7); }
    .guidance-section { margin-bottom: 16px; }
    .guidance-section label {
      display: block;
      margin-bottom: 6px;
      font-size: 0.85rem;
      color: rgba(255,255,255,0.5);
    }
    .guidance-input {
      width: 100%;
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 8px;
      padding: 10px;
      color: white;
      font-size: 0.9rem;
      resize: vertical;
    }
    .action-buttons { display: flex; gap: 10px; }
    .btn {
      padding: 8px 16px;
      border: none;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.85rem;
      transition: opacity 0.2s;
    }
    .btn:hover { opacity: 0.85; }
    .btn-proceed { background: #22c55e; color: white; }
    .btn-abort { background: #ef4444; color: white; }
    .btn-detail {
      background: rgba(255,255,255,0.08);
      color: rgba(255,255,255,0.7);
      border: 1px solid rgba(255,255,255,0.1);
    }
  `]
})
export class EscalationPanelComponent implements OnInit {
  escalatedRuns = signal<RunResponse[]>([]);
  loading = signal(true);
  guidance: Record<string, string> = {};

  constructor(
    private orchestratorService: OrchestratorService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadEscalatedRuns();
  }

  loadEscalatedRuns() {
    this.loading.set(true);
    this.orchestratorService.getRuns().subscribe({
      next: (runs) => {
        this.escalatedRuns.set(runs.filter(r => r.status === 'ESCALATED'));
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  resolveEscalation(runId: string, decision: string) {
    this.orchestratorService.resolveEscalation(runId, decision, this.guidance[runId]).subscribe({
      next: () => this.loadEscalatedRuns(),
      error: (err) => console.error('Failed to resolve escalation:', err)
    });
  }

  viewDetail(runId: string) {
    this.router.navigate(['/runs', runId]);
  }
}
