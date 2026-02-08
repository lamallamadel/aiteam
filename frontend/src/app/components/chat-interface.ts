import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrchestratorService } from '../services/orchestrator.service';
import { RunResponse, RunRequest } from '../models/orchestrator.model';

interface Message {
  role: 'user' | 'assistant';
  text: string;
  orchestrationStep?: string;
  timestamp: string;
}

@Component({
  selector: 'app-chat-interface',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="chat-wrapper">
      <!-- New Run Form -->
      <div *ngIf="!selectedRun" class="new-run-form glass-panel">
        <h2>Start New Orchestration</h2>
        <div class="form-group">
          <label>Repository</label>
          <input type="text" [(ngModel)]="newRequest.repo" placeholder="owner/repo">
        </div>
        <div class="form-group">
          <label>Issue Number</label>
          <input type="number" [(ngModel)]="newRequest.issueNumber">
        </div>
        <div class="form-group">
          <label>Mode</label>
          <select [(ngModel)]="newRequest.mode">
            <option value="PLANNING">Planning</option>
            <option value="EXECUTION">Execution</option>
          </select>
        </div>
        <button class="accent-gradient start-btn" (click)="startRun()">Launch Orchestrator</button>
      </div>

      <!-- Messages List -->
      <div *ngIf="selectedRun" class="message-list">
        <div *ngFor="let msg of messages" class="message" [ngClass]="msg.role">
          <div class="avatar">{{ msg.role === 'user' ? 'U' : 'AI' }}</div>
          <div class="bubble">
            <p>{{ msg.text }}</p>
            <div *ngIf="msg.orchestrationStep" class="step-indicator">
              <span class="pulse"></span> Executing: {{ msg.orchestrationStep }}
            </div>
          </div>
        </div>
      </div>

      <!-- Input Area (Only for active runs or feedback) -->
      <div *ngIf="selectedRun" class="input-area">
        <input type="text" placeholder="Type feedback or command..." class="glass-panel" [(ngModel)]="feedbackText">
        <button class="accent-gradient" (click)="sendFeedback()">Send</button>
      </div>
    </div>
  `,
  styles: [`
    .chat-wrapper { height: 100%; display: flex; flex-direction: column; padding: 20px; background: rgba(0,0,0,0.2); }
    .new-run-form { max-width: 500px; margin: auto; padding: 40px; display: flex; flex-direction: column; gap: 20px; }
    .new-run-form h2 { margin: 0; color: #38bdf8; text-align: center; }
    .form-group { display: flex; flex-direction: column; gap: 8px; }
    .form-group label { color: #94a3b8; font-size: 0.9rem; }
    .form-group input, .form-group select { padding: 12px; border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.05); color: white; border-radius: 8px; outline: none; }
    .start-btn { padding: 16px; margin-top: 20px; font-size: 1.1rem; }
    
    .message-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 16px; padding-bottom: 20px; }
    .message { display: flex; gap: 12px; }
    .message.user { flex-direction: row-reverse; }
    .bubble { padding: 12px 16px; border-radius: 12px; max-width: 80%; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.05); }
    .message.user .bubble { background: #38bdf8; color: white; border: none; }
    .step-indicator { margin-top: 8px; font-size: 0.8rem; color: #38bdf8; display: flex; align-items: center; gap: 8px; }
    .pulse { width: 8px; height: 8px; background: #38bdf8; border-radius: 50%; animation: pulse 1.5s infinite; }
    @keyframes pulse { 0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0.7); } 70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(56, 189, 248, 0); } 100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0); } }
    
    .input-area { margin-top: 20px; display: flex; gap: 12px; }
    input { flex: 1; padding: 12px; color: white; border: none; outline: none; border-radius: 8px; }
    button { padding: 12px 24px; border: none; border-radius: 8px; color: white; cursor: pointer; font-weight: bold; transition: opacity 0.2s; }
    button:hover { opacity: 0.9; }
  `]
})
export class ChatInterfaceComponent implements OnChanges {
  @Input() selectedRun?: RunResponse;

  messages: Message[] = [];
  newRequest: RunRequest = { repo: 'lamallamadel/orchistrateur', issueNumber: 1, mode: 'PLANNING' };
  feedbackText: string = '';

  constructor(private orchestratorService: OrchestratorService) { }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['selectedRun'] && this.selectedRun) {
      this.loadMessages();
    } else if (!this.selectedRun) {
      this.messages = [];
    }
  }

  loadMessages() {
    if (!this.selectedRun) return;

    this.orchestratorService.getArtifacts(this.selectedRun.id).subscribe((artifacts: any[]) => {
      this.messages = artifacts
        .filter((a: any) => a.artifactType === 'log' || a.artifactType === 'step') // Simplification
        .map((a: any) => ({
          role: 'assistant',
          text: a.payload,
          orchestrationStep: a.agentName,
          timestamp: a.createdAt
        }));

      // Inject user's starting message
      this.messages.unshift({
        role: 'user',
        text: `Starting orchestration for ${this.selectedRun?.repo} #${this.selectedRun?.issueNumber}`,
        timestamp: this.selectedRun!.createdAt
      });
    });
  }

  startRun() {
    this.orchestratorService.createRun(this.newRequest).subscribe((run: RunResponse) => {
      this.selectedRun = run;
      this.loadMessages();
    });
  }

  sendFeedback() {
    if (!this.feedbackText) return;
    // For now, just a mock feedback
    this.messages.push({
      role: 'user',
      text: this.feedbackText,
      timestamp: new Date().toISOString()
    });
    this.feedbackText = '';
  }
}
