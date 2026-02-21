import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { AgentCard, AgentBinding } from '../models/orchestrator.model';

@Component({
  selector: 'app-a2a-registry',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="registry-container">
      <div class="header">
        <div class="header-title">
          <h2>A2A Agent Registry</h2>
          <p class="subtitle">Registered agents, capabilities, and active bindings</p>
        </div>
        <div class="header-meta" *ngIf="orchestratorCard()">
          <span class="orch-badge">
            <span class="dot"></span>
            {{ orchestratorCard()?.name }} v{{ orchestratorCard()?.version }}
          </span>
        </div>
        <button class="btn-refresh glass-panel" (click)="refresh()" [disabled]="loading()">
          {{ loading() ? 'Loading…' : '↻ Refresh' }}
        </button>
      </div>

      <!-- Active bindings (live during a run) -->
      <div class="bindings-section" *ngIf="hasBindings()">
        <h3 class="section-title">
          <span class="live-dot"></span>
          Active Bindings ({{ bindingList().length }})
        </h3>
        <div class="bindings-grid">
          <div class="binding-card glass-panel" *ngFor="let b of bindingList()">
            <div class="binding-header">
              <span class="role-badge">{{ b.role }}</span>
              <span class="agent-name">{{ b.agentName }}</span>
              <span class="validity-dot" [class.valid]="b['__valid']" [title]="b['__valid'] ? 'Signature valid' : 'Invalid/expired'">
                {{ b['__valid'] ? '✓' : '✗' }}
              </span>
            </div>
            <div class="binding-caps">
              <span class="cap-pill req" *ngFor="let cap of b.requiredCapabilities">{{ cap }}</span>
            </div>
            <div class="binding-meta">
              <span>Issued: {{ formatTime(b.issuedAt) }}</span>
              <span>Expires: {{ formatTime(b.expiresAt) }}</span>
            </div>
            <div class="binding-sig">
              <span class="sig-label">HMAC-SHA256</span>
              <span class="sig-value mono">{{ b.signature.substring(0, 16) }}…</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Registered agents grid -->
      <div class="agents-section">
        <h3 class="section-title">Registered Agents ({{ agents().length }})</h3>

        <div class="agents-grid">
          <div class="agent-card glass-panel" *ngFor="let agent of agents()"
               [class.agent-active]="isAgentBound(agent.name)">
            <div class="agent-header">
              <div class="agent-name-row">
                <span class="agent-name">{{ agent.name }}</span>
                <span class="agent-version">v{{ agent.version }}</span>
              </div>
              <div class="agent-badges">
                <span class="transport-badge" [class.http]="agent.transport === 'http'">
                  {{ agent.transport }}
                </span>
                <span class="status-dot" [class.active]="agent.status === 'active'"></span>
              </div>
            </div>

            <div class="agent-role">
              <span class="role-label">{{ agent.role }}</span>
              <span class="vendor-label">{{ agent.vendor }}</span>
            </div>

            <p class="agent-description">{{ agent.description }}</p>

            <div class="agent-caps">
              <span class="cap-pill" *ngFor="let cap of agent.capabilities">{{ cap }}</span>
            </div>

            <div class="agent-constraints" *ngIf="agent.constraints">
              <span class="constraint-item">
                <span class="constraint-label">Tokens</span>
                {{ agent.constraints.maxTokens | number }}
              </span>
              <span class="constraint-item">
                <span class="constraint-label">Timeout</span>
                {{ formatDuration(agent.constraints.maxDurationMs) }}
              </span>
              <span class="constraint-item">
                <span class="constraint-label">Budget</span>
                \${{ agent.constraints.costBudgetUsd.toFixed(2) }}
              </span>
            </div>

            <!-- Bound indicator -->
            <div class="bound-indicator" *ngIf="isAgentBound(agent.name)">
              <span class="pulse-dot"></span>
              Currently executing
            </div>
          </div>
        </div>
      </div>

      <!-- Orchestrator card (self-description) -->
      <div class="orch-section glass-panel" *ngIf="orchestratorCard()">
        <h3 class="section-title inline">Orchestrator Self-Description
          <span class="section-meta">/.well-known/agent.json</span>
        </h3>
        <div class="orch-content">
          <div class="orch-meta">
            <div class="info-row">
              <span class="label">Name</span>
              <span class="value">{{ orchestratorCard()?.name }}</span>
            </div>
            <div class="info-row">
              <span class="label">Transport</span>
              <span class="value">{{ orchestratorCard()?.transport }}</span>
            </div>
            <div class="info-row">
              <span class="label">Health</span>
              <span class="value mono">{{ orchestratorCard()?.healthEndpoint }}</span>
            </div>
            <div class="info-row">
              <span class="label">Max Tokens</span>
              <span class="value">{{ orchestratorCard()?.constraints?.maxTokens | number }}</span>
            </div>
          </div>
          <div class="orch-caps">
            <p class="orch-desc">{{ orchestratorCard()?.description }}</p>
            <div class="agent-caps">
              <span class="cap-pill" *ngFor="let cap of orchestratorCard()?.capabilities">{{ cap }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="empty-state" *ngIf="!loading() && agents().length === 0">
        <p>No agents registered. The registry pre-populates on orchestrator startup.</p>
      </div>
    </div>
  `,
  styles: [`
    .registry-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
      height: 100%;
      overflow-y: auto;
    }
    .header {
      display: flex;
      align-items: flex-start;
      gap: 16px;
      flex-wrap: wrap;
    }
    .header-title { flex: 1; }
    .header-title h2 { margin: 0 0 4px; font-size: 1.6rem; }
    .subtitle { color: rgba(255,255,255,0.45); font-size: 0.88rem; margin: 0; }
    .orch-badge {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      background: rgba(56,189,248,0.1);
      border: 1px solid rgba(56,189,248,0.2);
      border-radius: 20px;
      font-size: 0.8rem;
      color: #38bdf8;
      font-weight: 600;
      white-space: nowrap;
    }
    .orch-badge .dot {
      width: 6px; height: 6px;
      background: #22c55e;
      border-radius: 50%;
      animation: blink 1s infinite;
    }
    @keyframes blink { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }
    .btn-refresh {
      padding: 8px 16px;
      border: none;
      border-radius: 6px;
      background: rgba(255,255,255,0.06);
      color: white;
      cursor: pointer;
      font-size: 0.85rem;
      transition: background 0.2s;
    }
    .btn-refresh:hover:not(:disabled) { background: rgba(56,189,248,0.2); }
    .btn-refresh:disabled { opacity: 0.5; cursor: not-allowed; }

    /* Section titles */
    .section-title {
      margin: 0 0 12px;
      font-size: 0.85rem;
      font-weight: 700;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .section-title.inline { margin-bottom: 16px; }
    .section-meta { font-size: 0.72rem; color: #38bdf8; font-family: monospace; font-weight: 400; }
    .live-dot {
      width: 8px; height: 8px;
      background: #22c55e;
      border-radius: 50%;
      animation: blink 1s infinite;
    }

    /* Bindings */
    .bindings-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 12px;
    }
    .binding-card { padding: 14px; border-radius: 10px; }
    .binding-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;
    }
    .role-badge {
      padding: 2px 8px;
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 700;
    }
    .validity-dot {
      margin-left: auto;
      width: 20px; height: 20px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.7rem;
      font-weight: 700;
      background: rgba(239,68,68,0.2);
      color: #ef4444;
    }
    .validity-dot.valid { background: rgba(34,197,94,0.2); color: #22c55e; }
    .binding-caps { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; }
    .binding-meta {
      display: flex;
      justify-content: space-between;
      font-size: 0.72rem;
      color: rgba(255,255,255,0.35);
      margin-bottom: 8px;
    }
    .binding-sig {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 8px;
      background: rgba(0,0,0,0.2);
      border-radius: 6px;
    }
    .sig-label {
      font-size: 0.65rem;
      color: rgba(255,255,255,0.3);
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
    .sig-value { font-size: 0.72rem; color: #94a3b8; }

    /* Agent cards */
    .agents-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 14px;
    }
    .agent-card {
      padding: 16px;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      border: 1px solid transparent;
      transition: border-color 0.3s;
    }
    .agent-card.agent-active {
      border-color: rgba(34,197,94,0.4);
      box-shadow: 0 0 16px rgba(34,197,94,0.08);
    }
    .agent-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
    }
    .agent-name-row { display: flex; align-items: baseline; gap: 6px; }
    .agent-name { font-weight: 700; color: white; font-size: 0.95rem; }
    .agent-version { font-size: 0.72rem; color: rgba(255,255,255,0.35); font-family: monospace; }
    .agent-badges { display: flex; align-items: center; gap: 6px; }
    .transport-badge {
      padding: 2px 6px;
      background: rgba(255,255,255,0.06);
      color: rgba(255,255,255,0.4);
      border-radius: 4px;
      font-size: 0.68rem;
      font-family: monospace;
    }
    .transport-badge.http { background: rgba(139,92,246,0.2); color: #8b5cf6; }
    .status-dot {
      width: 8px; height: 8px;
      border-radius: 50%;
      background: rgba(255,255,255,0.2);
    }
    .status-dot.active { background: #22c55e; animation: blink 2s infinite; }
    .agent-role {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .role-label {
      font-size: 0.75rem;
      font-weight: 700;
      color: #38bdf8;
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
    .vendor-label {
      font-size: 0.72rem;
      color: rgba(255,255,255,0.3);
    }
    .agent-description {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.5);
      margin: 0;
      line-height: 1.4;
    }
    .agent-caps { display: flex; flex-wrap: wrap; gap: 4px; }
    .cap-pill {
      padding: 2px 8px;
      background: rgba(56,189,248,0.1);
      color: #64748b;
      border-radius: 10px;
      font-size: 0.68rem;
      font-weight: 500;
    }
    .cap-pill.req {
      background: rgba(34,197,94,0.12);
      color: #4ade80;
    }
    .agent-constraints {
      display: flex;
      gap: 12px;
      padding-top: 8px;
      border-top: 1px solid rgba(255,255,255,0.05);
    }
    .constraint-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
      font-size: 0.8rem;
      color: white;
      font-family: monospace;
    }
    .constraint-label {
      font-size: 0.65rem;
      color: rgba(255,255,255,0.3);
      font-family: sans-serif;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .bound-indicator {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75rem;
      font-weight: 600;
      color: #22c55e;
    }
    .pulse-dot {
      width: 6px; height: 6px;
      background: #22c55e;
      border-radius: 50%;
      animation: blink 0.8s infinite;
    }

    /* Orchestrator card */
    .orch-section { padding: 20px; border-radius: 12px; }
    .orch-content { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
    .orch-desc { font-size: 0.85rem; color: rgba(255,255,255,0.5); margin: 0 0 12px; line-height: 1.5; }
    .info-row {
      display: flex;
      justify-content: space-between;
      padding: 8px 0;
      border-bottom: 1px solid rgba(255,255,255,0.05);
      font-size: 0.85rem;
    }
    .label { color: #94a3b8; }
    .value { color: white; font-weight: 500; }
    .mono { font-family: monospace; }

    .empty-state { text-align: center; padding: 48px; color: rgba(255,255,255,0.4); }

    @media (max-width: 768px) {
      .agents-grid, .bindings-grid { grid-template-columns: 1fr; }
      .orch-content { grid-template-columns: 1fr; }
    }
  `]
})
export class A2ARegistryComponent implements OnInit, OnDestroy {
  agents = signal<AgentCard[]>([]);
  bindings = signal<Record<string, AgentBinding & { __valid?: boolean }>>({});
  orchestratorCard = signal<AgentCard | null>(null);
  loading = signal(true);

  private pollInterval: ReturnType<typeof setInterval> | null = null;

  constructor(private orchestratorService: OrchestratorService) {}

  ngOnInit() {
    this.loadAll();
    // Poll bindings every 3s to reflect live changes during an active run
    this.pollInterval = setInterval(() => this.loadBindings(), 3000);
  }

  ngOnDestroy() {
    if (this.pollInterval) clearInterval(this.pollInterval);
  }

  refresh() {
    this.loading.set(true);
    this.loadAll();
  }

  private loadAll() {
    this.orchestratorService.listAgents().subscribe({
      next: (cards) => {
        this.agents.set(cards);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });

    this.orchestratorService.getOrchestratorCard().subscribe({
      next: (card) => this.orchestratorCard.set(card),
      error: () => {}
    });

    this.loadBindings();
  }

  private loadBindings() {
    this.orchestratorService.getAgentBindings().subscribe({
      next: (bindingMap) => {
        // Verify each binding
        const ids = Object.keys(bindingMap);
        if (ids.length === 0) { this.bindings.set({}); return; }

        const enriched: Record<string, AgentBinding & { __valid?: boolean }> = {};
        let remaining = ids.length;

        ids.forEach(id => {
          this.orchestratorService.verifyBinding(id).subscribe({
            next: (result) => {
              enriched[id] = { ...bindingMap[id], __valid: result.valid };
              remaining--;
              if (remaining === 0) this.bindings.set({ ...enriched });
            },
            error: () => {
              enriched[id] = { ...bindingMap[id], __valid: false };
              remaining--;
              if (remaining === 0) this.bindings.set({ ...enriched });
            }
          });
        });
      },
      error: () => {}
    });
  }

  hasBindings(): boolean {
    return Object.keys(this.bindings()).length > 0;
  }

  bindingList(): (AgentBinding & { __valid?: boolean })[] {
    return Object.values(this.bindings());
  }

  isAgentBound(agentName: string): boolean {
    return Object.values(this.bindings()).some(b => b.agentName === agentName);
  }

  formatTime(iso: string): string {
    return new Date(iso).toLocaleTimeString();
  }

  formatDuration(ms: number): string {
    if (ms < 60000) return `${ms / 1000}s`;
    return `${(ms / 60000).toFixed(1)}m`;
  }
}
