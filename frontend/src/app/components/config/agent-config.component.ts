import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

interface AgentConfig {
  name: string;
  description: string;
  canRead: string[];
  canModify: string[];
  cannotModify: string[];
  tokenBudget: number;
  fixLoopLimit?: number;
  tools: string[];
}

@Component({
  selector: 'app-agent-config',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="config-container">
      <div class="header">
        <h2>Agent Logic Configuration</h2>
        <p class="subtitle">Boundaries, tools, and token budgets for each pipeline agent</p>
      </div>

      @if (loading()) {
        <div class="loading glass-panel">Loading agent configurations...</div>
      }

      @for (agent of agents(); track agent.name) {
        <div class="agent-card glass-panel">
          <div class="agent-header">
            <div class="agent-title">
              <span class="agent-badge">{{ agent.name }}</span>
              <span class="agent-desc">{{ agent.description }}</span>
            </div>
            <div class="token-budget">
              <span class="budget-label">Token Budget</span>
              <span class="budget-value">{{ formatTokens(agent.tokenBudget) }}</span>
            </div>
          </div>

          <div class="agent-body">
            <div class="section">
              <h4>Allowed Read Paths</h4>
              <div class="path-list">
                @for (path of agent.canRead; track path) {
                  <span class="path-tag read">{{ path }}</span>
                }
              </div>
            </div>

            <div class="section">
              <h4>Allowed Modify Paths</h4>
              <div class="path-list">
                @for (path of agent.canModify; track path) {
                  <span class="path-tag modify">{{ path }}</span>
                }
              </div>
            </div>

            @if (agent.cannotModify.length > 0) {
              <div class="section">
                <h4>Protected Paths</h4>
                <div class="path-list">
                  @for (path of agent.cannotModify; track path) {
                    <span class="path-tag protected">{{ path }}</span>
                  }
                </div>
              </div>
            }

            <div class="section">
              <h4>Available Tools</h4>
              <div class="tool-list">
                @for (tool of agent.tools; track tool) {
                  <span class="tool-tag">{{ tool }}</span>
                }
              </div>
            </div>

            @if (agent.fixLoopLimit) {
              <div class="section">
                <div class="fix-loop-info">
                  <span class="fix-loop-label">Fix Loop Limit</span>
                  <span class="fix-loop-value">{{ agent.fixLoopLimit }} iterations</span>
                </div>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .config-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
      height: 100%;
      overflow-y: auto;
    }
    .header h2 { font-size: 1.8rem; margin-bottom: 4px; }
    .subtitle { color: rgba(255,255,255,0.5); font-size: 0.9rem; }
    .loading { padding: 24px; text-align: center; color: rgba(255,255,255,0.5); }
    .agent-card { padding: 20px; }
    .agent-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
      padding-bottom: 12px;
      border-bottom: 1px solid rgba(255,255,255,0.06);
    }
    .agent-title { display: flex; align-items: center; gap: 12px; }
    .agent-badge {
      background: rgba(56,189,248,0.15);
      color: #38bdf8;
      padding: 4px 12px;
      border-radius: 8px;
      font-size: 0.85rem;
      font-weight: 600;
    }
    .agent-desc { color: rgba(255,255,255,0.6); font-size: 0.9rem; }
    .token-budget { text-align: right; }
    .budget-label {
      display: block;
      font-size: 0.75rem;
      color: rgba(255,255,255,0.4);
      margin-bottom: 2px;
    }
    .budget-value {
      font-size: 1.1rem;
      font-weight: 600;
      color: #818cf8;
      font-family: monospace;
    }
    .agent-body { display: flex; flex-direction: column; gap: 12px; }
    .section h4 {
      margin: 0 0 6px 0;
      font-size: 0.8rem;
      color: rgba(255,255,255,0.4);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .path-list, .tool-list { display: flex; flex-wrap: wrap; gap: 6px; }
    .path-tag {
      padding: 3px 8px;
      border-radius: 4px;
      font-size: 0.8rem;
      font-family: monospace;
    }
    .path-tag.read { background: rgba(34,197,94,0.1); color: #22c55e; }
    .path-tag.modify { background: rgba(56,189,248,0.1); color: #38bdf8; }
    .path-tag.protected { background: rgba(239,68,68,0.1); color: #ef4444; }
    .tool-tag {
      background: rgba(129,140,248,0.1);
      color: #818cf8;
      padding: 3px 8px;
      border-radius: 4px;
      font-size: 0.8rem;
    }
    .fix-loop-info { display: flex; align-items: center; gap: 8px; }
    .fix-loop-label { color: rgba(255,255,255,0.5); font-size: 0.85rem; }
    .fix-loop-value {
      color: #eab308;
      font-weight: 600;
      font-size: 0.85rem;
    }
  `]
})
export class AgentConfigComponent implements OnInit {
  agents = signal<AgentConfig[]>([]);
  loading = signal(true);

  ngOnInit() {
    // Agent configs derived from YAML agent definitions
    this.agents.set([
      {
        name: 'PM',
        description: 'Analyzes issues and produces ticket plans',
        canRead: ['**/*'],
        canModify: [],
        cannotModify: [],
        tokenBudget: 8000,
        tools: ['GitHub Issue Read', 'Repo Tree'],
        fixLoopLimit: undefined
      },
      {
        name: 'Qualifier',
        description: 'Validates ticket plans against quality gates',
        canRead: ['**/*'],
        canModify: [],
        cannotModify: [],
        tokenBudget: 4000,
        tools: ['Schema Validator'],
        fixLoopLimit: undefined
      },
      {
        name: 'Architect',
        description: 'Designs work plans with file-level changes',
        canRead: ['**/*'],
        canModify: [],
        cannotModify: [],
        tokenBudget: 12000,
        tools: ['Repo Tree', 'File Read', 'Dependency Graph'],
        fixLoopLimit: undefined
      },
      {
        name: 'Developer',
        description: 'Implements code changes from work plans',
        canRead: ['backend/**', 'frontend/**', 'docs/**', 'infra/**'],
        canModify: ['backend/src/**', 'frontend/src/**', 'docs/**'],
        cannotModify: ['.github/workflows/**', 'Dockerfile', 'docker-compose.yml'],
        tokenBudget: 16000,
        tools: ['File Read', 'File Write', 'Git Diff', 'Shell (sandboxed)'],
        fixLoopLimit: undefined
      },
      {
        name: 'Review',
        description: 'Persona-based code review with multiple perspectives',
        canRead: ['**/*'],
        canModify: [],
        cannotModify: [],
        tokenBudget: 8000,
        tools: ['Git Diff', 'File Read'],
        fixLoopLimit: undefined
      },
      {
        name: 'Tester',
        description: 'Runs CI and E2E tests with fix loops',
        canRead: ['**/*'],
        canModify: ['backend/src/test/**', 'frontend/src/**/*.spec.ts'],
        cannotModify: ['.github/workflows/**'],
        tokenBudget: 12000,
        tools: ['Shell (sandboxed)', 'Git Diff', 'File Write'],
        fixLoopLimit: 3
      },
      {
        name: 'Writer',
        description: 'Generates documentation updates and PR descriptions',
        canRead: ['**/*'],
        canModify: ['docs/**', 'README.md', 'CHANGELOG.md'],
        cannotModify: ['backend/src/**', 'frontend/src/**'],
        tokenBudget: 6000,
        tools: ['File Read', 'File Write', 'Git Log'],
        fixLoopLimit: undefined
      }
    ]);
    this.loading.set(false);
  }

  formatTokens(tokens: number): string {
    return tokens >= 1000 ? `${(tokens / 1000).toFixed(0)}k` : `${tokens}`;
  }
}
