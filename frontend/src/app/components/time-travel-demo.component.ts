import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TimeTravelControlsComponent } from './time-travel-controls.component';
import { EventSourcingVizComponent } from './event-sourcing-viz.component';
import { CollaborationAnalyticsDashboardComponent } from './collaboration-analytics-dashboard.component';
import { TimeTravelSnapshot } from '../models/collaboration.model';

@Component({
  selector: 'app-time-travel-demo',
  standalone: true,
  imports: [
    CommonModule,
    TimeTravelControlsComponent,
    EventSourcingVizComponent,
    CollaborationAnalyticsDashboardComponent
  ],
  template: `
    <div class="time-travel-demo">
      <div class="demo-header">
        <h2 class="demo-title">🔬 Collaboration Time-Travel Debugger</h2>
        <p class="demo-description">
          Step through collaboration events, visualize state changes, and analyze patterns
        </p>
      </div>

      <div class="demo-tabs">
        <button 
          class="tab-btn"
          [class.active]="activeTab === 'playback'"
          (click)="activeTab = 'playback'">
          ⏯️ Playback
        </button>
        <button 
          class="tab-btn"
          [class.active]="activeTab === 'visualization'"
          (click)="activeTab = 'visualization'">
          📜 Event Sourcing
        </button>
        <button 
          class="tab-btn"
          [class.active]="activeTab === 'analytics'"
          (click)="activeTab = 'analytics'">
          📊 Analytics
        </button>
      </div>

      <div class="demo-content">
        <div *ngIf="activeTab === 'playback'" class="tab-content">
          <app-time-travel-controls
            [runId]="runId"
            (snapshotChanged)="onSnapshotChanged($event)">
          </app-time-travel-controls>
          
          <div class="current-snapshot-preview" *ngIf="currentSnapshot">
            <div class="preview-header">Current Event Preview</div>
            <div class="preview-content">
              <div class="preview-field">
                <span class="field-label">Type:</span>
                <span class="field-value">{{ currentSnapshot.eventType }}</span>
              </div>
              <div class="preview-field">
                <span class="field-label">User:</span>
                <span class="field-value">{{ currentSnapshot.userId }}</span>
              </div>
              <div class="preview-field">
                <span class="field-label">Description:</span>
                <span class="field-value">{{ currentSnapshot.description }}</span>
              </div>
            </div>
          </div>
        </div>

        <div *ngIf="activeTab === 'visualization'" class="tab-content">
          <app-event-sourcing-viz
            [snapshot]="currentSnapshot">
          </app-event-sourcing-viz>
        </div>

        <div *ngIf="activeTab === 'analytics'" class="tab-content">
          <app-collaboration-analytics-dashboard
            [runId]="runId">
          </app-collaboration-analytics-dashboard>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .time-travel-demo {
      background: rgba(15, 23, 42, 0.98);
      border: 2px solid rgba(56, 189, 248, 0.4);
      border-radius: 16px;
      padding: 20px;
      margin: 20px 0;
    }

    .demo-header {
      margin-bottom: 24px;
      text-align: center;
    }

    .demo-title {
      font-size: 1.4rem;
      font-weight: 800;
      color: #38bdf8;
      margin: 0 0 8px 0;
      letter-spacing: -0.02em;
    }

    .demo-description {
      font-size: 0.85rem;
      color: rgba(255, 255, 255, 0.6);
      margin: 0;
      line-height: 1.5;
    }

    .demo-tabs {
      display: flex;
      gap: 8px;
      margin-bottom: 20px;
      border-bottom: 2px solid rgba(255, 255, 255, 0.1);
      padding-bottom: 4px;
    }

    .tab-btn {
      padding: 10px 20px;
      background: transparent;
      border: none;
      border-bottom: 3px solid transparent;
      color: rgba(255, 255, 255, 0.5);
      font-size: 0.8rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.2s;
      position: relative;
      bottom: -2px;
    }

    .tab-btn:hover {
      color: rgba(255, 255, 255, 0.8);
      background: rgba(255, 255, 255, 0.03);
    }

    .tab-btn.active {
      color: #38bdf8;
      border-bottom-color: #38bdf8;
    }

    .demo-content {
      min-height: 400px;
    }

    .tab-content {
      animation: fadeIn 0.3s ease-in;
    }

    @keyframes fadeIn {
      from {
        opacity: 0;
        transform: translateY(10px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .current-snapshot-preview {
      margin-top: 20px;
      padding: 16px;
      background: rgba(56, 189, 248, 0.05);
      border: 1px solid rgba(56, 189, 248, 0.2);
      border-radius: 8px;
    }

    .preview-header {
      font-size: 0.75rem;
      font-weight: 700;
      color: #38bdf8;
      margin-bottom: 12px;
      letter-spacing: 0.05em;
    }

    .preview-content {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .preview-field {
      display: flex;
      gap: 8px;
      font-size: 0.75rem;
    }

    .field-label {
      color: rgba(255, 255, 255, 0.5);
      min-width: 100px;
      font-weight: 600;
    }

    .field-value {
      color: rgba(255, 255, 255, 0.9);
    }
  `]
})
export class TimeTravelDemoComponent {
  @Input() runId!: string;
  
  activeTab: 'playback' | 'visualization' | 'analytics' = 'playback';
  currentSnapshot: TimeTravelSnapshot | null = null;

  onSnapshotChanged(snapshot: TimeTravelSnapshot): void {
    this.currentSnapshot = snapshot;
  }
}
