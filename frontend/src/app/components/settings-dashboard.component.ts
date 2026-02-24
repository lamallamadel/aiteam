import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { SettingsService, GitProviderWithId } from '../services/settings.service';
import { AuthService } from '../services/auth.service';
import { OrchestratorService } from '../services/orchestrator.service';
import { CurrentUserDto } from '../models/orchestrator.model';
import { ToastService } from '../services/toast.service';

Chart.register(...registerables);

type TabId = 'integrations' | 'usage' | 'ai-customization';

@Component({
  selector: 'app-settings-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective],
  template: `
    <div class="settings-container">
      <div class="header">
        <h2>Settings</h2>
        <p class="subtitle">Configure integrations, monitor usage, and customize AI behavior</p>
      </div>

      <div class="tabs">
        <button 
          class="tab-btn"
          [class.active]="activeTab() === 'integrations'"
          (click)="activeTab.set('integrations')">
          Integrations
        </button>
        <button 
          class="tab-btn"
          [class.active]="activeTab() === 'usage'"
          (click)="activeTab.set('usage')">
          Usage & Rate Limiting
        </button>
        <button 
          class="tab-btn"
          [class.active]="activeTab() === 'ai-customization'"
          (click)="activeTab.set('ai-customization')">
          AI Customization
        </button>
      </div>

      <div class="tab-content">
        @switch (activeTab()) {
          @case ('integrations') {
            <div class="integrations-tab">
              <div class="section-header">
                <h3>Git Providers</h3>
                <button class="add-btn" (click)="showAddProviderForm()">+ Add Provider</button>
              </div>

              @if (showProviderForm()) {
                <div class="provider-form glass-panel">
                  <div class="form-row">
                    <div class="form-group">
                      <label>Provider Type</label>
                      <select [(ngModel)]="formProvider" class="input-field">
                        <option value="github">GitHub</option>
                        <option value="gitlab">GitLab</option>
                        <option value="bitbucket">Bitbucket</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label>Label</label>
                      <input 
                        type="text" 
                        [(ngModel)]="formLabel" 
                        placeholder="e.g., Company GitLab"
                        class="input-field">
                    </div>
                  </div>

                  <div class="form-row">
                    <div class="form-group">
                      <label>Access Token</label>
                      <input 
                        type="password" 
                        [(ngModel)]="formToken" 
                        placeholder="Enter access token"
                        class="input-field">
                    </div>
                  </div>

                  <div class="form-row">
                    <div class="form-group">
                      <label>Custom URL (Optional)</label>
                      <input 
                        type="text" 
                        [(ngModel)]="formUrl" 
                        placeholder="https://git.example.com"
                        class="input-field">
                      <span class="input-hint">Leave empty for cloud-hosted providers</span>
                    </div>
                  </div>

                  <div class="form-actions">
                    <button class="btn-secondary" (click)="cancelProviderForm()">Cancel</button>
                    <button 
                      class="btn-primary" 
                      [disabled]="!canSaveProvider()"
                      (click)="saveProvider()">
                      {{ editingProviderId() ? 'Update' : 'Add' }} Provider
                    </button>
                  </div>
                </div>
              }

              @if (providers().length === 0 && !showProviderForm()) {
                <div class="zero-state">
                  <div class="zero-icon">🔗</div>
                  <h4>Connect your first GitLab repository</h4>
                  <p>Add a Git provider to start orchestrating AI-powered code reviews and fixes</p>
                  <button class="btn-primary" (click)="showAddProviderForm()">+ Connect Provider</button>
                </div>
              }

              @if (providers().length > 0) {
                <div class="providers-grid">
                  @for (provider of providers(); track provider.id) {
                    <div class="provider-card">
                      <div class="provider-header">
                        <div class="provider-title">
                          <span class="provider-icon">{{ getProviderIcon(provider.provider) }}</span>
                          <div class="provider-info">
                            <span class="provider-label">{{ provider.label || provider.provider }}</span>
                            <span class="provider-type">{{ provider.provider }}</span>
                          </div>
                        </div>
                        <span class="status-badge" [class]="provider.status">{{ provider.status }}</span>
                      </div>
                      @if (provider.url) {
                        <div class="provider-url">{{ provider.url }}</div>
                      }
                      <div class="provider-actions">
                        <button class="btn-icon" (click)="editProvider(provider)">✏️</button>
                        <button class="btn-icon danger" (click)="deleteProvider(provider.id)">🗑️</button>
                      </div>
                    </div>
                  }
                </div>
              }

              <div class="section-header">
                <h3>OAuth2 Integrations</h3>
              </div>

              <div class="oauth2-section">
                <p class="section-description">Connect your accounts via OAuth2 for seamless integration</p>
                <div class="oauth2-buttons">
                  <div class="oauth2-item">
                    <button class="oauth2-btn github" (click)="connectOAuth2('github')" [disabled]="oauth2Loading() === 'github'">
                      @if (oauth2Loading() === 'github') {
                        <span class="oauth2-spinner">⏳</span>
                      } @else {
                        <span class="oauth2-icon">🐙</span>
                      }
                      <div class="oauth2-text">
                        <span class="oauth2-title">Connect GitHub</span>
                        <span class="oauth2-desc">Access repositories and manage code</span>
                      </div>
                    </button>
                    @if (isOAuth2Connected('github')) {
                      <span class="oauth2-status-badge connected">Connected</span>
                    }
                  </div>
                  <div class="oauth2-item">
                    <button class="oauth2-btn google" (click)="connectOAuth2('google')" [disabled]="oauth2Loading() === 'google'">
                      @if (oauth2Loading() === 'google') {
                        <span class="oauth2-spinner">⏳</span>
                      } @else {
                        <span class="oauth2-icon">📧</span>
                      }
                      <div class="oauth2-text">
                        <span class="oauth2-title">Connect Google</span>
                        <span class="oauth2-desc">Sync calendar and email notifications</span>
                      </div>
                    </button>
                    @if (isOAuth2Connected('google')) {
                      <span class="oauth2-status-badge connected">Connected</span>
                    }
                  </div>
                  <div class="oauth2-item">
                    <button class="oauth2-btn gitlab" (click)="connectOAuth2('gitlab')" [disabled]="oauth2Loading() === 'gitlab'">
                      @if (oauth2Loading() === 'gitlab') {
                        <span class="oauth2-spinner">⏳</span>
                      } @else {
                        <span class="oauth2-icon">🦊</span>
                      }
                      <div class="oauth2-text">
                        <span class="oauth2-title">Connect GitLab</span>
                        <span class="oauth2-desc">Integrate CI/CD and issue tracking</span>
                      </div>
                    </button>
                    @if (isOAuth2Connected('gitlab')) {
                      <span class="oauth2-status-badge connected">Connected</span>
                    }
                  </div>
                </div>
              </div>
            </div>
          }

          @case ('usage') {
            <div class="usage-tab">
              <div class="metrics-grid">
                <div class="metric-card">
                  <div class="metric-label">Token Consumption</div>
                  <div class="metric-value">{{ formatNumber(usageData().tokenConsumption) }}</div>
                  <div class="metric-subtext">of {{ formatNumber(usageData().budget) }} budget</div>
                </div>

                <div class="metric-card">
                  <div class="metric-label">Budget Used</div>
                  <div class="metric-value">{{ budgetPercent() }}%</div>
                  <div class="progress-bar">
                    <div class="progress-fill" [style.width.%]="budgetPercent()"></div>
                  </div>
                </div>

                <div class="metric-card">
                  <div class="metric-label">RPM Limit</div>
                  <div class="metric-value">{{ rateLimitConfig().rpm }}</div>
                  <div class="metric-subtext">requests/minute</div>
                </div>

                <div class="metric-card">
                  <div class="metric-label">Monthly Requests</div>
                  <div class="metric-value">{{ formatNumber(monthlyRequests()) }}</div>
                  <div class="metric-subtext">this billing cycle</div>
                </div>
              </div>

              <div class="charts-section">
                <div class="chart-card">
                  <h3>Token Consumption Over Time</h3>
                  <div class="chart-wrapper">
                    <canvas baseChart
                      [data]="consumptionChartData"
                      [options]="lineChartOptions"
                      type="line">
                    </canvas>
                  </div>
                </div>

                <div class="chart-card">
                  <h3>Request Rate</h3>
                  <div class="chart-wrapper">
                    <canvas baseChart
                      [data]="requestRateChartData"
                      [options]="barChartOptions"
                      type="bar">
                    </canvas>
                  </div>
                </div>
              </div>

              <div class="limits-section">
                <h3>Rate Limit Configuration</h3>
                <div class="form-row">
                  <div class="form-group">
                    <label>Requests Per Minute (RPM)</label>
                    <input 
                      type="number" 
                      [(ngModel)]="editRpm" 
                      class="input-field"
                      min="1">
                  </div>
                  <div class="form-group">
                    <label>Tokens Per Minute (TPM)</label>
                    <input 
                      type="number" 
                      [(ngModel)]="editTpm" 
                      class="input-field"
                      min="1">
                  </div>
                </div>
                <button class="btn-primary" (click)="saveRateLimits()">Save Limits</button>
              </div>
            </div>
          }

          @case ('ai-customization') {
            <div class="ai-customization-tab">
              <div class="autonomy-section">
                <h3>Agent Autonomy Level</h3>
                <div class="autonomy-levels">
                  @for (level of autonomyLevels; track level.value) {
                    <button
                      class="autonomy-btn"
                      [class.active]="aiPreferences().autonomyLevel === level.value"
                      (click)="setAutonomy(level.value)">
                      <span class="level-icon">{{ level.icon }}</span>
                      <span class="level-name">{{ level.name }}</span>
                      <span class="level-desc">{{ level.description }}</span>
                    </button>
                  }
                </div>
              </div>

              <div class="rules-section">
                <h3>Oversight Interrupt Rules</h3>
                <div class="rules-list">
                  @for (rule of interruptRules; track rule.id) {
                    <div class="rule-item">
                      <div class="rule-content">
                        <span class="rule-name">{{ rule.name }}</span>
                        <span class="rule-desc">{{ rule.description }}</span>
                      </div>
                      <label class="toggle">
                        <input
                          type="checkbox"
                          [checked]="isRuleEnabled(rule.id)"
                          (change)="toggleInterruptRule(rule.id)" />
                        <span class="toggle-slider"></span>
                      </label>
                    </div>
                  }
                </div>
              </div>

              <div class="instructions-section">
                <h3>Custom System Instructions</h3>
                <p class="section-hint">Add custom instructions to guide agent behavior across all workflows</p>
                <textarea 
                  [(ngModel)]="customInstructions"
                  class="instructions-textarea"
                  rows="8"
                  placeholder="e.g., Always prioritize security best practices, use TypeScript strict mode, follow company coding standards..."></textarea>
                <button class="btn-primary" (click)="saveCustomInstructions()">Save Instructions</button>
              </div>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .settings-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 20px;
      height: 100%;
      overflow-y: auto;
    }

    .header h2 { font-size: 1.8rem; margin-bottom: 4px; }
    .subtitle { color: rgba(255,255,255,0.5); font-size: 0.9rem; }

    .tabs {
      display: flex;
      gap: 8px;
      border-bottom: 1px solid var(--border);
      padding-bottom: 2px;
    }

    .tab-btn {
      padding: 10px 20px;
      background: transparent;
      border: none;
      border-bottom: 2px solid transparent;
      color: rgba(255,255,255,0.5);
      cursor: pointer;
      font-weight: 500;
      font-size: 0.9rem;
      transition: all 0.2s;
    }

    .tab-btn:hover { color: rgba(255,255,255,0.8); }
    .tab-btn.active {
      color: var(--accent-active);
      border-bottom-color: var(--accent-active);
    }

    .tab-content { flex: 1; overflow-y: auto; }

    .integrations-tab, .usage-tab, .ai-customization-tab {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .section-header h3 { font-size: 1.1rem; margin: 0; }

    .add-btn {
      padding: 8px 16px;
      background: var(--accent-active);
      color: white;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.85rem;
      cursor: pointer;
      transition: opacity 0.2s;
    }
    .add-btn:hover { opacity: 0.85; }

    .zero-state {
      padding: 60px 20px;
      text-align: center;
      background: var(--surface);
      border: 2px dashed var(--border);
      border-radius: 12px;
    }

    .zero-icon { font-size: 3rem; margin-bottom: 16px; }
    .zero-state h4 { font-size: 1.2rem; margin: 0 0 8px 0; }
    .zero-state p { color: rgba(255,255,255,0.5); margin: 0 0 24px 0; }

    .provider-form {
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .form-row {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .form-group label {
      font-size: 0.85rem;
      font-weight: 600;
      color: rgba(255,255,255,0.7);
    }

    .input-field {
      padding: 10px 12px;
      background: rgba(255,255,255,0.05);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: white;
      font-size: 0.9rem;
    }

    .input-field:focus {
      outline: none;
      border-color: var(--accent-active);
      background: rgba(255,255,255,0.08);
    }

    .input-hint {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.4);
    }

    .form-actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
    }

    .btn-primary, .btn-secondary {
      padding: 10px 20px;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.85rem;
      cursor: pointer;
      transition: opacity 0.2s;
    }

    .btn-primary {
      background: var(--accent-active);
      color: white;
    }

    .btn-secondary {
      background: rgba(255,255,255,0.08);
      color: white;
    }

    .btn-primary:hover, .btn-secondary:hover { opacity: 0.85; }
    .btn-primary:disabled {
      opacity: 0.4;
      cursor: not-allowed;
    }

    .providers-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 1px;
      background: var(--border);
    }

    .provider-card {
      padding: 16px;
      background: var(--surface);
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .provider-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .provider-title {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .provider-icon { font-size: 1.5rem; }

    .provider-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .provider-label {
      font-weight: 600;
      font-size: 0.9rem;
    }

    .provider-type {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.4);
      text-transform: capitalize;
    }

    .status-badge {
      padding: 3px 10px;
      border-radius: 8px;
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
    }

    .status-badge.connected {
      background: rgba(34,197,94,0.15);
      color: #22c55e;
    }

    .status-badge.disconnected {
      background: rgba(148,163,184,0.15);
      color: #94a3b8;
    }

    .status-badge.error {
      background: rgba(239,68,68,0.15);
      color: #ef4444;
    }

    .provider-url {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.4);
      font-family: var(--font-mono);
    }

    .provider-actions {
      display: flex;
      gap: 8px;
    }

    .btn-icon {
      padding: 6px 10px;
      background: rgba(255,255,255,0.05);
      border: 1px solid var(--border);
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.85rem;
      transition: all 0.2s;
    }

    .btn-icon:hover {
      background: rgba(255,255,255,0.08);
      border-color: var(--accent-active);
    }

    .btn-icon.danger:hover {
      background: rgba(239,68,68,0.1);
      border-color: #ef4444;
    }

    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1px;
      background: var(--border);
    }

    .metric-card {
      padding: 18px;
      background: var(--surface);
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .metric-label {
      font-size: 0.78rem;
      color: #94a3b8;
    }

    .metric-value {
      font-size: 1.9rem;
      font-weight: 700;
      color: white;
      font-family: var(--font-mono);
      font-variant-numeric: tabular-nums;
    }

    .metric-subtext {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.4);
    }

    .progress-bar {
      width: 100%;
      height: 6px;
      background: rgba(255,255,255,0.1);
      border-radius: 3px;
      overflow: hidden;
      margin-top: 4px;
    }

    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #22c55e, #38bdf8);
      transition: width 0.3s;
    }

    .charts-section {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
      gap: 1px;
      background: var(--border);
    }

    .chart-card {
      padding: 20px;
      background: var(--surface);
    }

    .chart-card h3 {
      margin: 0 0 16px 0;
      color: white;
      font-size: 0.95rem;
      font-weight: 600;
    }

    .chart-wrapper {
      position: relative;
      height: 240px;
    }

    .limits-section {
      padding: 20px;
      background: var(--surface);
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .limits-section h3 {
      margin: 0;
      font-size: 1rem;
    }

    .autonomy-section, .rules-section, .instructions-section {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .autonomy-section h3, .rules-section h3, .instructions-section h3 {
      margin: 0;
      font-size: 1rem;
      color: rgba(255,255,255,0.8);
    }

    .autonomy-levels {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 12px;
    }

    .autonomy-btn {
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
      border-color: var(--accent-active);
      background: rgba(56,189,248,0.08);
    }

    .level-icon { font-size: 1.8rem; }
    .level-name { font-weight: 600; font-size: 0.9rem; }
    .level-desc {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.4);
      text-align: center;
    }

    .rules-list {
      display: flex;
      flex-direction: column;
      gap: 1px;
      background: var(--border);
    }

    .rule-item {
      padding: 14px 18px;
      background: var(--surface);
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 16px;
    }

    .rule-content {
      display: flex;
      flex-direction: column;
      gap: 2px;
      flex: 1;
    }

    .rule-name {
      font-weight: 500;
      font-size: 0.9rem;
    }

    .rule-desc {
      color: rgba(255,255,255,0.4);
      font-size: 0.8rem;
    }

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
      background: var(--accent-active);
    }

    .toggle input:checked + .toggle-slider::before {
      transform: translateX(18px);
    }

    .section-hint {
      font-size: 0.85rem;
      color: rgba(255,255,255,0.5);
      margin: 0;
    }

    .instructions-textarea {
      width: 100%;
      padding: 12px;
      background: rgba(255,255,255,0.05);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: white;
      font-size: 0.9rem;
      font-family: var(--font-mono);
      resize: vertical;
    }

    .instructions-textarea:focus {
      outline: none;
      border-color: var(--accent-active);
      background: rgba(255,255,255,0.08);
    }

    .oauth2-section {
      display: flex;
      flex-direction: column;
      gap: 16px;
      padding: 20px;
      background: var(--surface);
      border-radius: 8px;
      border: 1px solid var(--border);
    }

    .section-description {
      margin: 0;
      font-size: 0.9rem;
      color: rgba(255,255,255,0.6);
    }

    .oauth2-buttons {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
    }

    .oauth2-item {
      display: flex;
      flex-direction: column;
      gap: 8px;
      position: relative;
    }

    .oauth2-btn {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 20px;
      background: rgba(255,255,255,0.03);
      border: 2px solid var(--border);
      border-radius: 12px;
      cursor: pointer;
      transition: all 0.2s;
      width: 100%;
    }

    .oauth2-btn:hover:not(:disabled) {
      background: rgba(255,255,255,0.06);
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
    }

    .oauth2-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .oauth2-btn.github:hover:not(:disabled) {
      border-color: #8b949e;
    }

    .oauth2-btn.google:hover:not(:disabled) {
      border-color: #4285f4;
    }

    .oauth2-btn.gitlab:hover:not(:disabled) {
      border-color: #fc6d26;
    }

    .oauth2-icon {
      font-size: 2.5rem;
      flex-shrink: 0;
    }

    .oauth2-spinner {
      font-size: 2.5rem;
      flex-shrink: 0;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .oauth2-text {
      display: flex;
      flex-direction: column;
      gap: 4px;
      text-align: left;
      flex: 1;
    }

    .oauth2-title {
      font-size: 1rem;
      font-weight: 600;
      color: white;
    }

    .oauth2-desc {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.5);
    }

    .oauth2-status-badge {
      padding: 4px 12px;
      border-radius: 8px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      align-self: flex-start;
      margin-left: 4px;
    }

    .oauth2-status-badge.connected {
      background: rgba(34,197,94,0.15);
      color: #22c55e;
    }
  `]
})
export class SettingsDashboardComponent implements OnInit, OnDestroy {
  activeTab = signal<TabId>('integrations');

  providers = computed(() => this.settingsService.gitProviders());
  usageData = computed(() => this.settingsService.usageData());
  usageHistory = computed(() => this.settingsService.usageHistory());
  monthlyRequests = computed(() => this.settingsService.monthlyRequests());
  rateLimitConfig = computed(() => this.settingsService.rateLimitConfig());
  aiPreferences = computed(() => this.settingsService.aiPreferences());
  
  currentUser = signal<CurrentUserDto | null>(null);
  oauth2Loading = signal<string | null>(null);
  
  private messageHandler: ((event: MessageEvent) => void) | null = null;

  budgetPercent = computed(() => {
    const usage = this.usageData();
    if (usage.budget === 0) return 0;
    return Math.min(100, Math.round((usage.tokenConsumption / usage.budget) * 100));
  });

  showProviderForm = signal(false);
  editingProviderId = signal<string | null>(null);
  formProvider = signal<'github' | 'gitlab' | 'bitbucket'>('gitlab');
  formLabel = signal('');
  formToken = signal('');
  formUrl = signal('');

  editRpm = signal(60);
  editTpm = signal(90000);

  customInstructions = signal('');

  enabledRules = signal<Set<string>>(new Set());

  autonomyLevels = [
    { value: 'autonomous', name: 'Autonomous', icon: '⚡', description: 'Agents execute without checkpoints' },
    { value: 'confirm', name: 'Confirm', icon: '👁', description: 'Ask before critical actions' },
    { value: 'observe', name: 'Observe', icon: '🔒', description: 'Human approval at every stage' }
  ];

  interruptRules = [
    { id: 'pre-commit', name: 'Pre-Commit Review', description: 'Require approval before commits' },
    { id: 'security-scan', name: 'Security Scan Gate', description: 'Escalate on vulnerabilities' },
    { id: 'test-failure', name: 'Test Failure Escalation', description: 'Escalate after fix loop limit' },
    { id: 'pr-merge', name: 'PR Merge Approval', description: 'Require approval before merging' },
    { id: 'schema-mismatch', name: 'Schema Validation Failure', description: 'Escalate on schema mismatch' },
    { id: 'token-overrun', name: 'Token Budget Exceeded', description: 'Escalate on token overrun' }
  ];

  consumptionChartData: ChartConfiguration['data'] = {
    labels: [],
    datasets: [{
      label: 'Token Consumption',
      data: [],
      borderColor: 'rgba(56,189,248,0.8)',
      backgroundColor: 'rgba(56,189,248,0.1)',
      fill: true,
      tension: 0.4
    }]
  };

  requestRateChartData: ChartConfiguration['data'] = {
    labels: [],
    datasets: [{
      label: 'Requests',
      data: [],
      backgroundColor: 'rgba(139,92,246,0.7)'
    }]
  };

  readonly lineChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.08)' }, ticks: { color: '#94a3b8' } },
      x: { grid: { display: false }, ticks: { color: '#94a3b8' } }
    }
  };

  readonly barChartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.08)' }, ticks: { color: '#94a3b8' } },
      x: { grid: { display: false }, ticks: { color: '#94a3b8' } }
    }
  };

  constructor(
    private settingsService: SettingsService,
    private authService: AuthService,
    private orchestratorService: OrchestratorService,
    private toastService: ToastService
  ) {}

  ngOnInit() {
    this.loadData();
    this.loadCurrentUser();
  }

  ngOnDestroy() {
    if (this.messageHandler) {
      window.removeEventListener('message', this.messageHandler);
    }
  }

  loadData() {
    const limits = this.rateLimitConfig();
    this.editRpm.set(limits.rpm);
    this.editTpm.set(limits.tpm);

    const prefs = this.aiPreferences();
    this.customInstructions.set(prefs.systemInstructions || '');
    this.enabledRules.set(new Set(prefs.oversightRules));

    this.updateCharts();
  }

  updateCharts() {
    const history = this.usageHistory();
    const labels = history.map(h => new Date(h.timestamp).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }));
    const data = history.map(h => h.tokens);

    this.consumptionChartData = {
      labels,
      datasets: [{
        label: 'Token Consumption',
        data,
        borderColor: 'rgba(56,189,248,0.8)',
        backgroundColor: 'rgba(56,189,248,0.1)',
        fill: true,
        tension: 0.4
      }]
    };

    const requestData = history.map(() => Math.floor(Math.random() * 100 + 50));
    this.requestRateChartData = {
      labels,
      datasets: [{
        label: 'Requests',
        data: requestData,
        backgroundColor: 'rgba(139,92,246,0.7)'
      }]
    };
  }

  showAddProviderForm() {
    this.showProviderForm.set(true);
    this.editingProviderId.set(null);
    this.resetProviderForm();
  }

  cancelProviderForm() {
    this.showProviderForm.set(false);
    this.editingProviderId.set(null);
    this.resetProviderForm();
  }

  resetProviderForm() {
    this.formProvider.set('gitlab');
    this.formLabel.set('');
    this.formToken.set('');
    this.formUrl.set('');
  }

  canSaveProvider(): boolean {
    return this.formProvider() !== null && this.formToken() !== '' && this.formLabel() !== '';
  }

  saveProvider() {
    if (!this.canSaveProvider()) return;

    const provider = {
      provider: this.formProvider(),
      label: this.formLabel(),
      token: this.formToken(),
      url: this.formUrl() || null,
      status: 'connected' as const
    };

    if (this.editingProviderId()) {
      this.settingsService.updateGitProviderById(this.editingProviderId()!, provider);
    } else {
      this.settingsService.addGitProvider(provider);
    }

    this.cancelProviderForm();
  }

  editProvider(provider: GitProviderWithId) {
    this.editingProviderId.set(provider.id);
    this.formProvider.set(provider.provider as 'github' | 'gitlab' | 'bitbucket');
    this.formLabel.set(provider.label || '');
    this.formToken.set(provider.token || '');
    this.formUrl.set(provider.url || '');
    this.showProviderForm.set(true);
  }

  deleteProvider(id: string) {
    if (confirm('Are you sure you want to delete this provider?')) {
      this.settingsService.removeGitProvider(id);
    }
  }

  getProviderIcon(provider: string | null): string {
    switch (provider) {
      case 'github': return '🐙';
      case 'gitlab': return '🦊';
      case 'bitbucket': return '🪣';
      default: return '🔗';
    }
  }

  saveRateLimits() {
    this.settingsService.updateRateLimitConfig({
      rpm: this.editRpm(),
      tpm: this.editTpm()
    });
  }

  setAutonomy(level: string) {
    this.settingsService.updateAIPreferences({
      autonomyLevel: level as 'autonomous' | 'confirm' | 'observe'
    });
  }

  isRuleEnabled(ruleId: string): boolean {
    return this.enabledRules().has(ruleId);
  }

  toggleInterruptRule(ruleId: string) {
    const current = new Set(this.enabledRules());
    if (current.has(ruleId)) {
      current.delete(ruleId);
    } else {
      current.add(ruleId);
    }
    this.enabledRules.set(current);
    this.settingsService.updateAIPreferences({
      oversightRules: Array.from(current)
    });
  }

  saveCustomInstructions() {
    this.settingsService.updateAIPreferences({
      systemInstructions: this.customInstructions() || null
    });
  }

  formatNumber(num: number): string {
    if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`;
    if (num >= 1000) return `${(num / 1000).toFixed(1)}K`;
    return num.toString();
  }

  loadCurrentUser() {
    this.orchestratorService.getCurrentUser().subscribe({
      next: (user) => {
        this.currentUser.set(user);
      },
      error: (err) => {
        this.toastService.show('Failed to load user information', 'error');
      }
    });
  }

  isOAuth2Connected(provider: string): boolean {
    const user = this.currentUser();
    if (!user || !user.oauth2LinkedAccounts) {
      return false;
    }
    return user.oauth2LinkedAccounts.includes(provider);
  }

  connectOAuth2(provider: 'github' | 'google' | 'gitlab') {
    this.oauth2Loading.set(provider);
    
    const width = 600;
    const height = 700;
    const left = (window.screen.width / 2) - (width / 2);
    const top = (window.screen.height / 2) - (height / 2);
    
    const authUrl = `/oauth2/authorization/${provider}`;
    const popup = window.open(
      authUrl,
      'oauth2_authorization',
      `width=${width},height=${height},top=${top},left=${left},scrollbars=yes,resizable=yes`
    );

    if (popup) {
      if (this.messageHandler) {
        window.removeEventListener('message', this.messageHandler);
      }

      this.messageHandler = (event: MessageEvent) => {
        if (event.origin !== window.location.origin) {
          return;
        }
        
        if (event.data && event.data.type === 'oauth2-success') {
          const { accessToken, refreshToken } = event.data;
          
          if (accessToken && refreshToken) {
            this.authService.storeTokens(accessToken, refreshToken);
            this.loadCurrentUser();
          }
          
          popup.close();
          this.oauth2Loading.set(null);
          
          if (this.messageHandler) {
            window.removeEventListener('message', this.messageHandler);
            this.messageHandler = null;
          }
        }
      };

      window.addEventListener('message', this.messageHandler);

      const checkPopup = setInterval(() => {
        if (popup.closed) {
          clearInterval(checkPopup);
          this.oauth2Loading.set(null);
          if (this.messageHandler) {
            window.removeEventListener('message', this.messageHandler);
            this.messageHandler = null;
          }
        }
      }, 1000);
    } else {
      this.oauth2Loading.set(null);
      this.toastService.show('Popup blocked. Please allow popups for this site to use OAuth2 authentication.', 'error');
    }
  }
}
