import { Component, Input, computed, signal, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface JudgeVerdict {
  run_id?: string;
  checkpoint?: string;
  artifact_key?: string;
  rubric_name?: string;
  overall_score: number;
  verdict: 'pass' | 'conditional_pass' | 'veto';
  confidence?: number;
  per_criterion?: CriterionScore[];
  findings?: Finding[];
  recommendation?: string;
  voting_metadata?: VotingMetadata;
}

export interface CriterionScore {
  criterion: string;
  weight: number;
  score: number;
  level: 'excellent' | 'good' | 'acceptable' | 'failing';
  evidence?: string;
}

export interface Finding {
  severity: 'critical' | 'high' | 'medium' | 'low';
  description: string;
  location?: string;
  suggested_fix?: string;
}

export interface VotingMetadata {
  voter_count: number;
  quorum_met: boolean;
  individual_verdicts: string[];
  aggregated_verdict: string;
  agreement_rate: number;
  dissenting_opinions?: string[];
}

@Component({
  selector: 'app-confidence-signal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="confidence-signal">
      <!-- Header row: gauge + verdict badge + score -->
      <div class="signal-header">
        <div class="gauge-wrap">
          <svg viewBox="0 0 80 80" class="gauge-svg" [attr.aria-label]="'Score: ' + pct() + '%'">
            <!-- Background arc -->
            <circle cx="40" cy="40" r="30" fill="none"
                    stroke="rgba(255,255,255,0.06)"
                    stroke-width="7"
                    stroke-dasharray="157 188"
                    stroke-dashoffset="-15.5"
                    stroke-linecap="round"
                    transform="rotate(-214 40 40)" />
            <!-- Filled arc -->
            <circle cx="40" cy="40" r="30" fill="none"
                    [attr.stroke]="gaugeColor()"
                    stroke-width="7"
                    [attr.stroke-dasharray]="gaugeDash() + ' 188'"
                    stroke-dashoffset="-15.5"
                    stroke-linecap="round"
                    transform="rotate(-214 40 40)"
                    style="transition: stroke-dasharray 0.8s ease;" />
            <!-- Score label -->
            <text x="40" y="45" text-anchor="middle"
                  [attr.fill]="gaugeColor()"
                  font-size="14" font-weight="700" font-family="monospace">
              {{ pct() }}%
            </text>
          </svg>
        </div>

        <div class="verdict-info">
          <span class="verdict-badge" [ngClass]="verdictClass()">
            {{ verdictLabel() }}
          </span>
          <div class="score-detail">
            <span class="score-main">{{ (verdict.overall_score * 100).toFixed(0) }}</span>
            <span class="score-denom">/100</span>
          </div>
          <div *ngIf="verdict.confidence !== undefined" class="confidence-row">
            <span class="meta-label">Confidence</span>
            <div class="conf-bar-track">
              <div class="conf-bar-fill" [style.width.%]="verdict.confidence * 100"
                   [style.background]="gaugeColor()"></div>
            </div>
            <span class="meta-val">{{ (verdict.confidence * 100).toFixed(0) }}%</span>
          </div>
          <div *ngIf="verdict.rubric_name" class="rubric-name">{{ verdict.rubric_name }}</div>
        </div>
      </div>

      <!-- Per-criterion bars -->
      <div *ngIf="verdict.per_criterion?.length" class="criteria-section">
        <h5 class="section-label">Criteria</h5>
        <div class="criteria-list">
          <div *ngFor="let c of verdict.per_criterion" class="criterion-row">
            <div class="criterion-meta">
              <span class="criterion-name">{{ c.criterion }}</span>
              <span class="criterion-level" [ngClass]="levelClass(c.level)">{{ c.level }}</span>
            </div>
            <div class="criterion-bar-track">
              <div class="criterion-bar-fill"
                   [ngClass]="levelClass(c.level)"
                   [style.width.%]="c.score * 100"
                   [title]="c.evidence || ''">
              </div>
            </div>
            <span class="criterion-score">{{ (c.score * 100).toFixed(0) }}</span>
          </div>
        </div>
      </div>

      <!-- Findings -->
      <div *ngIf="verdict.findings?.length" class="findings-section">
        <h5 class="section-label">
          Findings
          <span class="findings-count">{{ verdict.findings!.length }}</span>
        </h5>
        <div class="findings-list">
          <div *ngFor="let f of verdict.findings" class="finding-item">
            <div class="finding-header">
              <span class="severity-chip" [ngClass]="'sev-' + f.severity">{{ f.severity }}</span>
              <span *ngIf="f.location" class="finding-location mono">{{ f.location }}</span>
            </div>
            <p class="finding-desc">{{ f.description }}</p>
            <p *ngIf="f.suggested_fix" class="finding-fix">💡 {{ f.suggested_fix }}</p>
          </div>
        </div>
      </div>

      <!-- Recommendation -->
      <div *ngIf="verdict.recommendation" class="recommendation-section">
        <h5 class="section-label">Recommendation</h5>
        <p class="recommendation-text">{{ verdict.recommendation }}</p>
      </div>

      <!-- Voting metadata -->
      <div *ngIf="verdict.voting_metadata" class="voting-section">
        <h5 class="section-label">Voting</h5>
        <div class="voting-row">
          <div class="voting-stat">
            <span class="meta-label">Voters</span>
            <span class="meta-val">{{ verdict.voting_metadata.voter_count }}</span>
          </div>
          <div class="voting-stat">
            <span class="meta-label">Agreement</span>
            <span class="meta-val">{{ (verdict.voting_metadata.agreement_rate * 100).toFixed(0) }}%</span>
          </div>
          <div class="voting-stat">
            <span class="meta-label">Quorum</span>
            <span class="meta-val" [ngClass]="verdict.voting_metadata.quorum_met ? 'yes' : 'no'">
              {{ verdict.voting_metadata.quorum_met ? 'Met' : 'Not met' }}
            </span>
          </div>
        </div>
        <div *ngIf="verdict.voting_metadata.dissenting_opinions?.length" class="dissents">
          <span class="meta-label">Dissents:</span>
          <span *ngFor="let d of verdict.voting_metadata.dissenting_opinions" class="dissent-tag">{{ d }}</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .confidence-signal {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    /* --- Header --- */
    .signal-header {
      display: flex;
      align-items: flex-start;
      gap: 20px;
    }
    .gauge-wrap {
      flex-shrink: 0;
      width: 80px;
      height: 80px;
    }
    .gauge-svg { width: 100%; height: 100%; }

    .verdict-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 6px;
      justify-content: center;
    }
    .verdict-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.7rem;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      align-self: flex-start;
    }
    .verdict-pass    { background: rgba(34,197,94,0.2);  color: #22c55e; border: 1px solid rgba(34,197,94,0.3); }
    .verdict-conditional_pass { background: rgba(234,179,8,0.2); color: #eab308; border: 1px solid rgba(234,179,8,0.3); }
    .verdict-veto    { background: rgba(239,68,68,0.2);  color: #ef4444; border: 1px solid rgba(239,68,68,0.3); }

    .score-detail { display: flex; align-items: baseline; gap: 2px; }
    .score-main { font-size: 2rem; font-weight: 700; color: white; line-height: 1; font-family: monospace; }
    .score-denom { color: rgba(255,255,255,0.3); font-size: 1rem; }

    .confidence-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .conf-bar-track {
      flex: 1;
      height: 4px;
      background: rgba(255,255,255,0.08);
      border-radius: 2px;
      overflow: hidden;
    }
    .conf-bar-fill {
      height: 100%;
      border-radius: 2px;
      transition: width 0.6s ease;
    }
    .meta-label { font-size: 0.72rem; color: rgba(255,255,255,0.4); white-space: nowrap; }
    .meta-val   { font-size: 0.8rem; color: rgba(255,255,255,0.8); font-weight: 600; }
    .meta-val.yes { color: #22c55e; }
    .meta-val.no  { color: #ef4444; }
    .rubric-name { font-size: 0.72rem; color: rgba(255,255,255,0.3); font-style: italic; }

    /* --- Section label --- */
    .section-label {
      margin: 0 0 8px 0;
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      color: rgba(255,255,255,0.35);
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 6px;
    }
    .findings-count {
      background: rgba(255,255,255,0.07);
      color: rgba(255,255,255,0.5);
      font-size: 0.65rem;
      padding: 1px 6px;
      border-radius: 10px;
      font-weight: 600;
    }

    /* --- Criteria --- */
    .criteria-list { display: flex; flex-direction: column; gap: 6px; }
    .criterion-row {
      display: grid;
      grid-template-columns: 1fr auto;
      grid-template-rows: auto auto;
      gap: 3px 8px;
      align-items: center;
    }
    .criterion-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      grid-column: 1;
    }
    .criterion-name { font-size: 0.78rem; color: rgba(255,255,255,0.75); }
    .criterion-level {
      font-size: 0.65rem;
      font-weight: 700;
      text-transform: uppercase;
      padding: 1px 6px;
      border-radius: 8px;
    }
    .criterion-bar-track {
      height: 4px;
      background: rgba(255,255,255,0.06);
      border-radius: 2px;
      overflow: hidden;
      grid-column: 1;
    }
    .criterion-bar-fill {
      height: 100%;
      border-radius: 2px;
      transition: width 0.6s ease;
    }
    .criterion-score {
      font-size: 0.72rem;
      font-family: monospace;
      color: rgba(255,255,255,0.5);
      grid-column: 2;
      grid-row: 1 / 3;
      align-self: center;
    }

    /* Level color map */
    .level-excellent { background: rgba(34,197,94,0.2);  color: #22c55e; }
    .level-good      { background: rgba(56,189,248,0.2); color: #38bdf8; }
    .level-acceptable{ background: rgba(234,179,8,0.2);  color: #eab308; }
    .level-failing   { background: rgba(239,68,68,0.2);  color: #ef4444; }

    /* Bar fill color map */
    .criterion-bar-fill.level-excellent { background: #22c55e; }
    .criterion-bar-fill.level-good      { background: #38bdf8; }
    .criterion-bar-fill.level-acceptable{ background: #eab308; }
    .criterion-bar-fill.level-failing   { background: #ef4444; }

    /* --- Findings --- */
    .findings-list { display: flex; flex-direction: column; gap: 8px; }
    .finding-item {
      padding: 8px 10px;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.04);
      border-radius: 6px;
    }
    .finding-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;
    }
    .severity-chip {
      font-size: 0.65rem;
      font-weight: 800;
      text-transform: uppercase;
      padding: 2px 6px;
      border-radius: 8px;
      letter-spacing: 0.04em;
    }
    .sev-critical { background: rgba(239, 68, 68, 0.25); color: #ef4444; }
    .sev-high     { background: rgba(234,179,  8, 0.2);  color: #f97316; }
    .sev-medium   { background: rgba(234,179,  8, 0.15); color: #eab308; }
    .sev-low      { background: rgba(148,163,184,0.15);  color: #94a3b8; }
    .finding-location { font-size: 0.7rem; color: rgba(255,255,255,0.35); font-family: monospace; }
    .finding-desc { margin: 0 0 4px; font-size: 0.8rem; color: rgba(255,255,255,0.7); line-height: 1.4; }
    .finding-fix  { margin: 0; font-size: 0.75rem; color: rgba(56,189,248,0.8); line-height: 1.4; }

    /* --- Recommendation --- */
    .recommendation-text { margin: 0; font-size: 0.82rem; color: rgba(255,255,255,0.65); line-height: 1.5; }

    /* --- Voting --- */
    .voting-row { display: flex; gap: 24px; margin-bottom: 6px; }
    .voting-stat { display: flex; flex-direction: column; gap: 2px; }
    .dissents { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin-top: 4px; }
    .dissent-tag {
      background: rgba(239,68,68,0.1);
      color: rgba(239,68,68,0.7);
      font-size: 0.68rem;
      padding: 2px 8px;
      border-radius: 8px;
      border: 1px solid rgba(239,68,68,0.2);
    }
  `]
})
export class ConfidenceSignalComponent implements OnChanges {
  @Input({ required: true }) verdict!: JudgeVerdict;

  pct = signal(0);

  ngOnChanges(changes: SimpleChanges) {
    if (changes['verdict'] && this.verdict) {
      this.pct.set(Math.round(this.verdict.overall_score * 100));
    }
  }

  // 157 = circumference of 75% of a circle (r=30, full circ ~188)
  gaugeDash(): number {
    return Math.min(157, (this.verdict.overall_score ?? 0) * 157);
  }

  gaugeColor(): string {
    const s = this.verdict.overall_score ?? 0;
    if (s >= 0.8) return '#22c55e';
    if (s >= 0.6) return '#38bdf8';
    if (s >= 0.4) return '#eab308';
    return '#ef4444';
  }

  verdictClass(): string {
    return 'verdict-' + (this.verdict.verdict ?? 'veto');
  }

  verdictLabel(): string {
    switch (this.verdict.verdict) {
      case 'pass':             return '✓ Pass';
      case 'conditional_pass': return '~ Conditional';
      case 'veto':             return '✕ Veto';
      default:                 return this.verdict.verdict ?? '';
    }
  }

  levelClass(level: string): string {
    return 'level-' + level;
  }
}
