import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ConversationListComponent } from './conversation-list.component';
import { ChatInterfaceComponent } from './chat-interface';
import { RunResponse } from '../models/orchestrator.model';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-chat-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, ConversationListComponent, ChatInterfaceComponent],
  template: `
    <div class="dashboard-wrapper">
      <app-conversation-list 
        *ngIf="!selectedPersona"
        (runSelected)="onRunSelected($event)"
        (newChatRequested)="onNewChat()">
      </app-conversation-list>
      
      <main class="chat-area">
        <div class="settings-bar">
          <button class="settings-btn" (click)="toggleSettings()" title="Authentication Settings">
            ⚙️
          </button>
        </div>

        <!-- Auth Modal -->
        <div *ngIf="showSettings" class="settings-modal-overlay">
          <div class="settings-modal glass-panel">
            <h3>Orchestrator Auth</h3>
            <p>Enter your GitHub token to authorize orchestration runs.</p>
            <input type="password" [(ngModel)]="tokenInput" placeholder="ghp_xxxxxxxxxxxx" class="glass-panel">
            <div class="modal-actions">
              <button class="cancel-btn" (click)="toggleSettings()">Cancel</button>
              <button class="save-btn accent-gradient" (click)="saveToken()">Save Token</button>
            </div>
          </div>
        </div>

        <app-chat-interface 
            [selectedRun]="selectedRun" 
            [selectedPersona]="selectedPersona">
        </app-chat-interface>
      </main>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; flex: 1; min-height: 0; width: 100%; }
    .dashboard-wrapper { display: flex; flex: 1; min-height: 0; overflow: hidden; }
    .chat-area { flex: 1; position: relative; display: flex; flex-direction: column; }
    .settings-bar { position: absolute; top: 20px; right: 20px; z-index: 10; }
    .settings-btn { background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1); border-radius: 50%; width: 40px; height: 40px; cursor: pointer; color: white; display: flex; align-items: center; justify-content: center; font-size: 1.2rem; transition: background 0.2s; }
    .settings-btn:hover { background: rgba(255,255,255,0.1); }
    
    .settings-modal-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; z-index: 100; backdrop-filter: blur(5px); }
    .settings-modal { width: 400px; padding: 30px; display: flex; flex-direction: column; gap: 15px; }
    .settings-modal h3 { margin: 0; color: #38bdf8; }
    .settings-modal p { margin: 0; font-size: 0.9rem; color: #94a3b8; line-height: 1.4; }
    .settings-modal input { padding: 12px; border: none; border-radius: 8px; width: 100%; color: white; outline: none; }
    .modal-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 10px; }
    .modal-actions button { padding: 10px 20px; border-radius: 8px; border: none; cursor: pointer; font-weight: 600; }
    .cancel-btn { background: rgba(255,255,255,0.05); color: white; }
    .save-btn { color: white; }
  `]
})
export class ChatDashboardComponent implements OnInit {
  selectedRun?: RunResponse;
  selectedPersona?: string;
  showSettings = false;
  tokenInput = '';

  constructor(private authService: AuthService, private route: ActivatedRoute) { }

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.selectedPersona = params['persona'];
      if (this.selectedPersona) {
        this.selectedRun = undefined;
      }
    });
    this.tokenInput = this.authService.getToken() || '';
    if (!this.authService.hasToken()) {
      this.showSettings = true;
    }
  }

  onRunSelected(run: RunResponse) {
    this.selectedRun = run;
  }

  onNewChat() {
    this.selectedRun = undefined;
  }

  toggleSettings() {
    this.showSettings = !this.showSettings;
  }

  saveToken() {
    this.authService.setToken(this.tokenInput);
    this.showSettings = false;
    // Reload components if needed, or rely on interceptor for next call
    window.location.reload();
  }
}
