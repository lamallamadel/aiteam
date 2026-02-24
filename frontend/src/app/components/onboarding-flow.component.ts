import { Component, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SettingsService } from '../services/settings.service';
import { AuthService } from '../services/auth.service';

interface AgentRole {
  id: string;
  name: string;
  icon: string;
  description: string;
  focusAreas: string[];
}

@Component({
  selector: 'app-onboarding-flow',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="onboarding-wrapper">
      <div class="onboarding-container">
        <!-- Step 1: Welcome Message -->
        <div *ngIf="currentStep() === 1" class="chat-step">
          <div class="message-block assistant">
            <div class="message-header">
              <span class="message-sender">ATLASIA ORCHESTRATOR</span>
              <span class="message-timestamp">{{ timestamp }}</span>
            </div>
            <div class="message-body">
              <div class="message-text" [innerHTML]="typedText()"></div>
            </div>
          </div>

          <div class="cta-section" *ngIf="isTypingComplete()">
            <button class="primary-btn" (click)="nextStep()">Get Started →</button>
          </div>
        </div>

        <!-- Step 2: Git Provider Connection -->
        <div *ngIf="currentStep() === 2" class="chat-step">
          <div class="message-block assistant">
            <div class="message-header">
              <span class="message-sender">ATLASIA ORCHESTRATOR</span>
              <span class="message-timestamp">{{ timestamp }}</span>
            </div>
            <div class="message-body">
              <div class="message-text">
                To get started, I'll need access to your code repository. Please connect your Git provider.
              </div>
            </div>
          </div>

          <div class="form-section">
            <div class="form-group">
              <label>Git Provider</label>
              <div class="provider-selector">
                <button 
                  *ngFor="let provider of gitProviders"
                  class="provider-btn"
                  [class.selected]="selectedProvider() === provider.id"
                  (click)="selectProvider(provider.id)">
                  <span class="provider-icon">{{ provider.icon }}</span>
                  <span class="provider-name">{{ provider.name }}</span>
                </button>
              </div>
            </div>

            <div class="form-group">
              <label>Access Token</label>
              <input 
                type="password" 
                [ngModel]="gitToken()"
                (ngModelChange)="gitToken.set($event)"
                placeholder="Enter your personal access token"
                class="input-field">
              <span class="input-hint">
                This token will be stored securely in your browser's local storage.
              </span>
            </div>

            <div class="form-group" *ngIf="selectedProvider() === 'custom'">
              <label>Custom Git URL (Optional)</label>
              <input 
                type="text" 
                [ngModel]="customGitUrl()"
                (ngModelChange)="customGitUrl.set($event)"
                placeholder="https://git.example.com"
                class="input-field">
            </div>

            <div class="cta-section">
              <button class="secondary-btn" (click)="skipProvider()">Skip for Now</button>
              <button 
                class="primary-btn" 
                [disabled]="!canProceedFromProvider()"
                (click)="saveProviderAndNext()">
                Continue →
              </button>
            </div>
          </div>
        </div>

        <!-- Step 3: Agent Role Selection -->
        <div *ngIf="currentStep() === 3" class="chat-step">
          <div class="message-block assistant">
            <div class="message-header">
              <span class="message-sender">ATLASIA ORCHESTRATOR</span>
              <span class="message-timestamp">{{ timestamp }}</span>
            </div>
            <div class="message-body">
              <div class="message-text">
                Almost there! Choose your default AI agent role. This determines how the system approaches tasks, but you can always change it later or select different roles per task.
              </div>
            </div>
          </div>

          <div class="role-selection-section">
            <div class="role-grid">
              <div 
                *ngFor="let role of agentRoles"
                class="role-card"
                [class.selected]="selectedRole() === role.id"
                (click)="selectRole(role.id)">
                <div class="role-header">
                  <span class="role-icon">{{ role.icon }}</span>
                  <h3 class="role-name">{{ role.name }}</h3>
                </div>
                <p class="role-description">{{ role.description }}</p>
                <div class="role-focus">
                  <div class="focus-label">Focus Areas:</div>
                  <div class="focus-tags">
                    <span 
                      *ngFor="let area of role.focusAreas" 
                      class="focus-tag">
                      {{ area }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <div class="cta-section">
              <button class="secondary-btn" (click)="skipRole()">Skip for Now</button>
              <button 
                class="primary-btn" 
                [disabled]="!selectedRole()"
                (click)="completeOnboarding()">
                Complete Setup →
              </button>
            </div>
          </div>
        </div>

        <!-- Progress Indicator -->
        <div class="progress-bar">
          <div class="progress-step" [class.active]="currentStep() >= 1" [class.complete]="currentStep() > 1">
            <div class="step-dot"></div>
            <span class="step-label">Welcome</span>
          </div>
          <div class="progress-line" [class.complete]="currentStep() > 1"></div>
          <div class="progress-step" [class.active]="currentStep() >= 2" [class.complete]="currentStep() > 2">
            <div class="step-dot"></div>
            <span class="step-label">Git Setup</span>
          </div>
          <div class="progress-line" [class.complete]="currentStep() > 2"></div>
          <div class="progress-step" [class.active]="currentStep() >= 3">
            <div class="step-dot"></div>
            <span class="step-label">Agent Role</span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .onboarding-wrapper {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: var(--background);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 20px;
      overflow-y: auto;
    }

    .onboarding-container {
      width: 100%;
      max-width: 900px;
      padding: 40px;
      display: flex;
      flex-direction: column;
      gap: 32px;
    }

    .chat-step {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .message-block {
      display: flex;
      flex-direction: column;
      background: var(--surface);
      border-left: 3px solid #8b5cf6;
      padding: 20px 24px;
      border-radius: 8px;
    }

    .message-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding-bottom: 12px;
      border-bottom: 1px solid var(--border);
    }

    .message-sender {
      font-size: 0.7rem;
      font-weight: 800;
      color: #38bdf8;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      font-family: var(--font-mono);
    }

    .message-timestamp {
      font-size: 0.7rem;
      color: #64748b;
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
    }

    .message-body {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .message-text {
      line-height: 1.8;
      font-size: 1.1rem;
      color: #e2e8f0;
      white-space: pre-wrap;
    }

    .message-text h2 {
      margin: 0 0 12px 0;
      color: #38bdf8;
      font-size: 1.5rem;
    }

    .message-text ul {
      margin: 12px 0;
      padding-left: 24px;
    }

    .message-text li {
      margin: 8px 0;
      color: #94a3b8;
    }

    .form-section {
      background: var(--surface);
      padding: 24px;
      border-radius: 8px;
      border: 1px solid var(--border);
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .form-group label {
      color: #94a3b8;
      font-size: 0.9rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .provider-selector {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
      gap: 12px;
    }

    .provider-btn {
      padding: 16px;
      background: rgba(255, 255, 255, 0.03);
      border: 2px solid var(--border);
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      color: #94a3b8;
    }

    .provider-btn:hover {
      border-color: rgba(56, 189, 248, 0.5);
      background: rgba(56, 189, 248, 0.05);
    }

    .provider-btn.selected {
      border-color: #38bdf8;
      background: rgba(56, 189, 248, 0.1);
      color: #38bdf8;
    }

    .provider-icon {
      font-size: 2rem;
    }

    .provider-name {
      font-size: 0.85rem;
      font-weight: 600;
      font-family: var(--font-mono);
    }

    .input-field {
      padding: 14px 16px;
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: white;
      font-size: 0.95rem;
      outline: none;
      transition: border-color 0.2s;
    }

    .input-field:focus {
      border-color: #38bdf8;
    }

    .input-field::placeholder {
      color: #64748b;
    }

    .input-hint {
      font-size: 0.75rem;
      color: #64748b;
      font-style: italic;
    }

    .role-selection-section {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .role-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
    }

    .role-card {
      background: var(--surface);
      border: 2px solid var(--border);
      border-radius: 12px;
      padding: 24px;
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .role-card:hover {
      border-color: rgba(56, 189, 248, 0.5);
      background: rgba(56, 189, 248, 0.05);
      transform: translateY(-2px);
    }

    .role-card.selected {
      border-color: #38bdf8;
      background: rgba(56, 189, 248, 0.1);
      box-shadow: 0 0 20px rgba(56, 189, 248, 0.2);
    }

    .role-header {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .role-icon {
      font-size: 2rem;
    }

    .role-name {
      margin: 0;
      color: white;
      font-size: 1.125rem;
      font-weight: 600;
    }

    .role-description {
      margin: 0;
      color: #94a3b8;
      font-size: 0.9rem;
      line-height: 1.6;
    }

    .role-focus {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .focus-label {
      font-size: 0.75rem;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 600;
    }

    .focus-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }

    .focus-tag {
      padding: 4px 10px;
      background: rgba(56, 189, 248, 0.1);
      border: 1px solid rgba(56, 189, 248, 0.2);
      border-radius: 12px;
      font-size: 0.75rem;
      color: #38bdf8;
      font-family: var(--font-mono);
    }

    .cta-section {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
      margin-top: 8px;
    }

    .primary-btn,
    .secondary-btn {
      padding: 14px 32px;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.95rem;
      cursor: pointer;
      transition: all 0.2s;
      border: none;
      font-family: var(--font-mono);
    }

    .primary-btn {
      background: var(--accent-active);
      color: white;
    }

    .primary-btn:hover:not(:disabled) {
      opacity: 0.9;
      transform: translateY(-1px);
    }

    .primary-btn:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }

    .secondary-btn {
      background: rgba(255, 255, 255, 0.05);
      color: #94a3b8;
      border: 1px solid var(--border);
    }

    .secondary-btn:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(56, 189, 248, 0.3);
      color: #38bdf8;
    }

    .progress-bar {
      display: flex;
      align-items: center;
      justify-content: center;
      margin-top: 40px;
      padding-top: 32px;
      border-top: 1px solid var(--border);
    }

    .progress-step {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      opacity: 0.4;
      transition: opacity 0.3s;
    }

    .progress-step.active {
      opacity: 1;
    }

    .progress-step.complete {
      opacity: 1;
    }

    .step-dot {
      width: 12px;
      height: 12px;
      border-radius: 50%;
      background: var(--border);
      transition: all 0.3s;
    }

    .progress-step.active .step-dot {
      background: #38bdf8;
      box-shadow: 0 0 12px rgba(56, 189, 248, 0.5);
      transform: scale(1.3);
    }

    .progress-step.complete .step-dot {
      background: #22c55e;
    }

    .step-label {
      font-size: 0.75rem;
      color: #64748b;
      font-family: var(--font-mono);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .progress-step.active .step-label {
      color: #38bdf8;
    }

    .progress-step.complete .step-label {
      color: #22c55e;
    }

    .progress-line {
      width: 80px;
      height: 2px;
      background: var(--border);
      transition: background 0.3s;
    }

    .progress-line.complete {
      background: #22c55e;
    }

    @media (max-width: 768px) {
      .onboarding-container {
        padding: 20px;
      }

      .role-grid {
        grid-template-columns: 1fr;
      }

      .provider-selector {
        grid-template-columns: repeat(2, 1fr);
      }

      .cta-section {
        flex-direction: column;
      }

      .primary-btn,
      .secondary-btn {
        width: 100%;
      }
    }
  `]
})
export class OnboardingFlowComponent implements OnInit, OnDestroy {
  private readonly ONBOARDING_COMPLETE_KEY = 'atlasia_onboarding_complete';
  private readonly DEFAULT_AGENT_ROLE_KEY = 'atlasia_default_agent_role';

  currentStep = signal(1);
  timestamp = new Date().toLocaleTimeString();

  welcomeMessage = `<h2>Welcome to Atlasia Orchestrator</h2>

Your AI-powered development automation platform is ready to go. Here's what makes Atlasia special:

• <strong>Autonomous Engineering</strong> – Let AI agents handle code reviews, bug fixes, and feature implementation
• <strong>Multi-Agent Collaboration</strong> – Specialized roles work together on complex tasks
• <strong>Full Transparency</strong> – Every decision, tool call, and reasoning step is visible
• <strong>Your Control</strong> – Approve, modify, or reject any automated action

Let's get you set up in just a few quick steps.`;

  typedText = signal('');
  isTypingComplete = signal(false);
  private typingInterval?: ReturnType<typeof setInterval>;

  gitProviders = [
    { id: 'github', name: 'GitHub', icon: '🐙' },
    { id: 'gitlab', name: 'GitLab', icon: '🦊' },
    { id: 'bitbucket', name: 'Bitbucket', icon: '🪣' },
    { id: 'custom', name: 'Custom', icon: '⚙️' }
  ];

  selectedProvider = signal('');
  gitToken = signal('');
  customGitUrl = signal('');

  agentRoles: AgentRole[] = [
    {
      id: 'pm',
      name: 'Product Manager',
      icon: '📋',
      description: 'Focus on requirements analysis, user stories, and feature prioritization.',
      focusAreas: ['Requirements', 'Planning', 'Stakeholder Communication']
    },
    {
      id: 'architect',
      name: 'Software Architect',
      icon: '🏗️',
      description: 'Design system architecture, patterns, and technical decisions.',
      focusAreas: ['System Design', 'Patterns', 'Scalability']
    },
    {
      id: 'developer',
      name: 'Full-Stack Developer',
      icon: '💻',
      description: 'Implement features, write code, and integrate components.',
      focusAreas: ['Implementation', 'Testing', 'Integration']
    },
    {
      id: 'security-engineer',
      name: 'Security Engineer',
      icon: '🔒',
      description: 'Identify vulnerabilities, ensure secure coding practices, and compliance.',
      focusAreas: ['Security', 'Compliance', 'Best Practices']
    },
    {
      id: 'code-quality-engineer',
      name: 'Code Quality Engineer',
      icon: '✨',
      description: 'Review code quality, performance, maintainability, and standards.',
      focusAreas: ['Code Review', 'Performance', 'Refactoring']
    }
  ];

  selectedRole = signal('');

  constructor(
    private settingsService: SettingsService,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit() {
    this.startTypingAnimation();
  }

  ngOnDestroy() {
    if (this.typingInterval) {
      clearInterval(this.typingInterval);
    }
  }

  startTypingAnimation() {
    console.log('Starting typing animation...');
    const plainText = this.welcomeMessage;
    let currentIndex = 0;
    
    this.typingInterval = setInterval(() => {
      if (currentIndex <= plainText.length) {
        this.typedText.set(plainText.substring(0, currentIndex));
        currentIndex++;
      } else {
        if (this.typingInterval) {
          clearInterval(this.typingInterval);
        }
        console.log('Typing animation complete.');
        setTimeout(() => {
          this.isTypingComplete.set(true);
        });
      }
    }, 10);
  }

  nextStep() {
    console.log('Navigating to next step from:', this.currentStep());
    this.currentStep.update(step => step + 1);
    console.log('New step:', this.currentStep());
  }

  selectProvider(providerId: string) {
    this.selectedProvider.set(providerId);
  }

  canProceedFromProvider(): boolean {
    return this.selectedProvider() !== '' && this.gitToken().trim() !== '';
  }

  skipProvider() {
    this.nextStep();
  }

  saveProviderAndNext() {
    if (!this.canProceedFromProvider()) {
      return;
    }

    const providerMap: { [key: string]: 'github' | 'gitlab' | 'bitbucket' } = {
      'github': 'github',
      'gitlab': 'gitlab',
      'bitbucket': 'bitbucket'
    };

    const provider = this.selectedProvider() === 'custom' 
      ? null 
      : providerMap[this.selectedProvider()];

    this.settingsService.updateGitProvider({
      provider: provider,
      token: this.gitToken(),
      url: this.customGitUrl() || null,
      label: this.selectedProvider() === 'custom' ? 'Custom Git' : null
    });

    this.nextStep();
  }

  selectRole(roleId: string) {
    this.selectedRole.set(roleId);
  }

  skipRole() {
    this.completeOnboarding();
  }

  completeOnboarding() {
    if (this.selectedRole()) {
      localStorage.setItem(this.DEFAULT_AGENT_ROLE_KEY, this.selectedRole());
    }

    localStorage.setItem(this.ONBOARDING_COMPLETE_KEY, 'true');

    this.router.navigate(['/dashboard']);
  }
}
