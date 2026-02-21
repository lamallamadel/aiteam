import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { OrchestratorService } from './services/orchestrator.service';
import { EscalationService } from './services/escalation.service';
import { Persona } from './models/orchestrator.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  protected readonly title = signal('Atlasia Orchestrator');
  personas: Persona[] = [];

  constructor(
    private orchestratorService: OrchestratorService,
    public escalationService: EscalationService
  ) { }

  ngOnInit() {
    this.orchestratorService.getPersonas().subscribe(
      p => this.personas = p
    );
  }
}
