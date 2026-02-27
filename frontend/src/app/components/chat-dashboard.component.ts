import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ConversationListComponent } from './conversation-list.component';
import { ChatInterfaceComponent } from './chat-interface';
import { RunResponse } from '../models/orchestrator.model';
import { SettingsService } from '../services/settings.service';

@Component({
  selector: 'app-chat-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, ConversationListComponent, ChatInterfaceComponent],
  template: `
    <div class="dashboard-wrapper">
      @if (!selectedPersona) {
        <app-conversation-list 
          (runSelected)="onRunSelected($event)"
          (newChatRequested)="onNewChat()">
        </app-conversation-list>
      }
      
      <main class="chat-area">
        <div class="settings-bar">
          <button class="settings-btn" (click)="navigateToSettings()" title="Settings">
            ⚙️
          </button>
        </div>

        <app-chat-interface 
            [selectedRun]="selectedRun" 
            [selectedPersona]="selectedPersona">
        </app-chat-interface>
      </main>
    </div>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; flex: 1; min-height: 0; width: 100%; overflow: hidden; }
    .dashboard-wrapper { display: flex; flex: 1; min-height: 0; overflow: hidden; }
    .chat-area { flex: 1; position: relative; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
    .settings-bar { position: absolute; top: 20px; right: 20px; z-index: 10; }
    .settings-btn { background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1); border-radius: 50%; width: 40px; height: 40px; cursor: pointer; color: white; display: flex; align-items: center; justify-content: center; font-size: 1.2rem; transition: background 0.2s; }
    .settings-btn:hover { background: rgba(255,255,255,0.1); }
  `]
})
export class ChatDashboardComponent implements OnInit {
  selectedRun?: RunResponse;
  selectedPersona?: string;

  constructor(
    private settingsService: SettingsService, 
    private route: ActivatedRoute,
    private router: Router
  ) { }

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.selectedPersona = params['persona'];
      if (this.selectedPersona) {
        this.selectedRun = undefined;
      }
    });
  }

  onRunSelected(run: RunResponse) {
    this.selectedRun = run;
  }

  onNewChat() {
    this.selectedRun = undefined;
  }

  navigateToSettings() {
    this.router.navigate(['/settings']);
  }
}
