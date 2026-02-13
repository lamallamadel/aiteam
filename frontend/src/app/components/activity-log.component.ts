import { Component, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { WorkflowStreamStore } from '../services/workflow-stream.store';
import { ArtifactResponse, WorkflowEvent } from '../models/orchestrator.model';

interface LogEntry {
  timestamp: string;
  agentName: string;
  action: string;
  detail: string;
  type: 'step' | 'llm' | 'schema' | 'error' | 'escalation';
  durationMs?: number;
  tokensUsed?: number;
}

@Component({
  selector: 'app-activity-log',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="activity-log-container">
      <div class="header">
        <a [routerLink]="['/runs', runId()]" class="back-link">← Back to Bolt</a>
        <h2>Activity Log</h2>
        @if (streamStore.isStreaming()) {
          <span class="live-badge">LIVE</span>
        }
      </div>

      @if (entries().length === 0) {
        <div class="empty-state glass-panel">
          <p>No activity recorded yet.</p>
        </div>
      }

      <div class="log-list">
        @for (entry of entries(); track $index) {
          <div class="log-entry glass-panel" [class]="'log-' + entry.type">
            <div class="log-time">{{ formatTime(entry.timestamp) }}</div>
            <div class="log-agent">
              <span class="agent-badge">{{ entry.agentName }}</span>
            </div>
            <div class="log-content">
              <div class="log-action">{{ entry.action }}</div>
              <div class="log-detail">{{ entry.detail }}</div>
            </div>
            <div class="log-metrics">
              @if (entry.durationMs) {
                <span class="metric">{{ entry.durationMs }}ms</span>
              }
              @if (entry.tokensUsed) {
                <span class="metric tokens">{{ entry.tokensUsed }} tokens</span>
              }
            </div>
          </div>
        }
      </div>

      @if (streamStore.isStreaming()) {
        <div class="token-summary glass-panel">
          <span>Total tokens consumed: <strong>{{ streamStore.tokenConsumption() }}</strong></span>
          <span>Steps completed: <strong>{{ streamStore.completedSteps() }} / 7</strong></span>
        </div>
      }
    </div>
  `,
  styles: [`
    .activity-log-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      height: 100%;
      overflow-y: auto;
    }
    .header { display: flex; align-items: center; gap: 16px; }
    .header h2 { flex: 1; }
    .back-link {
      color: #38bdf8;
      text-decoration: none;
      font-size: 0.9rem;
    }
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
    .log-list { display: flex; flex-direction: column; gap: 6px; }
    .log-entry {
      display: grid;
      grid-template-columns: 90px 100px 1fr auto;
      gap: 12px;
      padding: 10px 16px;
      align-items: center;
      font-size: 0.85rem;
    }
    .log-time { color: rgba(255,255,255,0.4); font-family: monospace; font-size: 0.8rem; }
    .agent-badge {
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      padding: 2px 8px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
    }
    .log-action { font-weight: 500; }
    .log-detail { color: rgba(255,255,255,0.5); font-size: 0.8rem; }
    .log-metrics { display: flex; gap: 8px; }
    .metric {
      background: rgba(255,255,255,0.05);
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 0.75rem;
      font-family: monospace;
      color: rgba(255,255,255,0.5);
    }
    .metric.tokens { color: #818cf8; }
    .log-error { border-left: 3px solid #ef4444; }
    .log-escalation { border-left: 3px solid #eab308; }
    .log-step { border-left: 3px solid #38bdf8; }
    .log-llm { border-left: 3px solid #818cf8; }
    .log-schema { border-left: 3px solid #22c55e; }
    .empty-state { padding: 24px; text-align: center; color: rgba(255,255,255,0.5); }
    .token-summary {
      display: flex;
      justify-content: space-around;
      padding: 12px;
      font-size: 0.85rem;
      color: rgba(255,255,255,0.6);
    }
    .token-summary strong { color: white; }
  `]
})
export class ActivityLogComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  streamStore = inject(WorkflowStreamStore);
  private orchestratorService = inject(OrchestratorService);

  runId = signal<string>('');
  entries = signal<LogEntry[]>([]);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id') || '';
    this.runId.set(id);

    // Load historical artifacts
    this.orchestratorService.getRunArtifacts(id).subscribe({
      next: (artifacts) => {
        const historical = artifacts.map(a => this.artifactToLogEntry(a));
        this.entries.set(historical);
      }
    });

    // Connect SSE for live updates
    this.streamStore.connectToRun(id);
  }

  ngOnDestroy() {
    this.streamStore.disconnect();
  }

  private artifactToLogEntry(artifact: ArtifactResponse): LogEntry {
    return {
      timestamp: artifact.createdAt,
      agentName: artifact.agentName,
      action: 'Artifact produced',
      detail: artifact.artifactType,
      type: 'step'
    };
  }

  formatTime(timestamp: string): string {
    try {
      const d = new Date(timestamp);
      return d.toLocaleTimeString('en-US', { hour12: false });
    } catch {
      return timestamp;
    }
  }
}
