import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TimeTravelSnapshot } from '../models/collaboration.model';

@Component({
  selector: 'app-event-sourcing-viz',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="event-viz-container" *ngIf="snapshot">
      <div class="viz-header">
        <span class="header-label">📜 EVENT SOURCING VISUALIZATION</span>
      </div>

      <div class="event-details">
        <div class="detail-row">
          <span class="label">Event ID:</span>
          <span class="value mono">{{ snapshot.eventId }}</span>
        </div>
        <div class="detail-row">
          <span class="label">User:</span>
          <span class="value">{{ snapshot.userId }}</span>
        </div>
        <div class="detail-row">
          <span class="label">Type:</span>
          <span class="value event-type" [ngClass]="'type-' + snapshot.eventType.toLowerCase()">
            {{ snapshot.eventType }}
          </span>
        </div>
        <div class="detail-row">
          <span class="label">Time:</span>
          <span class="value">{{ formatTimestamp(snapshot.timestamp) }}</span>
        </div>
      </div>

      <div class="diff-container" *ngIf="hasDiff()">
        <div class="diff-header">🔄 State Changes (Before → After)</div>
        
        <div class="diff-list">
          <div *ngFor="let key of getDiffKeys()" class="diff-item">
            <div class="diff-key">{{ key }}</div>
            <div class="diff-values">
              <div class="before-value">
                <span class="label-tag before">BEFORE</span>
                <pre class="value-content">{{ formatValue(getDiffBefore(key)) }}</pre>
              </div>
              <div class="arrow">→</div>
              <div class="after-value">
                <span class="label-tag after">AFTER</span>
                <pre class="value-content">{{ formatValue(getDiffAfter(key)) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="no-changes" *ngIf="!hasDiff()">
        <span class="icon">ℹ️</span>
        <span>No state changes in this event</span>
      </div>

      <div class="full-state-toggle">
        <button class="toggle-btn" (click)="showFullState = !showFullState">
          {{ showFullState ? '▼ Hide Full State' : '▶ Show Full State' }}
        </button>
      </div>

      <div class="full-state-viewer" *ngIf="showFullState">
        <div class="state-section">
          <div class="state-header">State Before</div>
          <pre class="state-json">{{ formatJson(snapshot.stateBefore) }}</pre>
        </div>
        <div class="state-section">
          <div class="state-header">State After</div>
          <pre class="state-json">{{ formatJson(snapshot.stateAfter) }}</pre>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .event-viz-container {
      background: rgba(15, 23, 42, 0.95);
      border: 1px solid rgba(139, 92, 246, 0.3);
      border-radius: 12px;
      padding: 16px;
      margin: 16px 0;
    }

    .viz-header {
      margin-bottom: 16px;
      padding-bottom: 12px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }

    .header-label {
      font-size: 0.7rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: #a78bfa;
    }

    .event-details {
      display: grid;
      gap: 8px;
      margin-bottom: 16px;
    }

    .detail-row {
      display: flex;
      align-items: center;
      gap: 12px;
      font-size: 0.75rem;
    }

    .detail-row .label {
      color: rgba(255, 255, 255, 0.5);
      min-width: 80px;
    }

    .detail-row .value {
      color: rgba(255, 255, 255, 0.9);
    }

    .mono {
      font-family: monospace;
      font-size: 0.7rem;
    }

    .event-type {
      padding: 3px 10px;
      border-radius: 6px;
      font-weight: 700;
      font-size: 0.7rem;
    }

    .event-type.type-graft {
      background: rgba(139, 92, 246, 0.2);
      color: #a78bfa;
    }

    .event-type.type-prune {
      background: rgba(239, 68, 68, 0.2);
      color: #ef4444;
    }

    .event-type.type-flag {
      background: rgba(234, 179, 8, 0.2);
      color: #eab308;
    }

    .diff-container {
      margin: 16px 0;
    }

    .diff-header {
      font-size: 0.75rem;
      font-weight: 700;
      color: #a78bfa;
      margin-bottom: 12px;
    }

    .diff-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .diff-item {
      background: rgba(139, 92, 246, 0.05);
      border: 1px solid rgba(139, 92, 246, 0.2);
      border-radius: 8px;
      padding: 12px;
    }

    .diff-key {
      font-size: 0.7rem;
      font-weight: 700;
      font-family: monospace;
      color: #a78bfa;
      margin-bottom: 8px;
    }

    .diff-values {
      display: grid;
      grid-template-columns: 1fr auto 1fr;
      gap: 12px;
      align-items: center;
    }

    .before-value,
    .after-value {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .label-tag {
      font-size: 0.6rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      padding: 2px 6px;
      border-radius: 4px;
      width: fit-content;
    }

    .label-tag.before {
      background: rgba(239, 68, 68, 0.2);
      color: #ef4444;
    }

    .label-tag.after {
      background: rgba(34, 197, 94, 0.2);
      color: #22c55e;
    }

    .value-content {
      font-size: 0.7rem;
      font-family: monospace;
      color: rgba(255, 255, 255, 0.8);
      background: rgba(0, 0, 0, 0.2);
      padding: 8px;
      border-radius: 4px;
      margin: 0;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 200px;
      overflow-y: auto;
    }

    .arrow {
      font-size: 1.2rem;
      color: rgba(255, 255, 255, 0.3);
      text-align: center;
    }

    .no-changes {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 16px;
      background: rgba(100, 116, 139, 0.1);
      border: 1px solid rgba(100, 116, 139, 0.2);
      border-radius: 8px;
      color: rgba(255, 255, 255, 0.5);
      font-size: 0.75rem;
      margin: 16px 0;
    }

    .full-state-toggle {
      margin: 16px 0;
    }

    .toggle-btn {
      padding: 6px 12px;
      background: rgba(139, 92, 246, 0.1);
      border: 1px solid rgba(139, 92, 246, 0.3);
      border-radius: 6px;
      color: #a78bfa;
      font-size: 0.7rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .toggle-btn:hover {
      background: rgba(139, 92, 246, 0.2);
      border-color: #a78bfa;
    }

    .full-state-viewer {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
      margin-top: 12px;
    }

    .state-section {
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid rgba(139, 92, 246, 0.2);
      border-radius: 8px;
      padding: 12px;
    }

    .state-header {
      font-size: 0.7rem;
      font-weight: 700;
      color: #a78bfa;
      margin-bottom: 8px;
    }

    .state-json {
      font-size: 0.65rem;
      font-family: monospace;
      color: rgba(255, 255, 255, 0.7);
      margin: 0;
      white-space: pre-wrap;
      word-break: break-word;
      max-height: 300px;
      overflow-y: auto;
    }
  `]
})
export class EventSourcingVizComponent implements OnChanges {
  @Input() snapshot: TimeTravelSnapshot | null = null;
  
  showFullState = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['snapshot']) {
      this.showFullState = false;
    }
  }

  hasDiff(): boolean {
    return this.snapshot?.diff && Object.keys(this.snapshot.diff).length > 0;
  }

  getDiffKeys(): string[] {
    if (!this.snapshot?.diff) return [];
    return Object.keys(this.snapshot.diff);
  }

  getDiffBefore(key: string): any {
    return this.snapshot?.diff[key]?.before;
  }

  getDiffAfter(key: string): any {
    return this.snapshot?.diff[key]?.after;
  }

  formatValue(value: any): string {
    if (value === null || value === undefined) {
      return 'null';
    }
    if (typeof value === 'object') {
      return JSON.stringify(value, null, 2);
    }
    return String(value);
  }

  formatJson(obj: any): string {
    if (!obj || Object.keys(obj).length === 0) {
      return '{}';
    }
    return JSON.stringify(obj, null, 2);
  }

  formatTimestamp(timestamp: string): string {
    const date = new Date(timestamp);
    return date.toLocaleString();
  }
}
