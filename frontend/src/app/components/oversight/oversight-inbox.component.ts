import { Component, OnInit, OnDestroy, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OversightInboxService } from '../../services/oversight-inbox.service';
import { PendingInterrupt } from '../../models/orchestrator.model';

type InterruptTier = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

@Component({
  selector: 'app-oversight-inbox',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="inbox-container">
      <div class="header">
        <div class="header-row">
          <h2>Approval Inbox</h2>
          <span *ngIf="liveIndicator()" class="live-indicator" [class.pulse]="isLive()">
            <span class="dot"></span>
            LIVE
          </span>
        </div>
        <p class="subtitle">Pending interrupts requiring human approval</p>
        
        <div class="filter-bar">
          <div class="filter-group">
            <label>Tier</label>
            <select [(ngModel)]="tierFilter" (change)="applyFilters()" class="filter-select">
              <option value="">All Tiers</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
          </div>
          
          <div class="filter-group">
            <label>Agent</label>
            <select [(ngModel)]="agentFilter" (change)="applyFilters()" class="filter-select">
              <option value="">All Agents</option>
              <option *ngFor="let agent of uniqueAgents()" [value]="agent">{{ agent }}</option>
            </select>
          </div>
          
          <button class="btn-refresh" (click)="refresh()" [class.spinning]="refreshing()">
            ⟳ Refresh
          </button>
        </div>
      </div>

      <div *ngIf="loading()" class="loading-state glass-panel">
        Loading pending interrupts...
      </div>

      <div *ngIf="!loading() && filteredInterrupts().length === 0"
           class="empty-state glass-panel">
        <div class="empty-icon">✓</div>
        <h3>All clear</h3>
        <p *ngIf="!tierFilter && !agentFilter">No pending interrupts requiring approval.</p>
        <p *ngIf="tierFilter || agentFilter">No interrupts match the selected filters.</p>
        <p class="poll-hint">Auto-refreshing every 5s</p>
      </div>

      <div class="interrupt-list">
        <div *ngFor="let interrupt of filteredInterrupts()" 
             class="interrupt-card glass-panel"
             [class.tier-critical]="interrupt.tier === 'CRITICAL'"
             [class.tier-high]="interrupt.tier === 'HIGH'"
             [class.tier-medium]="interrupt.tier === 'MEDIUM'"
             [class.tier-low]="interrupt.tier === 'LOW'">
          
          <div class="card-header">
            <div class="tier-badge" [attr.data-tier]="interrupt.tier">
              {{ interrupt.tier }}
            </div>
            <span class="time-since">{{ formatTimeSince(interrupt.createdAt) }}</span>
          </div>

          <div class="card-body">
            <div class="rule-name">{{ interrupt.ruleName }}</div>
            <div class="agent-name">{{ interrupt.agentName }}</div>
            <div class="escalation-message">{{ interrupt.message }}</div>
          </div>

          <div class="card-actions">
            <button class="btn btn-approve"
                    [disabled]="resolving[interrupt.runId]"
                    (click)="approve(interrupt)">
              {{ resolving[interrupt.runId] ? 'Approving...' : 'Approve' }}
            </button>
            <button class="btn btn-abort"
                    [disabled]="resolving[interrupt.runId]"
                    (click)="abort(interrupt)">
              {{ resolving[interrupt.runId] ? 'Aborting...' : 'Abort' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .inbox-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      height: 100%;
      overflow-y: auto;
    }

    /* Header */
    .header-row {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 4px;
    }
    .header h2 {
      font-size: 1.8rem;
      margin: 0;
    }
    .subtitle {
      color: rgba(255,255,255,0.5);
      font-size: 0.9rem;
      margin: 0 0 16px 0;
    }

    /* Live indicator */
    .live-indicator {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      background: rgba(34,197,94,0.15);
      border: 1px solid rgba(34,197,94,0.3);
      border-radius: 12px;
      font-size: 0.7rem;
      font-weight: 700;
      color: #22c55e;
      letter-spacing: 0.05em;
    }
    .live-indicator .dot {
      width: 6px;
      height: 6px;
      background: #22c55e;
      border-radius: 50%;
    }
    .live-indicator.pulse .dot {
      animation: pulse 2s ease-in-out infinite;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.3; }
    }

    /* Filter bar */
    .filter-bar {
      display: flex;
      align-items: flex-end;
      gap: 12px;
      flex-wrap: wrap;
      padding: 12px;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.05);
      border-radius: 8px;
    }
    .filter-group {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .filter-group label {
      font-size: 0.72rem;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      font-weight: 600;
      letter-spacing: 0.05em;
    }
    .filter-select {
      padding: 6px 10px;
      background: rgba(255,255,255,0.05);
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 6px;
      color: white;
      font-size: 0.85rem;
      cursor: pointer;
      transition: border-color 0.2s;
    }
    .filter-select:hover {
      border-color: rgba(56,189,248,0.3);
    }
    .filter-select:focus {
      outline: none;
      border-color: rgba(56,189,248,0.5);
    }
    .btn-refresh {
      background: transparent;
      border: 1px solid rgba(255,255,255,0.1);
      color: rgba(255,255,255,0.5);
      padding: 6px 12px;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.8rem;
      transition: all 0.2s;
      margin-left: auto;
    }
    .btn-refresh:hover {
      border-color: rgba(56,189,248,0.3);
      color: #38bdf8;
    }
    .btn-refresh.spinning {
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    /* Loading / Empty states */
    .loading-state {
      padding: 24px;
      text-align: center;
      color: rgba(255,255,255,0.5);
    }
    .empty-state {
      text-align: center;
      padding: 48px 24px;
    }
    .empty-icon {
      font-size: 3rem;
      margin-bottom: 12px;
    }
    .empty-state h3 {
      margin-bottom: 8px;
    }
    .empty-state p {
      color: rgba(255,255,255,0.5);
      margin: 4px 0;
    }
    .poll-hint {
      font-size: 0.75rem;
      margin-top: 8px;
    }

    /* Interrupt cards */
    .interrupt-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .interrupt-card {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      border-left: 3px solid transparent;
      transition: all 0.2s;
    }
    .interrupt-card:hover {
      transform: translateX(2px);
    }
    .interrupt-card.tier-critical {
      border-left-color: #ef4444;
      background: rgba(239,68,68,0.03);
    }
    .interrupt-card.tier-high {
      border-left-color: #f59e0b;
      background: rgba(245,158,11,0.03);
    }
    .interrupt-card.tier-medium {
      border-left-color: #eab308;
      background: rgba(234,179,8,0.03);
    }
    .interrupt-card.tier-low {
      border-left-color: #6b7280;
      background: rgba(107,114,128,0.03);
    }

    /* Card header */
    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .tier-badge {
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .tier-badge[data-tier="CRITICAL"] {
      background: rgba(239,68,68,0.2);
      color: #ef4444;
      border: 1px solid rgba(239,68,68,0.3);
    }
    .tier-badge[data-tier="HIGH"] {
      background: rgba(245,158,11,0.2);
      color: #f59e0b;
      border: 1px solid rgba(245,158,11,0.3);
    }
    .tier-badge[data-tier="MEDIUM"] {
      background: rgba(234,179,8,0.2);
      color: #eab308;
      border: 1px solid rgba(234,179,8,0.3);
    }
    .tier-badge[data-tier="LOW"] {
      background: rgba(107,114,128,0.2);
      color: #9ca3af;
      border: 1px solid rgba(107,114,128,0.3);
    }
    .time-since {
      font-size: 0.72rem;
      color: rgba(255,255,255,0.3);
    }

    /* Card body */
    .card-body {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .rule-name {
      font-size: 0.95rem;
      font-weight: 600;
      color: white;
    }
    .agent-name {
      font-family: var(--font-mono);
      font-size: 0.85rem;
      color: #38bdf8;
    }
    .escalation-message {
      font-size: 0.88rem;
      color: rgba(255,255,255,0.7);
      line-height: 1.5;
      margin-top: 4px;
    }

    /* Card actions */
    .card-actions {
      display: flex;
      gap: 8px;
      margin-top: 4px;
    }
    .btn {
      padding: 7px 16px;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.82rem;
      transition: all 0.2s;
    }
    .btn:hover:not(:disabled) {
      opacity: 0.85;
      transform: translateY(-1px);
    }
    .btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .btn-approve {
      background: #22c55e;
      color: white;
    }
    .btn-abort {
      background: #ef4444;
      color: white;
    }

    /* Glass panel utility */
    .glass-panel {
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 8px;
      backdrop-filter: blur(10px);
    }
  `]
})
export class OversightInboxComponent implements OnInit, OnDestroy {
  private oversightInboxService = inject(OversightInboxService);

  loading = signal(false);
  refreshing = signal(false);
  isLive = signal(true);
  liveIndicator = signal(true);

  tierFilter = '';
  agentFilter = '';

  resolving: Record<string, boolean> = {};

  readonly filteredInterrupts = computed(() => {
    let interrupts = this.oversightInboxService.pendingInterrupts();
    
    if (this.tierFilter) {
      interrupts = interrupts.filter(i => i.tier === this.tierFilter);
    }
    
    if (this.agentFilter) {
      interrupts = interrupts.filter(i => i.agentName === this.agentFilter);
    }
    
    return interrupts.sort((a, b) => {
      const tierOrder: Record<string, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
      const tierDiff = (tierOrder[a.tier] ?? 99) - (tierOrder[b.tier] ?? 99);
      if (tierDiff !== 0) return tierDiff;
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
    });
  });

  readonly uniqueAgents = computed(() => {
    const agents = new Set(this.oversightInboxService.pendingInterrupts().map(i => i.agentName));
    return Array.from(agents).sort();
  });

  ngOnInit() {
    this.oversightInboxService.refresh();
  }

  ngOnDestroy() {
  }

  applyFilters() {
  }

  refresh() {
    this.refreshing.set(true);
    this.oversightInboxService.refresh();
    setTimeout(() => {
      this.refreshing.set(false);
    }, 800);
  }

  approve(interrupt: PendingInterrupt) {
    this.resolving[interrupt.runId] = true;
    this.oversightInboxService.resolve(
      interrupt.runId,
      'APPROVE',
      'human-operator',
      `Approved via Approval Inbox for rule: ${interrupt.ruleName}`
    );
    setTimeout(() => {
      this.resolving[interrupt.runId] = false;
    }, 1000);
  }

  abort(interrupt: PendingInterrupt) {
    this.resolving[interrupt.runId] = true;
    this.oversightInboxService.resolve(
      interrupt.runId,
      'ABORT',
      'human-operator',
      `Aborted via Approval Inbox for rule: ${interrupt.ruleName}`
    );
    setTimeout(() => {
      this.resolving[interrupt.runId] = false;
    }, 1000);
  }

  formatTimeSince(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    const seconds = Math.floor(diff / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}d ago`;
    if (hours > 0) return `${hours}h ago`;
    if (minutes > 0) return `${minutes}m ago`;
    if (seconds > 5) return `${seconds}s ago`;
    return 'just now';
  }
}
