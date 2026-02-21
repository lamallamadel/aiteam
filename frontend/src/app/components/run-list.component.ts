import { Component, OnInit, OnDestroy, signal, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { switchMap, startWith } from 'rxjs/operators';
import { OrchestratorService } from '../services/orchestrator.service';
import { RunResponse } from '../models/orchestrator.model';

const ACTIVE_STATUSES = new Set(['RECEIVED', 'PM', 'QUALIFIER', 'ARCHITECT', 'DEVELOPER', 'REVIEW', 'TESTER', 'WRITER', 'JUDGE']);

@Component({
  selector: 'app-run-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="run-list-container">
      <div class="header">
        <div class="header-left">
          <h2>Bolt History</h2>
          <span *ngIf="activeCount() > 0" class="live-chip">
            <span class="live-dot"></span>{{ activeCount() }} active
          </span>
          <span *ngIf="loading()" class="loading-chip">refreshing…</span>
        </div>
        <div class="filters">
          <select [ngModel]="statusFilter()" (ngModelChange)="onStatusFilterChange($event)" class="filter-select glass-panel">
            <option value="">All Statuses</option>
            <option value="DONE">Done</option>
            <option value="FAILED">Failed</option>
            <option value="ESCALATED">Escalated</option>
            <option value="DEVELOPER">In Progress</option>
          </select>
          <input
            type="text"
            [ngModel]="searchTerm()"
            (ngModelChange)="onSearchTermChange($event)"
            placeholder="Search by repo..."
            class="search-input glass-panel"
          />
        </div>
      </div>

      <div class="run-table">
        <div class="table-header">
          <div class="col col-id">ID</div>
          <div class="col col-repo">Repository</div>
          <div class="col col-status">Status</div>
          <div class="col col-agent">Agent</div>
          <div class="col col-fixes">Fixes</div>
          <div class="col col-date">Created</div>
        </div>

        <div class="table-body">
          <div *ngFor="let run of pagedRuns()" class="table-row" (click)="viewRun(run)">
            <div class="col col-id mono">{{ run.id.substring(0, 8) }}</div>
            <div class="col col-repo">
              {{ run.repo || 'N/A' }}
              <span *ngIf="run.issueNumber" class="issue-badge">#{{ run.issueNumber }}</span>
            </div>
            <div class="col col-status">
              <span class="status-badge" [ngClass]="getStatusClass(run.status)">
                {{ run.status }}
              </span>
              <span *ngIf="run.status === 'ESCALATED'" class="escalation-dot" title="Needs human decision">⚠</span>
            </div>
            <div class="col col-agent">
              <span *ngIf="run.currentAgent" class="agent-chip">{{ run.currentAgent }}</span>
              <span *ngIf="!run.currentAgent" class="dim">—</span>
            </div>
            <div class="col col-fixes">
              <span class="fix-count" *ngIf="run.ciFixCount > 0">CI:{{ run.ciFixCount }}</span>
              <span class="fix-count e2e" *ngIf="run.e2eFixCount > 0">E2E:{{ run.e2eFixCount }}</span>
              <span class="dim" *ngIf="!run.ciFixCount && !run.e2eFixCount">—</span>
            </div>
            <div class="col col-date dim">{{ formatAge(run.createdAt) }}</div>
          </div>

          <div *ngIf="filteredRuns().length === 0 && !loading()" class="empty-state">
            <p>No runs found</p>
          </div>
          <div *ngIf="filteredRuns().length === 0 && loading()" class="empty-state">
            <p>Loading…</p>
          </div>
        </div>
      </div>

      <div class="footer-row">
        <span class="total-label">{{ filteredRuns().length }} runs</span>
        <div class="pagination">
          <button (click)="previousPage()" [disabled]="currentPage() === 0" class="btn-pagination glass-panel">
            ‹ Prev
          </button>
          <span class="page-info">{{ currentPage() + 1 }} / {{ totalPages() }}</span>
          <button (click)="nextPage()" [disabled]="currentPage() >= totalPages() - 1" class="btn-pagination glass-panel">
            Next ›
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .run-list-container {
      padding: 24px;
      height: 100%;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .header-left { display: flex; align-items: center; gap: 10px; }
    .header-left h2 { margin: 0; color: white; }
    .live-chip {
      display: flex;
      align-items: center;
      gap: 5px;
      font-size: 0.72rem;
      font-weight: 600;
      color: #22c55e;
      background: rgba(34,197,94,0.12);
      padding: 2px 8px;
      border-radius: 8px;
    }
    .live-dot {
      width: 6px; height: 6px;
      background: #22c55e;
      border-radius: 50%;
      animation: blink 1s infinite;
    }
    @keyframes blink { 0%,100%{opacity:1} 50%{opacity:0.3} }
    .loading-chip { font-size: 0.72rem; color: rgba(255,255,255,0.35); }
    .filters { display: flex; gap: 12px; }
    .filter-select, .search-input {
      padding: 8px 12px;
      border: 1px solid rgba(255,255,255,0.1);
      border-radius: 6px;
      background: rgba(255,255,255,0.05);
      color: white;
      outline: none;
    }
    .search-input { width: 220px; }

    .run-table {
      flex: 1;
      background: rgba(255,255,255,0.03);
      border-radius: 12px;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }
    .table-header, .table-row {
      display: grid;
      grid-template-columns: 90px 2fr 130px 130px 120px 120px;
      gap: 12px;
      padding: 14px 16px;
      align-items: center;
    }
    .table-header {
      background: rgba(255,255,255,0.05);
      font-weight: 600;
      color: #94a3b8;
      font-size: 0.78rem;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .table-body { flex: 1; overflow-y: auto; }
    .table-row {
      border-bottom: 1px solid rgba(255,255,255,0.04);
      cursor: pointer;
      transition: background 0.15s;
    }
    .table-row:hover { background: rgba(56,189,248,0.07); }
    .mono { font-family: monospace; }
    .dim { color: rgba(255,255,255,0.35); }

    .status-badge {
      display: inline-block;
      padding: 3px 10px;
      border-radius: 10px;
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .done      { background: rgba(34,197,94,0.15);  color: #22c55e; }
    .failed    { background: rgba(239,68,68,0.15);  color: #ef4444; }
    .escalated { background: rgba(251,191,36,0.15); color: #fbbf24; }
    .in-progress { background: rgba(56,189,248,0.15); color: #38bdf8; }
    .escalation-dot { color: #fbbf24; margin-left: 4px; font-size: 0.8rem; }

    .issue-badge {
      margin-left: 6px;
      padding: 1px 6px;
      background: rgba(139,92,246,0.15);
      color: #8b5cf6;
      border-radius: 6px;
      font-size: 0.7rem;
    }
    .agent-chip {
      padding: 1px 7px;
      background: rgba(56,189,248,0.1);
      color: #38bdf8;
      border-radius: 6px;
      font-size: 0.72rem;
    }
    .fix-count {
      display: inline-block;
      margin-right: 4px;
      padding: 1px 5px;
      background: rgba(56,189,248,0.12);
      color: #38bdf8;
      border-radius: 4px;
      font-size: 0.7rem;
    }
    .fix-count.e2e { background: rgba(139,92,246,0.12); color: #8b5cf6; }

    .footer-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .total-label { font-size: 0.78rem; color: rgba(255,255,255,0.35); }
    .pagination { display: flex; align-items: center; gap: 10px; }
    .btn-pagination {
      padding: 6px 14px;
      border: none;
      border-radius: 6px;
      background: rgba(255,255,255,0.05);
      color: white;
      cursor: pointer;
      font-size: 0.82rem;
      transition: background 0.15s;
    }
    .btn-pagination:hover:not(:disabled) { background: rgba(56,189,248,0.15); }
    .btn-pagination:disabled { opacity: 0.4; cursor: not-allowed; }
    .page-info { color: #94a3b8; font-size: 0.82rem; }
    .empty-state { text-align: center; padding: 48px; color: #94a3b8; }
  `]
})
export class RunListComponent implements OnInit, OnDestroy {
  private orchestratorService = inject(OrchestratorService);
  private router = inject(Router);

  private allRuns = signal<RunResponse[]>([]);
  loading = signal(true);
  statusFilter = signal('');
  searchTerm = signal('');
  currentPage = signal(0);
  readonly pageSize = 15;

  private pollSub: Subscription | null = null;

  filteredRuns = computed(() => {
    let runs = this.allRuns();
    const sf = this.statusFilter();
    const st = this.searchTerm().toLowerCase();
    if (sf) runs = runs.filter(r => r.status === sf || (sf === 'DEVELOPER' && ACTIVE_STATUSES.has(r.status)));
    if (st) runs = runs.filter(r => r.repo?.toLowerCase().includes(st));
    return runs;
  });

  activeCount = computed(() => this.allRuns().filter(r => ACTIVE_STATUSES.has(r.status)).length);
  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredRuns().length / this.pageSize)));
  pagedRuns = computed(() => {
    const start = this.currentPage() * this.pageSize;
    return this.filteredRuns().slice(start, start + this.pageSize);
  });

  ngOnInit() {
    this.pollSub = interval(5000).pipe(
      startWith(0),
      switchMap(() => this.orchestratorService.getRuns())
    ).subscribe({
      next: (runs) => {
        this.allRuns.set(runs);
        this.loading.set(false);
        // Reset to page 0 if current page is out of range
        if (this.currentPage() >= this.totalPages()) this.currentPage.set(0);
      },
      error: () => this.loading.set(false)
    });
  }

  ngOnDestroy() {
    this.pollSub?.unsubscribe();
  }

  onStatusFilterChange(value: string) {
    this.statusFilter.set(value);
    this.currentPage.set(0);
  }

  onSearchTermChange(value: string) {
    this.searchTerm.set(value);
    this.currentPage.set(0);
  }

  getStatusClass(status: string): string {
    if (status === 'DONE') return 'done';
    if (status === 'FAILED') return 'failed';
    if (status === 'ESCALATED') return 'escalated';
    return 'in-progress';
  }

  formatAge(dateStr: string): string {
    try {
      const diff = Date.now() - new Date(dateStr).getTime();
      const m = Math.floor(diff / 60000);
      const h = Math.floor(diff / 3600000);
      const d = Math.floor(diff / 86400000);
      if (m < 60) return `${m}m ago`;
      if (h < 24) return `${h}h ago`;
      if (d < 7) return `${d}d ago`;
      return new Date(dateStr).toLocaleDateString();
    } catch { return dateStr; }
  }

  viewRun(run: RunResponse) { this.router.navigate(['/runs', run.id]); }
  previousPage() { if (this.currentPage() > 0) this.currentPage.update(p => p - 1); }
  nextPage() { if (this.currentPage() < this.totalPages() - 1) this.currentPage.update(p => p + 1); }
}
