import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrchestratorService } from '../../services/orchestrator.service';

interface OversightRule {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
  level: 'full_auto' | 'supervised' | 'manual';
}

@Component({
  selector: 'app-oversight-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="config-container">
      <div class="header">
        <h2>Oversight Rules</h2>
        <p class="subtitle">Configure autonomy levels and escalation checkpoints</p>
      </div>

      <div class="autonomy-card glass-panel">
        <h3>Global Autonomy Level</h3>
        <div class="autonomy-levels">
          @for (level of autonomyLevels; track level.value) {
            <button
              class="autonomy-btn"
              [class.active]="selectedAutonomy() === level.value"
              (click)="setAutonomy(level.value)">
              <span class="level-icon">{{ level.icon }}</span>
              <span class="level-name">{{ level.name }}</span>
              <span class="level-desc">{{ level.description }}</span>
            </button>
          }
        </div>
      </div>

      <div class="rules-section">
        <h3>Escalation Checkpoints</h3>
        @for (rule of rules(); track rule.id) {
          <div class="rule-card glass-panel" [class.disabled]="!rule.enabled">
            <div class="rule-header">
              <div class="rule-info">
                <span class="rule-name">{{ rule.name }}</span>
                <span class="rule-desc">{{ rule.description }}</span>
              </div>
              <div class="rule-controls">
                <span class="level-tag" [class]="rule.level">{{ formatLevel(rule.level) }}</span>
                <label class="toggle">
                  <input
                    type="checkbox"
                    [checked]="rule.enabled"
                    (change)="toggleRule(rule.id)" />
                  <span class="toggle-slider"></span>
                </label>
              </div>
            </div>
          </div>
        }
      </div>

      <div class="save-section">
        <button class="btn-save" (click)="saveConfig()" [disabled]="saving()">
          {{ saving() ? 'Saving…' : 'Save Configuration' }}
        </button>
        <span *ngIf="savedOk()" class="save-feedback ok">✓ Saved</span>
        <span *ngIf="saveError()" class="save-feedback err">✗ {{ saveError() }}</span>
        <span *ngIf="!savedOk() && !saveError()" class="save-hint">Changes take effect on the next bolt execution</span>
      </div>
    </div>
  `,
  styles: [`
    .config-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 20px;
      height: 100%;
      overflow-y: auto;
    }
    .header h2 { font-size: 1.8rem; margin-bottom: 4px; }
    .subtitle { color: rgba(255,255,255,0.5); font-size: 0.9rem; }
    .autonomy-card { padding: 20px; }
    .autonomy-card h3, .rules-section h3 {
      margin: 0 0 16px 0;
      font-size: 1rem;
      color: rgba(255,255,255,0.8);
    }
    .autonomy-levels { display: flex; gap: 12px; }
    .autonomy-btn {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      padding: 16px;
      background: rgba(255,255,255,0.03);
      border: 2px solid rgba(255,255,255,0.08);
      border-radius: 12px;
      cursor: pointer;
      transition: all 0.2s;
      color: white;
    }
    .autonomy-btn:hover {
      background: rgba(255,255,255,0.06);
      border-color: rgba(255,255,255,0.15);
    }
    .autonomy-btn.active {
      border-color: #38bdf8;
      background: rgba(56,189,248,0.08);
    }
    .level-icon { font-size: 1.8rem; }
    .level-name { font-weight: 600; font-size: 0.9rem; }
    .level-desc {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.4);
      text-align: center;
    }
    .rules-section { display: flex; flex-direction: column; gap: 8px; }
    .rule-card { padding: 14px 18px; transition: opacity 0.2s; }
    .rule-card.disabled { opacity: 0.5; }
    .rule-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .rule-info { display: flex; flex-direction: column; gap: 2px; }
    .rule-name { font-weight: 500; font-size: 0.9rem; }
    .rule-desc { color: rgba(255,255,255,0.4); font-size: 0.8rem; }
    .rule-controls { display: flex; align-items: center; gap: 12px; }
    .level-tag {
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
    }
    .level-tag.full_auto { background: rgba(34,197,94,0.15); color: #22c55e; }
    .level-tag.supervised { background: rgba(234,179,8,0.15); color: #eab308; }
    .level-tag.manual { background: rgba(239,68,68,0.15); color: #ef4444; }
    .toggle {
      position: relative;
      width: 40px;
      height: 22px;
      cursor: pointer;
    }
    .toggle input { display: none; }
    .toggle-slider {
      position: absolute;
      inset: 0;
      background: rgba(255,255,255,0.1);
      border-radius: 11px;
      transition: 0.2s;
    }
    .toggle-slider::before {
      content: '';
      position: absolute;
      width: 16px;
      height: 16px;
      left: 3px;
      bottom: 3px;
      background: white;
      border-radius: 50%;
      transition: 0.2s;
    }
    .toggle input:checked + .toggle-slider {
      background: #38bdf8;
    }
    .toggle input:checked + .toggle-slider::before {
      transform: translateX(18px);
    }
    .save-section {
      display: flex;
      align-items: center;
      gap: 16px;
      padding-top: 8px;
    }
    .btn-save {
      padding: 10px 24px;
      background: #38bdf8;
      color: white;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      cursor: pointer;
      transition: opacity 0.2s;
    }
    .btn-save:hover { opacity: 0.85; }
    .save-hint { color: rgba(255,255,255,0.35); font-size: 0.8rem; }
    .save-feedback { font-size: 0.82rem; font-weight: 600; }
    .save-feedback.ok  { color: #22c55e; }
    .save-feedback.err { color: #ef4444; }
  `]
})
// Default rule catalogue (descriptions live only in the frontend)
const DEFAULT_RULES: OversightRule[] = [
  { id: 'pre-commit',      name: 'Pre-Commit Review',        description: 'Require human approval before developer commits code',          enabled: true,  level: 'supervised' },
  { id: 'security-scan',   name: 'Security Scan Gate',       description: 'Escalate if static analysis detects vulnerabilities',           enabled: true,  level: 'full_auto'  },
  { id: 'test-failure',    name: 'Test Failure Escalation',   description: 'Escalate after fix loop limit is exceeded',                    enabled: true,  level: 'supervised' },
  { id: 'pr-merge',        name: 'PR Merge Approval',        description: 'Require human approval before merging pull requests',           enabled: true,  level: 'manual'     },
  { id: 'schema-mismatch', name: 'Schema Validation Failure', description: 'Escalate when agent output does not match expected schema',    enabled: false, level: 'supervised' },
  { id: 'token-overrun',   name: 'Token Budget Exceeded',    description: 'Escalate when an agent exceeds its token budget',               enabled: true,  level: 'supervised' },
];

export class OversightConfigComponent implements OnInit {
  private orchestratorService = inject(OrchestratorService);

  selectedAutonomy = signal<string>('supervised');
  rules            = signal<OversightRule[]>([]);
  saving           = signal(false);
  savedOk          = signal(false);
  saveError        = signal<string | null>(null);

  autonomyLevels = [
    { value: 'full_auto',  name: 'Full Auto',  icon: '⚡', description: 'Agents execute without human checkpoints' },
    { value: 'supervised', name: 'Supervised', icon: '👁',  description: 'Escalate on ambiguity, errors, or security concerns' },
    { value: 'manual',     name: 'Manual',     icon: '🔒', description: 'Human approval required at every stage' },
  ];

  ngOnInit() {
    // Seed with defaults first so the UI renders immediately
    this.rules.set(DEFAULT_RULES.map(r => ({ ...r })));

    // Then overlay with persisted config from backend
    this.orchestratorService.getOversightConfig().subscribe({
      next: (cfg) => {
        if (cfg.autonomyLevel) this.selectedAutonomy.set(cfg.autonomyLevel);
        if (cfg.interruptRules?.length) {
          // Update enabled state for rules the backend knows about; keep defaults otherwise
          this.rules.update(rules => rules.map(r => {
            const backendRule = cfg.interruptRules.find(br => br.ruleName === r.name);
            return backendRule ? { ...r, enabled: backendRule.enabled } : r;
          }));
        }
      },
      error: () => { /* backend unavailable — stick with defaults */ }
    });
  }

  setAutonomy(level: string) { this.selectedAutonomy.set(level); }

  toggleRule(ruleId: string) {
    this.rules.update(rules => rules.map(r => r.id === ruleId ? { ...r, enabled: !r.enabled } : r));
  }

  formatLevel(level: string): string { return level.replace('_', ' '); }

  saveConfig() {
    this.saving.set(true);
    this.savedOk.set(false);
    this.saveError.set(null);

    const payload = {
      autonomyLevel: this.selectedAutonomy(),
      interruptRules: this.rules().map(r => ({
        ruleName: r.name,
        tier: this.levelToTier(r.level),
        enabled: r.enabled,
      })),
      autoApproveMedianTier: true,
      maxConcurrentRuns: 5,
    };

    this.orchestratorService.saveOversightConfig(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.savedOk.set(true);
        setTimeout(() => this.savedOk.set(false), 3000);
      },
      error: (err) => {
        this.saving.set(false);
        this.saveError.set(err?.status === 0 ? 'Backend unreachable' : 'Save failed');
        setTimeout(() => this.saveError.set(null), 4000);
      }
    });
  }

  private levelToTier(level: string): string {
    switch (level) {
      case 'manual':    return 'critical';
      case 'supervised': return 'high';
      default:           return 'medium';
    }
  }
}
