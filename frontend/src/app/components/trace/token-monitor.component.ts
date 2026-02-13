import { Component, inject, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WorkflowStreamStore } from '../../services/workflow-stream.store';

@Component({
  selector: 'app-token-monitor',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="token-monitor">
      @if (store.isStreaming()) {
        <div class="monitor-card glass-panel">
          <h3>Token Monitor</h3>

          <div class="token-total">
            <span class="total-label">Tokens Consumed</span>
            <span class="total-value">{{ store.tokenConsumption() }}</span>
          </div>

          <div class="agent-breakdown">
            @for (agent of agentBreakdown(); track agent.name) {
              <div class="agent-row">
                <div class="agent-info">
                  <span class="agent-name">{{ agent.name }}</span>
                  <span class="agent-tokens">{{ agent.tokens }} tokens</span>
                </div>
                <div class="bar-bg">
                  <div class="bar-fill"
                       [style.width.%]="agent.percent"
                       [class]="'agent-bar-' + agent.name.toLowerCase()">
                  </div>
                </div>
              </div>
            }
          </div>

          <div class="metrics-row">
            <div class="metric">
              <span class="metric-label">LLM Calls</span>
              <span class="metric-value">{{ store.llmCalls().length }}</span>
            </div>
            <div class="metric">
              <span class="metric-label">Steps Done</span>
              <span class="metric-value">{{ store.completedSteps() }} / 7</span>
            </div>
            <div class="metric">
              <span class="metric-label">Progress</span>
              <span class="metric-value">{{ store.progress() }}%</span>
            </div>
          </div>
        </div>
      } @else {
        <div class="monitor-card glass-panel idle">
          <h3>Token Monitor</h3>
          <p class="idle-text">No active bolt streaming. Start a bolt to see real-time token usage.</p>
        </div>
      }
    </div>
  `,
  styles: [`
    .token-monitor { width: 100%; }
    .monitor-card { padding: 20px; }
    .monitor-card h3 { margin: 0 0 16px 0; font-size: 1rem; }
    .monitor-card.idle { opacity: 0.6; }
    .idle-text { color: rgba(255,255,255,0.4); font-size: 0.85rem; }
    .token-total {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      margin-bottom: 20px;
      padding-bottom: 12px;
      border-bottom: 1px solid rgba(255,255,255,0.06);
    }
    .total-label { color: rgba(255,255,255,0.5); font-size: 0.85rem; }
    .total-value {
      font-size: 1.8rem;
      font-weight: 700;
      color: #818cf8;
      font-family: monospace;
    }
    .agent-breakdown { display: flex; flex-direction: column; gap: 10px; margin-bottom: 20px; }
    .agent-row { display: flex; flex-direction: column; gap: 4px; }
    .agent-info {
      display: flex;
      justify-content: space-between;
      font-size: 0.8rem;
    }
    .agent-name { color: rgba(255,255,255,0.7); }
    .agent-tokens { color: rgba(255,255,255,0.4); font-family: monospace; }
    .bar-bg {
      height: 6px;
      background: rgba(255,255,255,0.05);
      border-radius: 3px;
      overflow: hidden;
    }
    .bar-fill {
      height: 100%;
      border-radius: 3px;
      transition: width 0.3s ease;
      background: #818cf8;
    }
    .agent-bar-pm { background: #38bdf8; }
    .agent-bar-qualifier { background: #22c55e; }
    .agent-bar-architect { background: #a78bfa; }
    .agent-bar-developer { background: #f59e0b; }
    .agent-bar-review { background: #ec4899; }
    .agent-bar-tester { background: #ef4444; }
    .agent-bar-writer { background: #14b8a6; }
    .metrics-row {
      display: flex;
      justify-content: space-between;
      padding-top: 12px;
      border-top: 1px solid rgba(255,255,255,0.06);
    }
    .metric { text-align: center; }
    .metric-label {
      display: block;
      font-size: 0.7rem;
      color: rgba(255,255,255,0.4);
      margin-bottom: 2px;
    }
    .metric-value {
      font-size: 1rem;
      font-weight: 600;
      color: white;
      font-family: monospace;
    }
  `]
})
export class TokenMonitorComponent {
  store = inject(WorkflowStreamStore);

  agentBreakdown = computed(() => {
    const llmCalls = this.store.llmCalls();
    const agentTokens: Record<string, number> = {};

    for (const call of llmCalls) {
      const agent = call.agentName || 'unknown';
      agentTokens[agent] = (agentTokens[agent] || 0) + (call.tokensUsed || 0);
    }

    const total = this.store.tokenConsumption();
    return Object.entries(agentTokens)
      .map(([name, tokens]) => ({
        name,
        tokens,
        percent: total > 0 ? (tokens / total) * 100 : 0
      }))
      .sort((a, b) => b.tokens - a.tokens);
  });
}
