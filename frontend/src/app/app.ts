import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { OrchestratorService } from './services/orchestrator.service';
import { EscalationService } from './services/escalation.service';
import { OversightInboxService } from './services/oversight-inbox.service';
import { AuthService } from './services/auth.service';
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
  userMenuOpen = signal(false);

  constructor(
    private orchestratorService: OrchestratorService,
    public escalationService: EscalationService,
    public oversightInboxService: OversightInboxService,
    public authService: AuthService
  ) { }

  ngOnInit() {
    this.orchestratorService.getPersonas().subscribe(
      p => {
        setTimeout(() => {
          this.personas = p;
        });
      }
    );
  }

  toggleUserMenu() {
    this.userMenuOpen.set(!this.userMenuOpen());
  }

  handleLogout() {
    this.authService.logout().subscribe();
  }

  getAvatarLetter(): string {
    const username = this.authService.currentUser()?.username;
    return username ? username.charAt(0).toUpperCase() : '?';
  }

  getUsername(): string {
    return this.authService.currentUser()?.username || 'User';
  }
}
