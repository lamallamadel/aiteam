import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AnalyticsService } from '../services/analytics.service';

interface Run {
  id: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  currentAgent: string;
  ciFixCount: number;
  e2eFixCount: number;
  repo?: string;
  issueNumber?: number;
}

@Component({
  selector: 'app-run-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="run-list-container">
      <div class="header">
        <h2>Bolt History</h2>
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
          <div class="col col-agent">Current Agent</div>
          <div class="col col-fixes">Fixes</div>
          <div class="col col-date">Created</div>
        </div>

        <div class="table-body">
          <div *ngFor="let run of filteredRuns()" class="table-row" (click)="viewRun(run)">
            <div class="col col-id">{{ run.id.substring(0, 8) }}</div>
            <div class="col col-repo">
              {{ run.repo || 'N/A' }}
              <span *ngIf="run.issueNumber" class="issue-badge">#{{ run.issueNumber }}</span>
            </div>
            <div class="col col-status">
              <span class="status-badge" [ngClass]="getStatusClass(run.status)">
                {{ run.status }}
              </span>
            </div>
            <div class="col col-agent">{{ run.currentAgent || '-' }}</div>
            <div class="col col-fixes">
              <span class="fix-count" *ngIf="run.ciFixCount > 0">CI: {{ run.ciFixCount }}</span>
              <span class="fix-count" *ngIf="run.e2eFixCount > 0">E2E: {{ run.e2eFixCount }}</span>
              <span *ngIf="!run.ciFixCount && !run.e2eFixCount">-</span>
            </div>
            <div class="col col-date">{{ formatDate(run.createdAt) }}</div>
          </div>

          <div *ngIf="filteredRuns().length === 0" class="empty-state">
            <p>No runs found</p>
          </div>
        </div>
      </div>

      <div class="pagination">
        <button 
          (click)="previousPage()" 
          [disabled]="currentPage() === 0"
          class="btn-pagination glass-panel"
        >
          Previous
        </button>
        <span class="page-info">
          Page {{ currentPage() + 1 }} of {{ totalPages() }}
        </span>
        <button 
          (click)="nextPage()" 
          [disabled]="currentPage() >= totalPages() - 1"
          class="btn-pagination glass-panel"
        >
          Next
        </button>
      </div>
    </div>
  `,
  styles: [`
    .run-list-container {
      padding: 24px;
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 24px;
    }

    .header h2 {
      margin: 0;
      color: white;
    }

    .filters {
      display: flex;
      gap: 12px;
    }

    .filter-select, .search-input {
      padding: 8px 12px;
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 6px;
      background: rgba(255, 255, 255, 0.05);
      color: white;
      outline: none;
    }

    .search-input {
      width: 250px;
    }

    .run-table {
      flex: 1;
      background: rgba(255, 255, 255, 0.03);
      border-radius: 12px;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }

    .table-header, .table-row {
      display: grid;
      grid-template-columns: 100px 2fr 120px 150px 120px 150px;
      gap: 16px;
      padding: 16px;
      align-items: center;
    }

    .table-header {
      background: rgba(255, 255, 255, 0.05);
      font-weight: 600;
      color: #94a3b8;
      font-size: 0.875rem;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .table-body {
      flex: 1;
      overflow-y: auto;
    }

    .table-row {
      border-bottom: 1px solid rgba(255, 255, 255, 0.05);
      cursor: pointer;
      transition: background 0.2s;
    }

    .table-row:hover {
      background: rgba(56, 189, 248, 0.1);
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

    .issue-badge {
      display: inline-block;
      margin-left: 8px;
      padding: 2px 8px;
      background: rgba(139, 92, 246, 0.2);
      color: #8b5cf6;
      border-radius: 8px;
      font-size: 0.75rem;
    }

    .fix-count {
      display: inline-block;
      margin-right: 8px;
      padding: 2px 6px;
      background: rgba(56, 189, 248, 0.2);
      color: #38bdf8;
      border-radius: 4px;
      font-size: 0.75rem;
    }

    .pagination {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 16px;
      margin-top: 24px;
    }

    .btn-pagination {
      padding: 8px 16px;
      border: none;
      border-radius: 6px;
      background: rgba(255, 255, 255, 0.05);
      color: white;
      cursor: pointer;
      transition: background 0.2s;
    }

    .btn-pagination:hover:not(:disabled) {
      background: rgba(56, 189, 248, 0.2);
    }

    .btn-pagination:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .page-info {
      color: #94a3b8;
      font-size: 0.875rem;
    }

    .empty-state {
      text-align: center;
      padding: 48px;
      color: #94a3b8;
    }
  `]
})
export class RunListComponent implements OnInit {
  runs = signal<Run[]>([]);
  filteredRuns = signal<Run[]>([]);
  statusFilter = signal('');
  searchTerm = signal('');
  currentPage = signal(0);
  pageSize = 10;
  totalPages = signal(1);

  constructor(
    private analyticsService: AnalyticsService,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadRuns();
  }

  onStatusFilterChange(value: string) {
    this.statusFilter.set(value);
    this.applyFilters();
  }

  onSearchTermChange(value: string) {
    this.searchTerm.set(value);
    this.applyFilters();
  }

  loadRuns() {
    const mockRuns: Run[] = [
      {
        id: '550e8400-e29b-41d4-a716-446655440001',
        status: 'DONE',
        createdAt: new Date(Date.now() - 3600000).toISOString(),
        updatedAt: new Date().toISOString(),
        currentAgent: 'WriterStep',
        ciFixCount: 2,
        e2eFixCount: 1,
        repo: 'atlasia-core',
        issueNumber: 42
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440002',
        status: 'FAILED',
        createdAt: new Date(Date.now() - 7200000).toISOString(),
        updatedAt: new Date().toISOString(),
        currentAgent: 'TesterStep',
        ciFixCount: 5,
        e2eFixCount: 3,
        repo: 'atlasia-frontend',
        issueNumber: 15
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440003',
        status: 'DEVELOPER',
        createdAt: new Date(Date.now() - 1800000).toISOString(),
        updatedAt: new Date().toISOString(),
        currentAgent: 'DeveloperStep',
        ciFixCount: 0,
        e2eFixCount: 0,
        repo: 'atlasia-api',
        issueNumber: 23
      },
      {
        id: '550e8400-e29b-41d4-a716-446655440004',
        status: 'ESCALATED',
        createdAt: new Date(Date.now() - 10800000).toISOString(),
        updatedAt: new Date().toISOString(),
        currentAgent: 'ReviewStep',
        ciFixCount: 8,
        e2eFixCount: 4,
        repo: 'atlasia-core',
        issueNumber: 78
      },
    ];

    this.runs.set(mockRuns);
    this.applyFilters();
  }

  applyFilters() {
    let filtered = this.runs();

    if (this.statusFilter()) {
      filtered = filtered.filter(run => run.status === this.statusFilter());
    }

    if (this.searchTerm()) {
      const term = this.searchTerm().toLowerCase();
      filtered = filtered.filter(run => 
        run.repo?.toLowerCase().includes(term)
      );
    }

    this.filteredRuns.set(filtered);
    this.totalPages.set(Math.ceil(filtered.length / this.pageSize));
  }

  getStatusClass(status: string): string {
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

  formatDate(dateStr: string): string {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    const hours = Math.floor(diff / 3600000);
    const days = Math.floor(diff / 86400000);

    if (minutes < 60) return `${minutes}m ago`;
    if (hours < 24) return `${hours}h ago`;
    if (days < 7) return `${days}d ago`;
    return date.toLocaleDateString();
  }

  viewRun(run: Run) {
    this.router.navigate(['/runs', run.id]);
  }

  previousPage() {
    if (this.currentPage() > 0) {
      this.currentPage.update(p => p - 1);
    }
  }

  nextPage() {
    if (this.currentPage() < this.totalPages() - 1) {
      this.currentPage.update(p => p + 1);
    }
  }
}
