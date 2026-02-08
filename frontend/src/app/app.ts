import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { ChatInterfaceComponent } from './components/chat-interface';
import { AnalyticsDashboardComponent } from './components/analytics-dashboard';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, ChatInterfaceComponent, AnalyticsDashboardComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('Atlasia Orchestrator');
}
