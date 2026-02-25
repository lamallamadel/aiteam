import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrchestratorService } from '../services/orchestrator.service';
import { AgentCard } from '../models/orchestrator.model';
import { ToastService } from '../services/toast.service';

interface AgentWithVersionInfo extends AgentCard {
  installedVersion?: string;
  availableVersion: string;
  isInstalled: boolean;
  updateAvailable: boolean;
}

@Component({
  selector: 'app-agent-marketplace',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="marketplace-container">
      <!-- Header -->
      <div class="header">
        <div class="header-title">
          <h2>Agent Marketplace</h2>
          <p class="subtitle">Browse, filter, and install AI agents with specialized capabilities</p>
        </div>
        <button class="btn-refresh glass-panel" (click)="refresh()" [disabled]="loading()">
          {{ loading() ? 'Loading…' : '↻ Refresh' }}
        </button>
      </div>

      <div class="marketplace-layout">
        <!-- Capability Filter Sidebar -->
        <aside class="filter-sidebar glass-panel">
          <div class="filter-header">
            <h3>Filter by Capability</h3>
            <button class="btn-clear" (click)="clearFilters()" *ngIf="selectedCapabilities().size > 0">
              Clear All
            </button>
          </div>
          
          <div class="search-box">
            <input 
              type="text" 
              [(ngModel)]="capabilitySearch" 
              (ngModelChange)="onCapabilitySearchChange()"
              placeholder="Search capabilities..."
              class="search-input"
            />
          </div>

          <div class="capability-list">
            <label 
              class="capability-item" 
              *ngFor="let cap of filteredCapabilities()"
              [class.selected]="selectedCapabilities().has(cap)"
            >
              <input 
                type="checkbox" 
                [checked]="selectedCapabilities().has(cap)"
                (change)="toggleCapability(cap)"
              />
              <span class="capability-name">{{ cap }}</span>
              <span class="capability-count">{{ getCapabilityCount(cap) }}</span>
            </label>
          </div>
        </aside>

        <!-- Agent Cards Grid -->
        <main class="agents-main">
          <!-- Status Filter Tabs -->
          <div class="status-tabs">
            <button 
              class="status-tab"
              [class.active]="statusFilter() === 'all'"
              (click)="setStatusFilter('all')"
            >
              All ({{ agents().length }})
            </button>
            <button 
              class="status-tab"
              [class.active]="statusFilter() === 'installed'"
              (click)="setStatusFilter('installed')"
            >
              Installed ({{ installedCount() }})
            </button>
            <button 
              class="status-tab"
              [class.active]="statusFilter() === 'available'"
              (click)="setStatusFilter('available')"
            >
              Available ({{ availableCount() }})
            </button>
          </div>

          <div class="agents-grid">
            <div class="agent-card glass-panel" *ngFor="let agent of filteredAgents()">
              <!-- Health Status Badge -->
              <div class="health-badge" [class]="'health-' + agent.status">
                <span class="health-dot"></span>
                {{ agent.status }}
              </div>

              <!-- Agent Header -->
              <div class="agent-header">
                <div class="agent-title">
                  <h3>{{ agent.name }}</h3>
                  <span class="vendor-badge">{{ agent.vendor }}</span>
                </div>
                <div class="version-info">
                  <span class="version-badge">v{{ agent.version }}</span>
                </div>
              </div>

              <!-- Agent Role & Description -->
              <div class="agent-role">
                <span class="role-badge">{{ agent.role }}</span>
              </div>
              <p class="agent-description">{{ agent.description }}</p>

              <!-- Capabilities -->
              <div class="agent-capabilities">
                <div class="capabilities-header">Capabilities</div>
                <div class="capabilities-list">
                  <span 
                    class="capability-pill" 
                    *ngFor="let cap of agent.capabilities"
                    [class.highlighted]="selectedCapabilities().has(cap)"
                  >
                    {{ cap }}
                  </span>
                </div>
              </div>

              <!-- Constraints -->
              <div class="agent-constraints">
                <div class="constraint-item">
                  <span class="constraint-icon">🔢</span>
                  <div class="constraint-details">
                    <span class="constraint-label">Max Tokens</span>
                    <span class="constraint-value">{{ agent.constraints.maxTokens | number }}</span>
                  </div>
                </div>
                <div class="constraint-item">
                  <span class="constraint-icon">⏱️</span>
                  <div class="constraint-details">
                    <span class="constraint-label">Timeout</span>
                    <span class="constraint-value">{{ formatDuration(agent.constraints.maxDurationMs) }}</span>
                  </div>
                </div>
                <div class="constraint-item">
                  <span class="constraint-icon">💰</span>
                  <div class="constraint-details">
                    <span class="constraint-label">Cost Budget</span>
                    <span class="constraint-value">\${{ agent.constraints.costBudgetUsd.toFixed(2) }}</span>
                  </div>
                </div>
              </div>

              <!-- Installation Status & Action -->
              <div class="agent-actions">
                <div class="installation-status" *ngIf="agent.isInstalled">
                  <span class="installed-badge">
                    <span class="check-icon">✓</span>
                    Installed
                  </span>
                  <span class="installed-version">v{{ agent.version }}</span>
                </div>
                <button 
                  class="btn-install" 
                  *ngIf="!agent.isInstalled"
                  (click)="installAgent(agent)"
                  [disabled]="installing()"
                >
                  {{ installing() ? 'Installing...' : 'Install Agent' }}
                </button>
              </div>
            </div>

            <!-- Empty State -->
            <div class="empty-state" *ngIf="filteredAgents().length === 0 && !loading()">
              <div class="empty-icon">🔍</div>
              <h3>No agents found</h3>
              <p>Try adjusting your filters or search criteria</p>
            </div>
          </div>
        </main>
      </div>

      <!-- Version Management Table -->
      <section class="version-management glass-panel" *ngIf="installedAgents().length > 0">
        <h3 class="section-title">Version Management</h3>
        <div class="version-table">
          <div class="table-header">
            <div class="col col-name">Agent Name</div>
            <div class="col col-vendor">Vendor</div>
            <div class="col col-installed">Installed Version</div>
            <div class="col col-available">Latest Available</div>
            <div class="col col-status">Status</div>
            <div class="col col-actions">Actions</div>
          </div>
          <div class="table-row" *ngFor="let agent of installedAgents()">
            <div class="col col-name">
              <span class="agent-name">{{ agent.name }}</span>
            </div>
            <div class="col col-vendor">{{ agent.vendor }}</div>
            <div class="col col-installed">
              <span class="version-badge">v{{ agent.version }}</span>
            </div>
            <div class="col col-available">
              <span class="version-badge">v{{ agent.version }}</span>
            </div>
            <div class="col col-status">
              <span class="status-pill" [class]="'status-' + agent.status">
                {{ agent.status }}
              </span>
            </div>
            <div class="col col-actions">
              <button class="btn-action" disabled>Up to date</button>
            </div>
          </div>
        </div>
      </section>
    </div>
  `,
  styles: [`
    .marketplace-container {
      padding: 24px;
      height: 100%;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
    }

    .header-title h2 {
      margin: 0 0 4px;
      font-size: 1.8rem;
      color: white;
    }

    .subtitle {
      color: rgba(255,255,255,0.5);
      font-size: 0.9rem;
      margin: 0;
    }

    .btn-refresh {
      padding: 10px 20px;
      border: none;
      border-radius: 8px;
      background: rgba(255,255,255,0.06);
      color: white;
      cursor: pointer;
      font-size: 0.9rem;
      transition: all 0.2s;
      white-space: nowrap;
    }

    .btn-refresh:hover:not(:disabled) {
      background: rgba(56,189,248,0.2);
    }

    .btn-refresh:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .marketplace-layout {
      display: grid;
      grid-template-columns: 280px 1fr;
      gap: 24px;
      flex: 1;
      min-height: 0;
    }

    /* Filter Sidebar */
    .filter-sidebar {
      padding: 20px;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      height: fit-content;
      max-height: calc(100vh - 200px);
      overflow: hidden;
      position: sticky;
      top: 24px;
    }

    .filter-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
    }

    .filter-header h3 {
      margin: 0;
      font-size: 1rem;
      color: white;
      font-weight: 600;
    }

    .btn-clear {
      padding: 4px 10px;
      border: none;
      border-radius: 6px;
      background: rgba(239,68,68,0.2);
      color: #ef4444;
      cursor: pointer;
      font-size: 0.75rem;
      transition: background 0.2s;
    }

    .btn-clear:hover {
      background: rgba(239,68,68,0.3);
    }

    .search-box {
      margin-bottom: 8px;
    }

    .search-input {
      width: 100%;
      padding: 8px 12px;
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 6px;
      background: rgba(0,0,0,0.3);
      color: white;
      font-size: 0.85rem;
      outline: none;
    }

    .search-input:focus {
      border-color: rgba(56,189,248,0.5);
    }

    .capability-list {
      display: flex;
      flex-direction: column;
      gap: 4px;
      overflow-y: auto;
      max-height: 500px;
    }

    .capability-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 10px;
      border-radius: 6px;
      cursor: pointer;
      transition: background 0.2s;
      font-size: 0.85rem;
    }

    .capability-item:hover {
      background: rgba(255,255,255,0.05);
    }

    .capability-item.selected {
      background: rgba(56,189,248,0.15);
    }

    .capability-item input[type="checkbox"] {
      cursor: pointer;
    }

    .capability-name {
      flex: 1;
      color: rgba(255,255,255,0.8);
      font-size: 0.8rem;
    }

    .capability-count {
      padding: 2px 8px;
      background: rgba(255,255,255,0.1);
      border-radius: 10px;
      font-size: 0.7rem;
      color: rgba(255,255,255,0.5);
    }

    /* Main Content */
    .agents-main {
      display: flex;
      flex-direction: column;
      gap: 16px;
      min-height: 0;
    }

    .status-tabs {
      display: flex;
      gap: 8px;
      padding: 4px;
      background: rgba(0,0,0,0.2);
      border-radius: 8px;
      width: fit-content;
    }

    .status-tab {
      padding: 8px 16px;
      border: none;
      border-radius: 6px;
      background: transparent;
      color: rgba(255,255,255,0.6);
      cursor: pointer;
      font-size: 0.85rem;
      transition: all 0.2s;
    }

    .status-tab:hover {
      background: rgba(255,255,255,0.05);
      color: rgba(255,255,255,0.8);
    }

    .status-tab.active {
      background: rgba(56,189,248,0.2);
      color: #38bdf8;
    }

    .agents-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
      gap: 16px;
      overflow-y: auto;
    }

    .agent-card {
      padding: 20px;
      border-radius: 12px;
      display: flex;
      flex-direction: column;
      gap: 14px;
      position: relative;
      border: 1px solid rgba(255,255,255,0.05);
      transition: all 0.2s;
    }

    .agent-card:hover {
      border-color: rgba(56,189,248,0.3);
      box-shadow: 0 4px 20px rgba(56,189,248,0.1);
    }

    .health-badge {
      position: absolute;
      top: 16px;
      right: 16px;
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .health-badge.health-active {
      background: rgba(34,197,94,0.2);
      color: #22c55e;
    }

    .health-badge.health-degraded {
      background: rgba(251,191,36,0.2);
      color: #fbbf24;
    }

    .health-badge.health-offline, .health-badge.health-inactive {
      background: rgba(148,163,184,0.2);
      color: #94a3b8;
    }

    .health-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: currentColor;
    }

    .health-active .health-dot {
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.5; }
    }

    .agent-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
      padding-right: 80px;
    }

    .agent-title {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .agent-title h3 {
      margin: 0;
      font-size: 1.1rem;
      color: white;
    }

    .vendor-badge {
      padding: 2px 8px;
      background: rgba(139,92,246,0.2);
      color: #a78bfa;
      border-radius: 6px;
      font-size: 0.7rem;
      font-weight: 600;
      width: fit-content;
    }

    .version-badge {
      padding: 4px 8px;
      background: rgba(255,255,255,0.1);
      color: rgba(255,255,255,0.7);
      border-radius: 6px;
      font-size: 0.75rem;
      font-family: monospace;
    }

    .agent-role {
      display: flex;
      gap: 8px;
    }

    .role-badge {
      padding: 4px 10px;
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .agent-description {
      font-size: 0.85rem;
      color: rgba(255,255,255,0.6);
      margin: 0;
      line-height: 1.5;
    }

    .agent-capabilities {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 12px;
      background: rgba(0,0,0,0.2);
      border-radius: 8px;
    }

    .capabilities-header {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      font-weight: 600;
      letter-spacing: 0.05em;
    }

    .capabilities-list {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }

    .capability-pill {
      padding: 4px 10px;
      background: rgba(56,189,248,0.1);
      color: rgba(56,189,248,0.8);
      border-radius: 10px;
      font-size: 0.7rem;
      font-weight: 500;
      transition: all 0.2s;
    }

    .capability-pill.highlighted {
      background: rgba(56,189,248,0.3);
      color: #38bdf8;
      box-shadow: 0 0 8px rgba(56,189,248,0.4);
    }

    .agent-constraints {
      display: flex;
      gap: 8px;
      padding: 12px;
      background: rgba(0,0,0,0.2);
      border-radius: 8px;
    }

    .constraint-item {
      flex: 1;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .constraint-icon {
      font-size: 1.2rem;
    }

    .constraint-details {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .constraint-label {
      font-size: 0.65rem;
      color: rgba(255,255,255,0.4);
      text-transform: uppercase;
    }

    .constraint-value {
      font-size: 0.8rem;
      color: white;
      font-weight: 600;
    }

    .agent-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-top: 8px;
      border-top: 1px solid rgba(255,255,255,0.05);
    }

    .installation-status {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .installed-badge {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      background: rgba(34,197,94,0.15);
      color: #22c55e;
      border-radius: 8px;
      font-size: 0.8rem;
      font-weight: 600;
    }

    .check-icon {
      font-weight: 700;
    }

    .installed-version {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.5);
      font-family: monospace;
    }

    .btn-install {
      padding: 10px 20px;
      border: none;
      border-radius: 8px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      cursor: pointer;
      font-size: 0.85rem;
      font-weight: 600;
      transition: all 0.2s;
    }

    .btn-install:hover:not(:disabled) {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102,126,234,0.4);
    }

    .btn-install:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .empty-state {
      grid-column: 1 / -1;
      text-align: center;
      padding: 60px 20px;
      color: rgba(255,255,255,0.4);
    }

    .empty-icon {
      font-size: 3rem;
      margin-bottom: 16px;
    }

    .empty-state h3 {
      margin: 0 0 8px;
      color: rgba(255,255,255,0.6);
    }

    .empty-state p {
      margin: 0;
      font-size: 0.9rem;
    }

    /* Version Management Table */
    .version-management {
      padding: 24px;
      border-radius: 12px;
    }

    .section-title {
      margin: 0 0 16px;
      font-size: 1.2rem;
      color: white;
    }

    .version-table {
      display: flex;
      flex-direction: column;
      gap: 1px;
      background: rgba(255,255,255,0.05);
      border-radius: 8px;
      overflow: hidden;
    }

    .table-header, .table-row {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr 1fr 1fr 1fr;
      gap: 16px;
      padding: 12px 16px;
      background: rgba(0,0,0,0.2);
      align-items: center;
    }

    .table-header {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      font-weight: 600;
      letter-spacing: 0.05em;
    }

    .table-row {
      transition: background 0.2s;
      font-size: 0.85rem;
    }

    .table-row:hover {
      background: rgba(56,189,248,0.05);
    }

    .col {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .status-pill {
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
      width: fit-content;
    }

    .status-pill.status-active {
      background: rgba(34,197,94,0.2);
      color: #22c55e;
    }

    .status-pill.status-degraded {
      background: rgba(251,191,36,0.2);
      color: #fbbf24;
    }

    .status-pill.status-offline, .status-pill.status-inactive {
      background: rgba(148,163,184,0.2);
      color: #94a3b8;
    }

    .btn-action {
      padding: 6px 14px;
      border: none;
      border-radius: 6px;
      background: rgba(255,255,255,0.1);
      color: rgba(255,255,255,0.6);
      cursor: pointer;
      font-size: 0.75rem;
      transition: all 0.2s;
    }

    .btn-action:hover:not(:disabled) {
      background: rgba(56,189,248,0.2);
      color: #38bdf8;
    }

    .btn-action:disabled {
      cursor: not-allowed;
      opacity: 0.5;
    }

    @media (max-width: 1200px) {
      .marketplace-layout {
        grid-template-columns: 1fr;
      }

      .filter-sidebar {
        position: relative;
        max-height: 400px;
      }

      .agents-grid {
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      }
    }

    @media (max-width: 768px) {
      .agents-grid {
        grid-template-columns: 1fr;
      }

      .table-header, .table-row {
        grid-template-columns: 1fr;
        gap: 8px;
      }

      .table-header {
        display: none;
      }

      .col {
        display: grid;
        grid-template-columns: 120px 1fr;
        gap: 8px;
      }

      .col::before {
        content: attr(data-label);
        color: rgba(255,255,255,0.5);
        font-size: 0.75rem;
        text-transform: uppercase;
      }
    }
  `]
})
export class AgentMarketplaceComponent implements OnInit, OnDestroy {
  agents = signal<AgentCard[]>([]);
  allCapabilities = signal<string[]>([]);
  selectedCapabilities = signal<Set<string>>(new Set());
  statusFilter = signal<'all' | 'installed' | 'available'>('all');
  loading = signal(true);
  installing = signal(false);
  capabilitySearch = '';
  
  filteredCapabilities = computed(() => {
    const search = this.capabilitySearch.toLowerCase();
    return this.allCapabilities().filter(cap => 
      cap.toLowerCase().includes(search)
    ).sort();
  });

  installedAgents = computed(() => {
    return this.agents().filter(agent => 
      agent.vendor === 'atlasia' || agent.transport === 'local'
    );
  });

  installedCount = computed(() => this.installedAgents().length);
  
  availableCount = computed(() => {
    return this.agents().length - this.installedCount();
  });

  filteredAgents = computed(() => {
    let filtered = this.agents();

    // Apply status filter
    if (this.statusFilter() === 'installed') {
      filtered = this.installedAgents();
    } else if (this.statusFilter() === 'available') {
      filtered = filtered.filter(agent => 
        agent.vendor !== 'atlasia' && agent.transport !== 'local'
      );
    }

    // Apply capability filter
    const selectedCaps = this.selectedCapabilities();
    if (selectedCaps.size > 0) {
      filtered = filtered.filter(agent =>
        Array.from(selectedCaps).every(cap => 
          agent.capabilities.includes(cap)
        )
      );
    }

    return filtered;
  });

  constructor(
    private orchestratorService: OrchestratorService,
    private toastService: ToastService
  ) {}

  ngOnInit() {
    this.loadData();
  }

  ngOnDestroy() {}

  loadData() {
    this.loading.set(true);
    
    this.orchestratorService.listAgents().subscribe({
      next: (agents) => {
        this.agents.set(agents);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load agents:', err);
        this.loading.set(false);
        this.toastService.error('Failed to load agents');
      }
    });

    this.orchestratorService.listCapabilities().subscribe({
      next: (capabilities) => {
        this.allCapabilities.set(capabilities);
      },
      error: (err) => {
        console.error('Failed to load capabilities:', err);
      }
    });
  }

  refresh() {
    this.loadData();
  }

  toggleCapability(capability: string) {
    const current = new Set(this.selectedCapabilities());
    if (current.has(capability)) {
      current.delete(capability);
    } else {
      current.add(capability);
    }
    this.selectedCapabilities.set(current);
  }

  clearFilters() {
    this.selectedCapabilities.set(new Set());
    this.capabilitySearch = '';
  }

  onCapabilitySearchChange() {
    // Trigger re-computation of filteredCapabilities
  }

  setStatusFilter(filter: 'all' | 'installed' | 'available') {
    this.statusFilter.set(filter);
  }

  getCapabilityCount(capability: string): number {
    return this.agents().filter(agent => 
      agent.capabilities.includes(capability)
    ).length;
  }

  installAgent(agent: AgentCard) {
    this.installing.set(true);
    
    this.orchestratorService.installAgent(agent).subscribe({
      next: (response) => {
        this.installing.set(false);
        if (response.success) {
          this.toastService.success(`Agent ${agent.name} installed successfully`);
          this.loadData();
        } else {
          this.toastService.error(response.message);
        }
      },
      error: (err) => {
        this.installing.set(false);
        const message = err.error?.message || 'Failed to install agent';
        this.toastService.error(message);
      }
    });
  }

  formatDuration(ms: number): string {
    if (ms < 60000) return `${ms / 1000}s`;
    if (ms < 3600000) return `${(ms / 60000).toFixed(1)}m`;
    return `${(ms / 3600000).toFixed(1)}h`;
  }
}
