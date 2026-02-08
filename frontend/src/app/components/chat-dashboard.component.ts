import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConversationListComponent } from './conversation-list.component';
import { ChatInterfaceComponent } from './chat-interface';
import { RunResponse } from '../models/orchestrator.model';

@Component({
    selector: 'app-chat-dashboard',
    standalone: true,
    imports: [CommonModule, ConversationListComponent, ChatInterfaceComponent],
    template: `
    <div class="dashboard-wrapper">
      <app-conversation-list 
        (runSelected)="onRunSelected($event)"
        (newChatRequested)="onNewChat()">
      </app-conversation-list>
      
      <main class="chat-area">
        <app-chat-interface [selectedRun]="selectedRun"></app-chat-interface>
      </main>
    </div>
  `,
    styles: [`
    .dashboard-wrapper { display: flex; height: calc(100vh - 80px); overflow: hidden; }
    .chat-area { flex: 1; position: relative; }
  `]
})
export class ChatDashboardComponent {
    selectedRun?: RunResponse;

    onRunSelected(run: RunResponse) {
        this.selectedRun = run;
    }

    onNewChat() {
        this.selectedRun = undefined;
    }
}
