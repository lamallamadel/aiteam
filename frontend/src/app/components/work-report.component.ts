import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { ArtifactResponse } from '../models/orchestrator.model';
import { ArtifactRendererComponent } from './artifact-renderer.component';

@Component({
  selector: 'app-work-report',
  standalone: true,
  imports: [CommonModule, RouterModule, ArtifactRendererComponent],
  template: `
    <div class="report-container">
      <div class="header">
        <a [routerLink]="['/runs', runId()]" class="back-link">← Back to Bolt</a>
        <h2>Work Report</h2>
      </div>

      @if (loading()) {
        <div class="loading glass-panel">Loading report...</div>
      }

      @if (!loading() && artifacts().length === 0) {
        <div class="empty glass-panel">No artifacts produced yet.</div>
      }

      @for (artifact of artifacts(); track artifact.id) {
        <div class="artifact-card glass-panel">
          <div class="artifact-header">
            <span class="agent-badge">{{ artifact.agentName }}</span>
            <span class="timestamp">{{ formatDate(artifact.createdAt) }}</span>
          </div>
          <app-artifact-renderer [artifact]="artifact" />
        </div>
      }
    </div>
  `,
  styles: [`
    .report-container {
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
    .loading, .empty { padding: 24px; text-align: center; color: rgba(255,255,255,0.5); }
    .artifact-card { padding: 16px; }
    .artifact-header {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 12px;
    }
    .agent-badge {
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .timestamp { margin-left: auto; color: rgba(255,255,255,0.4); font-size: 0.8rem; }
  `]
})
export class WorkReportComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private orchestratorService = inject(OrchestratorService);

  runId = signal('');
  artifacts = signal<ArtifactResponse[]>([]);
  loading = signal(true);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.runId.set(id);

    this.orchestratorService.getRunArtifacts(id).subscribe({
      next: (artifacts) => {
        this.artifacts.set(artifacts);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  formatDate(date: string): string {
    try {
      return new Date(date).toLocaleString();
    } catch {
      return date;
    }
  }
}
