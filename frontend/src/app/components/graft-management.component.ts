import { Component, OnInit, Input, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { GraftExecution, CircuitBreakerStatus } from '../models/orchestrator.model';

@Component({
  selector: 'app-graft-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="graft-management">
      <!-- Header -->
      <div class="graft-header">
        <h2 class="graft-title">
          <span class="icon">🔀</span>
          Graft Management
        </h2>
        <div class="header-controls">
          <button class="refresh-btn" (click)="refresh()">
            <span class="icon">↻</span> Refresh
          </button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="graft-tabs">
        <button 
          class="tab-btn" 
          [class.active]="activeTab() === 'history'"
          (click)="activeTab.set('history')">
          <span class="icon">📋</span> Execution History
        </button>
        <button 
          class="tab-btn" 
          [class.active]="activeTab() === 'scheduler'"
          (click)="activeTab.set('scheduler')">
          <span class="icon">⚡</span> Graft Scheduler
        </button>
        <button 
          class="tab-btn" 
          [class.active]="activeTab() === 'circuit-breaker'"
          (click)="activeTab.set('circuit-breaker')">
          <span class="icon">🔌</span> Circuit Breaker
        </button>
      </div>

      <!-- History View -->
      <div *ngIf="activeTab() === 'history'" class="tab-content">
        <div class="history-filters">
          <input 
            type="text" 
            class="filter-input"
            placeholder="Filter by run ID or agent name..."
            [(ngModel)]="historyFilter"
            (input)="filterExecutions()">
          <span class="result-count">{{ filteredExecutions().length }} executions</span>
        </div>

        <div class="executions-table">
          <table>
            <thead>
              <tr>
                <th>Graft ID</th>
                <th>Agent</th>
                <th>Checkpoint</th>
                <th>Status</th>
                <th>Started</th>
                <th>Duration</th>
                <th>Retries</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let exec of filteredExecutions()" class="exec-row">
                <td class="graft-id-cell">
                  <code>{{ exec.graftId.substring(0, 8) }}</code>
                </td>
                <td class="agent-cell">
                  <span class="agent-badge">{{ exec.agentName }}</span>
                </td>
                <td class="checkpoint-cell">{{ exec.checkpointAfter }}</td>
                <td class="status-cell">
                  <span class="status-badge" [ngClass]="getStatusClass(exec.status)">
                    {{ exec.status }}
                  </span>
                </td>
                <td class="time-cell">{{ formatTime(exec.startedAt) }}</td>
                <td class="duration-cell">
                  <span *ngIf="exec.completedAt">
                    {{ calculateDuration(exec.startedAt, exec.completedAt) }}
                  </span>
                  <span *ngIf="!exec.completedAt" class="in-progress">—</span>
                </td>
                <td class="retry-cell">
                  <span class="retry-count" *ngIf="exec.retryCount > 0">
                    {{ exec.retryCount }}
                  </span>
                  <span *ngIf="exec.retryCount === 0" class="no-retries">—</span>
                </td>
                <td class="actions-cell">
                  <button 
                    *ngIf="exec.outputArtifactId" 
                    class="view-artifact-btn"
                    (click)="viewArtifact(exec.outputArtifactId)"
                    title="View artifact">
                    📄
                  </button>
                  <button 
                    *ngIf="exec.errorMessage" 
                    class="view-error-btn"
                    (click)="viewError(exec)"
                    title="View error">
                    ⚠️
                  </button>
                </td>
              </tr>
              <tr *ngIf="filteredExecutions().length === 0">
                <td colspan="8" class="no-data">No graft executions found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Scheduler View -->
      <div *ngIf="activeTab() === 'scheduler'" class="tab-content scheduler-content">
        <div class="scheduler-form glass-panel">
          <h3 class="form-title">Schedule New Graft</h3>
          
          <div class="form-group">
            <label>Run ID</label>
            <input 
              type="text" 
              class="form-input"
              [(ngModel)]="schedulerForm.runId"
              placeholder="Enter run UUID">
          </div>

          <div class="form-group">
            <label>Inject After Checkpoint</label>
            <select class="form-select" [(ngModel)]="schedulerForm.checkpoint">
              <option value="">Select checkpoint...</option>
              <option value="PM">PM</option>
              <option value="QUALIFIER">QUALIFIER</option>
              <option value="ARCHITECT">ARCHITECT</option>
              <option value="DEVELOPER">DEVELOPER</option>
              <option value="REVIEW">REVIEW</option>
              <option value="TESTER">TESTER</option>
            </select>
          </div>

          <div class="form-group">
            <label>Agent Name</label>
            <div class="agent-autocomplete">
              <input 
                type="text" 
                class="form-input"
                [(ngModel)]="schedulerForm.agentName"
                (input)="filterAgents()"
                (focus)="showAgentSuggestions = true"
                placeholder="Type to search agents...">
              
              <div *ngIf="showAgentSuggestions && filteredAgents().length > 0" 
                   class="agent-suggestions">
                <div 
                  *ngFor="let agent of filteredAgents()" 
                  class="suggestion-item"
                  (click)="selectAgent(agent)">
                  {{ agent }}
                </div>
              </div>
            </div>
          </div>

          <div class="form-group">
            <label>Timeout (ms)</label>
            <input 
              type="number" 
              class="form-input"
              [(ngModel)]="schedulerForm.timeoutMs"
              placeholder="300000">
          </div>

          <div class="form-actions">
            <button 
              class="schedule-btn" 
              (click)="scheduleGraft()"
              [disabled]="!canSchedule()">
              <span class="icon">⚡</span> Schedule Graft
            </button>
            <button class="clear-btn" (click)="clearSchedulerForm()">Clear</button>
          </div>

          <div *ngIf="schedulerMessage" class="scheduler-message" [class.error]="schedulerError">
            {{ schedulerMessage }}
          </div>
        </div>
      </div>

      <!-- Circuit Breaker View -->
      <div *ngIf="activeTab() === 'circuit-breaker'" class="tab-content">
        <div class="cb-dashboard">
          <div *ngFor="let status of circuitBreakerStatuses()" class="cb-card glass-panel">
            <div class="cb-header">
              <div class="cb-agent">
                <span class="agent-badge">{{ status.agentName }}</span>
                <span class="cb-state-badge" [ngClass]="getCircuitStateClass(status.state)">
                  {{ status.state }}
                </span>
              </div>
              <button 
                *ngIf="status.state !== 'CLOSED'" 
                class="reset-btn"
                (click)="resetCircuit(status.agentName)">
                Reset
              </button>
            </div>

            <div class="cb-metrics">
              <div class="metric">
                <div class="metric-label">Failure Rate</div>
                <div class="metric-value">
                  <div class="failure-rate-bar">
                    <div 
                      class="failure-rate-fill" 
                      [style.width.%]="status.failureRate * 100"
                      [class.high]="status.failureRate > 0.5">
                    </div>
                  </div>
                  <span class="rate-text">{{ (status.failureRate * 100).toFixed(1) }}%</span>
                </div>
              </div>

              <div class="metric">
                <div class="metric-label">Executions</div>
                <div class="metric-value metric-split">
                  <span class="success-count">✓ {{ status.successfulExecutions }}</span>
                  <span class="failure-count">✗ {{ status.failedExecutions }}</span>
                </div>
              </div>

              <div class="metric">
                <div class="metric-label">Current Failures</div>
                <div class="metric-value">
                  <span class="failure-badge">{{ status.failureCount }}</span>
                </div>
              </div>

              <div class="metric" *ngIf="status.lastFailureTime">
                <div class="metric-label">Last Failure</div>
                <div class="metric-value time-value">{{ formatTime(status.lastFailureTime) }}</div>
              </div>
            </div>

            <div *ngIf="status.recentFailures.length > 0" class="cb-recent-failures">
              <div class="failures-header">Recent Failures</div>
              <div class="failure-list">
                <div *ngFor="let failure of status.recentFailures.slice(0, 3)" class="failure-item">
                  <code class="failure-graft-id">{{ failure.graftId.substring(0, 8) }}</code>
                  <span class="failure-time">{{ formatTime(failure.timestamp) }}</span>
                  <div class="failure-msg">{{ truncate(failure.errorMessage, 60) }}</div>
                </div>
              </div>
            </div>
          </div>

          <div *ngIf="circuitBreakerStatuses().length === 0" class="no-data">
            No circuit breaker data available
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .graft-management {
      padding: 20px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .graft-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }

    .graft-title {
      font-size: 1.5rem;
      font-weight: 800;
      color: rgba(255,255,255,0.9);
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 0;
    }

    .graft-title .icon {
      font-size: 1.8rem;
    }

    .header-controls {
      display: flex;
      gap: 10px;
    }

    .refresh-btn {
      padding: 8px 16px;
      background: rgba(56,189,248,0.1);
      border: 1px solid rgba(56,189,248,0.3);
      border-radius: 6px;
      color: #38bdf8;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 6px;
      transition: all 0.2s;
    }

    .refresh-btn:hover {
      background: rgba(56,189,248,0.15);
      border-color: #38bdf8;
    }

    .graft-tabs {
      display: flex;
      gap: 2px;
      margin-bottom: 20px;
      background: var(--border);
      border-radius: 8px 8px 0 0;
      overflow: hidden;
    }

    .tab-btn {
      flex: 1;
      padding: 12px 20px;
      background: var(--surface);
      border: none;
      color: rgba(255,255,255,0.5);
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
    }

    .tab-btn:hover {
      background: rgba(255,255,255,0.03);
      color: rgba(255,255,255,0.7);
    }

    .tab-btn.active {
      background: rgba(56,189,248,0.1);
      color: #38bdf8;
      border-bottom: 2px solid #38bdf8;
    }

    .tab-content {
      background: var(--surface);
      border-radius: 0 0 8px 8px;
      padding: 20px;
      min-height: 400px;
    }

    .history-filters {
      display: flex;
      gap: 15px;
      align-items: center;
      margin-bottom: 20px;
    }

    .filter-input {
      flex: 1;
      padding: 10px 15px;
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 6px;
      color: rgba(255,255,255,0.9);
      font-size: 0.85rem;
    }

    .filter-input:focus {
      outline: none;
      border-color: rgba(56,189,248,0.4);
      background: rgba(255,255,255,0.05);
    }

    .result-count {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.4);
      font-weight: 600;
      white-space: nowrap;
    }

    .executions-table {
      overflow-x: auto;
    }

    table {
      width: 100%;
      border-collapse: collapse;
    }

    thead {
      background: rgba(255,255,255,0.02);
    }

    th {
      padding: 12px 15px;
      text-align: left;
      font-size: 0.75rem;
      font-weight: 700;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      border-bottom: 1px solid rgba(255,255,255,0.08);
    }

    td {
      padding: 12px 15px;
      font-size: 0.85rem;
      color: rgba(255,255,255,0.7);
      border-bottom: 1px solid rgba(255,255,255,0.04);
    }

    .exec-row:hover {
      background: rgba(255,255,255,0.02);
    }

    .graft-id-cell code {
      font-family: var(--font-mono);
      font-size: 0.8rem;
      color: rgba(255,255,255,0.6);
      background: rgba(255,255,255,0.05);
      padding: 3px 6px;
      border-radius: 4px;
    }

    .agent-badge {
      display: inline-block;
      padding: 4px 10px;
      background: rgba(139,92,246,0.15);
      border: 1px solid rgba(139,92,246,0.3);
      border-radius: 12px;
      font-size: 0.75rem;
      font-weight: 600;
      font-family: monospace;
      color: #a78bfa;
    }

    .status-badge {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 12px;
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .status-badge.status-completed {
      background: rgba(34,197,94,0.15);
      color: #22c55e;
      border: 1px solid rgba(34,197,94,0.3);
    }

    .status-badge.status-running {
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      border: 1px solid rgba(56,189,248,0.3);
    }

    .status-badge.status-failed {
      background: rgba(239,68,68,0.15);
      color: #ef4444;
      border: 1px solid rgba(239,68,68,0.3);
    }

    .status-badge.status-timeout {
      background: rgba(251,146,60,0.15);
      color: #fb923c;
      border: 1px solid rgba(251,146,60,0.3);
    }

    .status-badge.status-circuit_open {
      background: rgba(234,179,8,0.15);
      color: #eab308;
      border: 1px solid rgba(234,179,8,0.3);
    }

    .status-badge.status-pending {
      background: rgba(100,116,139,0.15);
      color: #64748b;
      border: 1px solid rgba(100,116,139,0.3);
    }

    .retry-count {
      display: inline-block;
      padding: 3px 8px;
      background: rgba(251,146,60,0.15);
      border-radius: 10px;
      font-size: 0.75rem;
      font-weight: 700;
      color: #fb923c;
    }

    .no-retries {
      color: rgba(255,255,255,0.2);
    }

    .in-progress {
      color: rgba(56,189,248,0.6);
      font-style: italic;
    }

    .view-artifact-btn, .view-error-btn {
      background: transparent;
      border: none;
      cursor: pointer;
      font-size: 1.1rem;
      padding: 4px 8px;
      opacity: 0.6;
      transition: opacity 0.2s;
    }

    .view-artifact-btn:hover, .view-error-btn:hover {
      opacity: 1;
    }

    .no-data {
      text-align: center;
      padding: 40px;
      color: rgba(255,255,255,0.3);
      font-style: italic;
    }

    .scheduler-content {
      display: flex;
      justify-content: center;
      align-items: flex-start;
    }

    .scheduler-form {
      max-width: 600px;
      width: 100%;
      padding: 30px;
    }

    .form-title {
      font-size: 1.2rem;
      font-weight: 700;
      color: rgba(255,255,255,0.9);
      margin: 0 0 25px 0;
    }

    .form-group {
      margin-bottom: 20px;
    }

    .form-group label {
      display: block;
      font-size: 0.8rem;
      font-weight: 600;
      color: rgba(255,255,255,0.6);
      margin-bottom: 8px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .form-input, .form-select {
      width: 100%;
      padding: 10px 15px;
      background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 6px;
      color: rgba(255,255,255,0.9);
      font-size: 0.9rem;
    }

    .form-input:focus, .form-select:focus {
      outline: none;
      border-color: rgba(56,189,248,0.4);
      background: rgba(255,255,255,0.05);
    }

    .agent-autocomplete {
      position: relative;
    }

    .agent-suggestions {
      position: absolute;
      top: 100%;
      left: 0;
      right: 0;
      margin-top: 4px;
      background: rgba(15,23,42,0.98);
      border: 1px solid rgba(56,189,248,0.3);
      border-radius: 6px;
      max-height: 200px;
      overflow-y: auto;
      z-index: 100;
    }

    .suggestion-item {
      padding: 10px 15px;
      cursor: pointer;
      font-size: 0.85rem;
      font-family: monospace;
      color: rgba(255,255,255,0.7);
      border-bottom: 1px solid rgba(255,255,255,0.05);
      transition: background 0.15s;
    }

    .suggestion-item:last-child {
      border-bottom: none;
    }

    .suggestion-item:hover {
      background: rgba(56,189,248,0.1);
      color: #38bdf8;
    }

    .form-actions {
      display: flex;
      gap: 10px;
      margin-top: 25px;
    }

    .schedule-btn {
      flex: 1;
      padding: 12px 24px;
      background: rgba(56,189,248,0.15);
      border: 1px solid rgba(56,189,248,0.4);
      border-radius: 6px;
      color: #38bdf8;
      font-size: 0.9rem;
      font-weight: 700;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.2s;
    }

    .schedule-btn:hover:not(:disabled) {
      background: rgba(56,189,248,0.25);
      border-color: #38bdf8;
    }

    .schedule-btn:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }

    .clear-btn {
      padding: 12px 24px;
      background: transparent;
      border: 1px solid rgba(255,255,255,0.15);
      border-radius: 6px;
      color: rgba(255,255,255,0.5);
      font-size: 0.9rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .clear-btn:hover {
      background: rgba(255,255,255,0.05);
      color: rgba(255,255,255,0.7);
    }

    .scheduler-message {
      margin-top: 15px;
      padding: 12px 15px;
      background: rgba(34,197,94,0.1);
      border: 1px solid rgba(34,197,94,0.3);
      border-radius: 6px;
      color: #22c55e;
      font-size: 0.85rem;
    }

    .scheduler-message.error {
      background: rgba(239,68,68,0.1);
      border-color: rgba(239,68,68,0.3);
      color: #ef4444;
    }

    .cb-dashboard {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
      gap: 20px;
    }

    .cb-card {
      padding: 20px;
    }

    .cb-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
    }

    .cb-agent {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .cb-state-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .cb-state-badge.state-closed {
      background: rgba(34,197,94,0.15);
      color: #22c55e;
      border: 1px solid rgba(34,197,94,0.3);
    }

    .cb-state-badge.state-open {
      background: rgba(239,68,68,0.15);
      color: #ef4444;
      border: 1px solid rgba(239,68,68,0.3);
    }

    .cb-state-badge.state-half_open {
      background: rgba(251,146,60,0.15);
      color: #fb923c;
      border: 1px solid rgba(251,146,60,0.3);
    }

    .reset-btn {
      padding: 6px 14px;
      background: rgba(239,68,68,0.1);
      border: 1px solid rgba(239,68,68,0.3);
      border-radius: 6px;
      color: #ef4444;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .reset-btn:hover {
      background: rgba(239,68,68,0.15);
      border-color: #ef4444;
    }

    .cb-metrics {
      display: flex;
      flex-direction: column;
      gap: 15px;
    }

    .metric {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .metric-label {
      font-size: 0.75rem;
      font-weight: 600;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .metric-value {
      font-size: 0.9rem;
      font-weight: 700;
      color: rgba(255,255,255,0.8);
    }

    .metric-split {
      display: flex;
      gap: 15px;
    }

    .success-count {
      color: #22c55e;
    }

    .failure-count {
      color: #ef4444;
    }

    .failure-badge {
      display: inline-block;
      padding: 4px 12px;
      background: rgba(239,68,68,0.15);
      border-radius: 12px;
      font-size: 0.85rem;
      font-weight: 700;
      color: #ef4444;
    }

    .time-value {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.6);
    }

    .failure-rate-bar {
      width: 200px;
      height: 8px;
      background: rgba(255,255,255,0.05);
      border-radius: 4px;
      overflow: hidden;
      margin-right: 10px;
      display: inline-block;
    }

    .failure-rate-fill {
      height: 100%;
      background: #22c55e;
      transition: width 0.3s, background 0.3s;
    }

    .failure-rate-fill.high {
      background: #ef4444;
    }

    .rate-text {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.6);
    }

    .cb-recent-failures {
      margin-top: 20px;
      padding-top: 20px;
      border-top: 1px solid rgba(255,255,255,0.08);
    }

    .failures-header {
      font-size: 0.75rem;
      font-weight: 700;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-bottom: 12px;
    }

    .failure-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .failure-item {
      padding: 10px;
      background: rgba(239,68,68,0.05);
      border-left: 3px solid rgba(239,68,68,0.4);
      border-radius: 4px;
    }

    .failure-graft-id {
      font-family: var(--font-mono);
      font-size: 0.75rem;
      color: rgba(255,255,255,0.5);
      margin-right: 10px;
    }

    .failure-time {
      font-size: 0.7rem;
      color: rgba(255,255,255,0.4);
    }

    .failure-msg {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.6);
      margin-top: 5px;
    }

    .glass-panel {
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 8px;
    }
  `]
})
export class GraftManagementComponent implements OnInit {
  @Input() runId?: string;

  activeTab = signal<'history' | 'scheduler' | 'circuit-breaker'>('history');
  
  executions = signal<GraftExecution[]>([]);
  filteredExecutions = signal<GraftExecution[]>([]);
  historyFilter = '';

  availableAgents = signal<string[]>([]);
  filteredAgents = signal<string[]>([]);
  showAgentSuggestions = false;

  circuitBreakerStatuses = signal<CircuitBreakerStatus[]>([]);

  schedulerForm = {
    runId: '',
    checkpoint: '',
    agentName: '',
    timeoutMs: 300000
  };

  schedulerMessage = '';
  schedulerError = false;

  constructor(
    private orchestrator: OrchestratorService,
    private route: ActivatedRoute
  ) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
      if (params['runId']) {
        this.runId = params['runId'];
        this.schedulerForm.runId = params['runId'];
      }
    });
    
    this.loadData();
    if (this.runId) {
      this.schedulerForm.runId = this.runId;
    }
  }

  loadData() {
    this.orchestrator.getGraftExecutions(this.runId).subscribe(execs => {
      this.executions.set(execs);
      this.filteredExecutions.set(execs);
    });

    this.orchestrator.getAvailableGraftAgents().subscribe(agents => {
      this.availableAgents.set(agents);
      this.filteredAgents.set(agents);
    });

    this.orchestrator.getCircuitBreakerStatus().subscribe(statuses => {
      this.circuitBreakerStatuses.set(statuses);
    });
  }

  refresh() {
    this.loadData();
  }

  filterExecutions() {
    const filter = this.historyFilter.toLowerCase();
    if (!filter) {
      this.filteredExecutions.set(this.executions());
      return;
    }

    const filtered = this.executions().filter(exec =>
      exec.graftId.toLowerCase().includes(filter) ||
      exec.runId.toLowerCase().includes(filter) ||
      exec.agentName.toLowerCase().includes(filter) ||
      exec.checkpointAfter.toLowerCase().includes(filter)
    );
    this.filteredExecutions.set(filtered);
  }

  filterAgents() {
    const filter = this.schedulerForm.agentName.toLowerCase();
    if (!filter) {
      this.filteredAgents.set(this.availableAgents());
      this.showAgentSuggestions = true;
      return;
    }

    const filtered = this.availableAgents().filter(agent =>
      agent.toLowerCase().includes(filter)
    );
    this.filteredAgents.set(filtered);
    this.showAgentSuggestions = true;
  }

  selectAgent(agent: string) {
    this.schedulerForm.agentName = agent;
    this.showAgentSuggestions = false;
  }

  canSchedule(): boolean {
    return !!(
      this.schedulerForm.runId &&
      this.schedulerForm.checkpoint &&
      this.schedulerForm.agentName
    );
  }

  scheduleGraft() {
    if (!this.canSchedule()) return;

    this.orchestrator.addGraft(
      this.schedulerForm.runId,
      this.schedulerForm.checkpoint,
      this.schedulerForm.agentName
    ).subscribe({
      next: () => {
        this.schedulerMessage = `Graft scheduled successfully for ${this.schedulerForm.agentName} after ${this.schedulerForm.checkpoint}`;
        this.schedulerError = false;
        this.clearSchedulerForm();
        setTimeout(() => this.schedulerMessage = '', 5000);
      },
      error: (err) => {
        this.schedulerMessage = `Failed to schedule graft: ${err.message}`;
        this.schedulerError = true;
      }
    });
  }

  clearSchedulerForm() {
    this.schedulerForm = {
      runId: this.runId || '',
      checkpoint: '',
      agentName: '',
      timeoutMs: 300000
    };
  }

  resetCircuit(agentName: string) {
    this.orchestrator.resetCircuitBreaker(agentName).subscribe(() => {
      this.loadData();
    });
  }

  getStatusClass(status: string): string {
    return 'status-' + status.toLowerCase();
  }

  getCircuitStateClass(state: string): string {
    return 'state-' + state.toLowerCase();
  }

  formatTime(timestamp: string): string {
    const date = new Date(timestamp);
    return date.toLocaleString();
  }

  calculateDuration(start: string, end: string): string {
    const startTime = new Date(start).getTime();
    const endTime = new Date(end).getTime();
    const durationMs = endTime - startTime;

    if (durationMs < 1000) return `${durationMs}ms`;
    if (durationMs < 60000) return `${(durationMs / 1000).toFixed(1)}s`;
    const minutes = Math.floor(durationMs / 60000);
    const seconds = Math.floor((durationMs % 60000) / 1000);
    return `${minutes}m ${seconds}s`;
  }

  truncate(text: string, maxLength: number): string {
    if (!text || text.length <= maxLength) return text;
    return text.substring(0, maxLength) + '...';
  }

  viewArtifact(artifactId: string) {
    console.log('View artifact:', artifactId);
  }

  viewError(exec: GraftExecution) {
    alert(`Error for graft ${exec.graftId}:\n\n${exec.errorMessage}`);
  }
}
