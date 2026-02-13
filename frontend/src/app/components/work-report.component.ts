import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { ArtifactResponse } from '../models/orchestrator.model';

@Component({
  selector: 'app-work-report',
  standalone: true,
  imports: [CommonModule, RouterModule],
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
            <span class="type-badge" [class]="getTypeClass(artifact.artifactType)">
              {{ artifact.artifactType }}
            </span>
            <span class="timestamp">{{ formatDate(artifact.createdAt) }}</span>
          </div>
          <div class="artifact-body">
            @if (isJson(artifact.payload)) {
              <pre class="json-view">{{ formatJson(artifact.payload) }}</pre>
            } @else {
              <pre class="text-view">{{ artifact.payload }}</pre>
            }
          </div>
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
    .type-badge {
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .type-badge.json { background: rgba(129,140,248,0.15); color: #818cf8; }
    .type-badge.md { background: rgba(34,197,94,0.15); color: #22c55e; }
    .type-badge.url { background: rgba(234,179,8,0.15); color: #eab308; }
    .type-badge.default { background: rgba(255,255,255,0.08); color: rgba(255,255,255,0.6); }
    .timestamp { margin-left: auto; color: rgba(255,255,255,0.4); font-size: 0.8rem; }
    .artifact-body {
      max-height: 400px;
      overflow: auto;
    }
    pre {
      margin: 0;
      white-space: pre-wrap;
      word-break: break-word;
      font-size: 0.8rem;
      line-height: 1.5;
      color: rgba(255,255,255,0.7);
    }
    .json-view {
      background: rgba(0,0,0,0.2);
      padding: 12px;
      border-radius: 8px;
    }
    .text-view {
      background: rgba(0,0,0,0.2);
      padding: 12px;
      border-radius: 8px;
    }
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

  isJson(payload: string): boolean {
    try {
      JSON.parse(payload);
      return true;
    } catch {
      return false;
    }
  }

  formatJson(payload: string): string {
    try {
      return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
      return payload;
    }
  }

  getTypeClass(type: string): string {
    if (type.endsWith('.json')) return 'json';
    if (type.endsWith('.md') || type === 'docs_update') return 'md';
    if (type === 'pr_url') return 'url';
    return 'default';
  }

  formatDate(date: string): string {
    try {
      return new Date(date).toLocaleString();
    } catch {
      return date;
    }
  }
}
