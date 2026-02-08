import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-chat-interface',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="chat-wrapper">
      <div class="message-list">
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
      <div class="input-area">
        <input type="text" placeholder="Type a command to the orchestrator..." class="glass-panel">
        <button class="accent-gradient">Send</button>
      </div>
    </div>
  `,
    styles: [`
    .chat-wrapper { height: 100%; display: flex; flex-direction: column; padding: 20px; }
    .message-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 16px; }
    .message { display: flex; gap: 12px; }
    .message.user { flex-direction: row-reverse; }
    .bubble { padding: 12px 16px; border-radius: 12px; max-width: 80%; background: rgba(255,255,255,0.05); }
    .message.user .bubble { background: #38bdf8; color: white; }
    .step-indicator { margin-top: 8px; font-size: 0.8rem; color: #38bdf8; display: flex; align-items: center; gap: 8px; }
    .pulse { width: 8px; height: 8px; background: #38bdf8; border-radius: 50%; animation: pulse 1.5s infinite; }
    @keyframes pulse { 0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0.7); } 70% { transform: scale(1); box-shadow: 0 0 0 10px rgba(56, 189, 248, 0); } 100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(56, 189, 248, 0); } }
    .input-area { margin-top: 20px; display: flex; gap: 12px; }
    input { flex: 1; padding: 12px; color: white; border: none; outline: none; }
    button { padding: 12px 24px; border: none; border-radius: 8px; color: white; cursor: pointer; font-weight: bold; }
  `]
})
export class ChatInterfaceComponent {
    messages = [
        { role: 'assistant', text: 'Hello! I am ready to orchestrate your development task. What should we build today?' },
        { role: 'user', text: 'Add analytics to the dashboard' },
        { role: 'assistant', text: 'Starting orchestration...', orchestrationStep: 'PmStep' }
    ];
}
