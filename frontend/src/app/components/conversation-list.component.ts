import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { RunResponse, Persona } from '../models/orchestrator.model';

@Component({
  selector: 'app-conversation-list',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="conv-list-container glass-panel">
      <div class="header">
        <h3>Conversations</h3>
        <button class="new-btn" (click)="onNewChat()">+</button>
      </div>
      <div class="list">
        <div *ngFor="let run of runs" 
             class="conv-item" 
             [class.active]="selectedRunId === run.id"
             (click)="selectRun(run)">
          <div class="status-dot" [ngClass]="run.status.toLowerCase()"></div>
          <div class="conv-info">
            <div class="repo">{{ run.repo }} #{{ run.issueNumber }}</div>
            <div class="date">{{ run.createdAt | date:'short' }}</div>
          </div>
        </div>
      </div>

      <div class="header section-header">
        <h3>AI Gems</h3>
      </div>
      <div class="list gem-list">
        <div *ngFor="let persona of personas" 
             class="conv-item gem-item" 
             (click)="selectPersona(persona)">
          <div class="gem-icon">💎</div>
          <div class="conv-info">
            <div class="repo">{{ persona.name | uppercase }}</div>
            <div class="date">{{ persona.role }}</div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; flex-shrink: 0; }
    .conv-list-container { width: 300px; height: 100%; display: flex; flex-direction: column; border-right: 1px solid rgba(255,255,255,0.1); overflow: hidden; }
    .header { padding: 20px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid rgba(255,255,255,0.05); flex-shrink: 0; }
    .header h3 { margin: 0; color: white; font-size: 1rem; }
    .section-header { margin-top: 10px; border-top: 1px solid rgba(255,255,255,0.05); }
    .new-btn { background: #38bdf8; border: none; border-radius: 50%; width: 32px; height: 32px; color: white; cursor: pointer; font-weight: bold; }
    .list { flex: 1; overflow-y: auto; padding: 10px; }
    .gem-list { flex: 0 1 auto; max-height: 40%; }
    .conv-item { padding: 12px; border-radius: 8px; margin-bottom: 8px; cursor: pointer; display: flex; gap: 12px; align-items: center; transition: background 0.2s; }
    .conv-item:hover { background: rgba(255,255,255,0.05); }
    .conv-item.active { background: rgba(56, 189, 248, 0.1); border-left: 3px solid #38bdf8; }
    .status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
    .status-dot.done { background: #22c55e; box-shadow: 0 0 8px rgba(34, 197, 94, 0.5); }
    .status-dot.failed { background: #ef4444; box-shadow: 0 0 8px rgba(239, 68, 68, 0.5); }
    .status-dot.in_progress, .status-dot.received { background: #38bdf8; box-shadow: 0 0 8px rgba(56, 189, 248, 0.5); }
    .gem-icon { font-size: 1.2rem; flex-shrink: 0; }
    .conv-info { flex: 1; min-width: 0; }
    .repo { color: white; font-weight: 500; font-size: 0.9rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .date { color: #94a3b8; font-size: 0.75rem; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  `]
})
export class ConversationListComponent implements OnInit {
  runs: RunResponse[] = [];
  personas: Persona[] = [];
  selectedRunId?: string;

  @Output() runSelected = new EventEmitter<RunResponse>();
  @Output() newChatRequested = new EventEmitter<void>();

  constructor(
    private orchestratorService: OrchestratorService,
    private router: Router
  ) { }

  ngOnInit() {
    this.loadRuns();
    this.loadPersonas();
  }

  loadRuns() {
    this.orchestratorService.getRuns().subscribe((runs: RunResponse[]) => {
      this.runs = runs;
    });
  }

  loadPersonas() {
    this.orchestratorService.getPersonas().subscribe((personas: Persona[]) => {
      this.personas = personas;
    });
  }

  selectRun(run: RunResponse) {
    this.selectedRunId = run.id;
    this.runSelected.emit(run);
    this.router.navigate(['/chat', run.id]);
  }

  selectPersona(persona: Persona) {
    this.selectedRunId = undefined;
    this.router.navigate(['/chat/gem', persona.name]);
  }

  onNewChat() {
    this.selectedRunId = undefined;
    this.newChatRequested.emit();
    this.router.navigate(['/chat']);
  }
}
