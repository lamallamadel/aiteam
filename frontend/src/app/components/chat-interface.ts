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
      <!-- Chat Header -->
      <header class="chat-header glass-panel" *ngIf="selectedRun || selectedPersona">
        <div class="header-info">
          <span class="header-icon" [ngClass]="selectedPersona ? 'gem' : 'run'">
            {{ selectedPersona ? '💎' : '🚀' }}
          </span>
          <div class="header-text">
            <h3>{{ selectedPersona ? (selectedPersona | uppercase) : 'Orchestration Run' }}</h3>
            <p *ngIf="selectedRun">{{ selectedRun.repo }} #{{ selectedRun.issueNumber }}</p>
            <p *ngIf="selectedPersona">Interactive AI Gem Expertise</p>
          </div>
        </div>
        <div class="header-actions">
            <span class="status-badge" [ngClass]="selectedPersona ? 'gem' : 'run'">
                {{ selectedPersona ? 'Direct Chat' : selectedRun?.status }}
            </span>
        </div>
      </header>

      <!-- New Run Form -->
      <div *ngIf="!selectedRun && !selectedPersona" class="new-run-form glass-panel">
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
      <div *ngIf="selectedRun || selectedPersona" class="message-list">
        <div *ngFor="let msg of messages" class="message" [ngClass]="msg.role">
          <div class="avatar">{{ msg.role === 'user' ? 'U' : (selectedPersona ? 'G' : 'AI') }}</div>
          <div class="bubble">
            <div class="message-text">{{ msg.text }}</div>
            <div *ngIf="msg.orchestrationStep" class="step-indicator">
              <span class="pulse"></span> Executing: {{ msg.orchestrationStep }}
            </div>
            <div class="timestamp">{{ msg.timestamp | date:'shortTime' }}</div>
          </div>
        </div>
        <div *ngIf="isTyping" class="message assistant typing">
          <div class="avatar">G</div>
          <div class="bubble typing-bubble">
            <div class="typing-indicator"><span></span><span></span><span></span></div>
          </div>
        </div>
      </div>

      <!-- Input Area -->
      <div *ngIf="selectedRun || selectedPersona" class="input-area">
        <input type="text" placeholder="Ask anything or provide feedback..." class="glass-panel" 
               [(ngModel)]="feedbackText" (keyup.enter)="sendFeedback()">
        <button class="accent-gradient" [disabled]="isTyping" (click)="sendFeedback()">
            {{ isTyping ? 'Thinking...' : 'Send' }}
        </button>
      </div>
    </div>
  `,
  styles: [`
    .chat-wrapper { height: 100%; display: flex; flex-direction: column; padding: 20px; background: rgba(0,0,0,0.1); border-radius: 12px; }
    
    .chat-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; margin-bottom: 20px; border-bottom: 1px solid rgba(255,255,255,0.05); }
    .header-info { display: flex; align-items: center; gap: 16px; }
    .header-icon { width: 48px; height: 48px; border-radius: 12px; background: rgba(255,255,255,0.05); display: flex; align-items: center; justify-content: center; font-size: 1.5rem; }
    .header-icon.gem { background: rgba(56, 189, 248, 0.1); border: 1px solid rgba(56, 189, 248, 0.2); }
    .header-text h3 { margin: 0; font-size: 1.1rem; color: #f8fafc; }
    .header-text p { margin: 2px 0 0; font-size: 0.85rem; color: #94a3b8; }
    .status-badge { padding: 4px 12px; border-radius: 20px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; background: rgba(255,255,255,0.05); color: #94a3b8; }
    .status-badge.gem { background: #38bdf8; color: white; }
    
    .new-run-form { max-width: 500px; margin: auto; padding: 40px; display: flex; flex-direction: column; gap: 20px; width: 100%; }
    .new-run-form h2 { margin: 0; color: #38bdf8; text-align: center; font-size: 1.5rem; }
    .form-group { display: flex; flex-direction: column; gap: 8px; }
    .form-group label { color: #94a3b8; font-size: 0.9rem; font-weight: 500; }
    .form-group input, .form-group select { padding: 12px; border: 1px solid rgba(255,255,255,0.1); background: rgba(0,0,0,0.2); color: white; border-radius: 8px; outline: none; transition: border-color 0.2s; }
    .form-group input:focus { border-color: #38bdf8; }
    .start-btn { padding: 16px; margin-top: 20px; font-size: 1.1rem; }
    
    .message-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 20px; padding: 10px; }
    .message { display: flex; gap: 12px; max-width: 85%; }
    .message.user { align-self: flex-end; flex-direction: row-reverse; }
    .avatar { width: 36px; height: 36px; border-radius: 50%; background: #1e293b; display: flex; align-items: center; justify-content: center; font-size: 0.8rem; font-weight: bold; color: #38bdf8; flex-shrink: 0; }
    .message.user .avatar { background: #38bdf8; color: white; }
    
    .bubble { padding: 12px 16px; border-radius: 12px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.05); position: relative; }
    .message.user .bubble { background: #0ea5e9; color: white; border: none; border-bottom-right-radius: 2px; }
    .message.assistant .bubble { border-bottom-left-radius: 2px; }
    .message-text { line-height: 1.5; white-space: pre-wrap; font-size: 0.95rem; }
    .timestamp { font-size: 0.7rem; color: #64748b; margin-top: 6px; text-align: right; }
    .message.user .timestamp { color: rgba(255,255,255,0.7); }
    
    .step-indicator { margin-top: 10px; padding: 8px; background: rgba(56, 189, 248, 0.1); border-radius: 6px; font-size: 0.8rem; color: #38bdf8; display: flex; align-items: center; gap: 8px; }
    .pulse { width: 8px; height: 8px; background: #38bdf8; border-radius: 50%; animation: pulse 1.5s infinite; }
    @keyframes pulse { 0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0.7); } 70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(56, 189, 248, 0); } 100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0); } }
    
    .typing-bubble { padding: 12px 20px; }
    .typing-indicator { display: flex; gap: 4px; }
    .typing-indicator span { width: 6px; height: 6px; background: #64748b; border-radius: 50%; animation: bounce 1.4s infinite ease-in-out both; }
    .typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
    .typing-indicator span:nth-child(2) { animation-delay: -0.16s; }
    @keyframes bounce { 0%, 80%, 100% { transform: scale(0); } 40% { transform: scale(1.0); } }
    
    .input-area { margin-top: 20px; display: flex; gap: 12px; padding: 12px; background: rgba(0,0,0,0.2); border-radius: 12px; }
    .input-area input { flex: 1; padding: 12px; background: transparent; border: none; color: white; outline: none; font-size: 0.95rem; }
    .input-area button { padding: 0 24px; border: none; border-radius: 8px; color: white; cursor: pointer; font-weight: 600; transition: all 0.2s; }
    .input-area button:disabled { opacity: 0.5; cursor: not-allowed; }
  `]
})
export class ChatInterfaceComponent implements OnChanges {
  @Input() selectedRun?: RunResponse;
  @Input() selectedPersona?: string;

  messages: Message[] = [];
  newRequest: RunRequest = { repo: 'lamallamadel/orchistrateur', issueNumber: 1, mode: 'PLANNING' };
  feedbackText: string = '';
  isTyping = false;

  constructor(private orchestratorService: OrchestratorService) { }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['selectedRun'] && this.selectedRun) {
      this.selectedPersona = undefined;
      this.loadMessages();
    } else if (changes['selectedPersona'] && this.selectedPersona) {
      this.selectedRun = undefined;
      this.initPersonaChat();
    } else if (!this.selectedRun && !this.selectedPersona) {
      this.messages = [];
    }
  }

  initPersonaChat() {
    this.messages = [{
      role: 'assistant',
      text: `Hello! I am ${this.selectedPersona?.toUpperCase()}, your specialized AI Gem. How can I help you today?`,
      timestamp: new Date().toISOString()
    }];
  }

  loadMessages() {
    if (!this.selectedRun) return;

    this.orchestratorService.getArtifacts(this.selectedRun.id).subscribe((artifacts: any[]) => {
      this.messages = artifacts
        .filter((a: any) => a.artifactType === 'log' || a.artifactType === 'step')
        .map((a: any) => ({
          role: 'assistant',
          text: a.payload,
          orchestrationStep: a.agentName,
          timestamp: a.createdAt
        }));

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

    const userMsg: Message = {
      role: 'user',
      text: this.feedbackText,
      timestamp: new Date().toISOString()
    };
    this.messages.push(userMsg);
    const textToChat = this.feedbackText;
    this.feedbackText = '';

    if (this.selectedPersona) {
      this.isTyping = true;
      this.orchestratorService.chat(this.selectedPersona, textToChat).subscribe(res => {
        this.isTyping = false;
        this.messages.push({
          role: 'assistant',
          text: res.response,
          timestamp: new Date().toISOString()
        });
      });
    }
  }
}
