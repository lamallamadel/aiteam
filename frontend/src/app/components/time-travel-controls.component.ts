import { Component, Input, Output, EventEmitter, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TimeTravelSnapshot } from '../models/collaboration.model';
import { TimeTravelService } from '../services/time-travel.service';
import { Subscription, interval } from 'rxjs';

@Component({
  selector: 'app-time-travel-controls',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="time-travel-container">
      <div class="controls-header">
        <span class="header-label">⏱️ TIME-TRAVEL DEBUGGER</span>
        <div class="status-badge" [class.playing]="isPlaying">
          {{ isPlaying ? 'PLAYING' : 'PAUSED' }}
        </div>
      </div>

      <div class="timeline-container" *ngIf="snapshots.length > 0">
        <div class="timeline-track">
          <input 
            type="range" 
            class="timeline-slider"
            [min]="0" 
            [max]="snapshots.length - 1"
            [(ngModel)]="currentIndex"
            (input)="onSliderChange()"
            [disabled]="isPlaying">
          
          <div class="timeline-markers">
            <div *ngFor="let snapshot of snapshots; let i = index"
                 class="timeline-marker"
                 [class.active]="i === currentIndex"
                 [class.graft]="snapshot.eventType === 'GRAFT'"
                 [class.prune]="snapshot.eventType === 'PRUNE'"
                 [class.flag]="snapshot.eventType === 'FLAG'"
                 [style.left.%]="(i / (snapshots.length - 1)) * 100"
                 (click)="jumpToEvent(i)"
                 [title]="snapshot.description">
            </div>
          </div>
        </div>

        <div class="timeline-info">
          <span class="event-counter">{{ currentIndex + 1 }} / {{ snapshots.length }}</span>
          <span class="timestamp">{{ formatTimestamp(currentSnapshot?.timestamp) }}</span>
        </div>
      </div>

      <div class="playback-controls">
        <button class="control-btn" (click)="jumpToStart()" [disabled]="currentIndex === 0">
          ⏮ Start
        </button>
        <button class="control-btn" (click)="stepBackward()" [disabled]="currentIndex === 0">
          ⏪ Step Back
        </button>
        <button class="control-btn primary" (click)="togglePlayback()">
          {{ isPlaying ? '⏸ Pause' : '▶ Play' }}
        </button>
        <button class="control-btn" (click)="stepForward()" [disabled]="currentIndex >= snapshots.length - 1">
          Step Forward ⏩
        </button>
        <button class="control-btn" (click)="jumpToEnd()" [disabled]="currentIndex === snapshots.length - 1">
          End ⏭
        </button>
      </div>

      <div class="speed-control">
        <label>Playback Speed:</label>
        <select [(ngModel)]="playbackSpeed" class="speed-select">
          <option [value]="0.25">0.25x</option>
          <option [value]="0.5">0.5x</option>
          <option [value]="1">1x</option>
          <option [value]="2">2x</option>
          <option [value]="4">4x</option>
        </select>
      </div>

      <div class="current-event" *ngIf="currentSnapshot">
        <div class="event-header">
          <span class="event-type" [ngClass]="'type-' + currentSnapshot.eventType.toLowerCase()">
            {{ currentSnapshot.eventType }}
          </span>
          <span class="event-user">by {{ currentSnapshot.userId }}</span>
        </div>
        <div class="event-description">{{ currentSnapshot.description }}</div>
      </div>

      <div class="export-controls">
        <button class="export-btn" (click)="exportJson()">📄 Export JSON</button>
        <button class="export-btn" (click)="exportCsv()">📊 Export CSV</button>
      </div>
    </div>
  `,
  styles: [`
    .time-travel-container {
      background: rgba(15, 23, 42, 0.95);
      border: 1px solid rgba(56, 189, 248, 0.3);
      border-radius: 12px;
      padding: 16px;
      margin: 16px 0;
    }

    .controls-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;
    }

    .header-label {
      font-size: 0.7rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: #38bdf8;
    }

    .status-badge {
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 0.65rem;
      font-weight: 700;
      background: rgba(100, 116, 139, 0.2);
      color: #64748b;
      border: 1px solid rgba(100, 116, 139, 0.3);
    }

    .status-badge.playing {
      background: rgba(34, 197, 94, 0.2);
      color: #22c55e;
      border-color: rgba(34, 197, 94, 0.3);
    }

    .timeline-container {
      margin: 16px 0;
    }

    .timeline-track {
      position: relative;
      padding: 20px 0;
    }

    .timeline-slider {
      width: 100%;
      height: 4px;
      background: rgba(56, 189, 248, 0.2);
      outline: none;
      border-radius: 2px;
      cursor: pointer;
    }

    .timeline-slider::-webkit-slider-thumb {
      appearance: none;
      width: 16px;
      height: 16px;
      background: #38bdf8;
      border-radius: 50%;
      cursor: pointer;
      border: 2px solid rgba(15, 23, 42, 1);
    }

    .timeline-slider::-moz-range-thumb {
      width: 16px;
      height: 16px;
      background: #38bdf8;
      border-radius: 50%;
      cursor: pointer;
      border: 2px solid rgba(15, 23, 42, 1);
    }

    .timeline-markers {
      position: absolute;
      top: 20px;
      left: 0;
      right: 0;
      height: 4px;
      pointer-events: none;
    }

    .timeline-marker {
      position: absolute;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: rgba(100, 116, 139, 0.5);
      transform: translate(-50%, -2px);
      pointer-events: all;
      cursor: pointer;
      transition: all 0.2s;
    }

    .timeline-marker:hover {
      transform: translate(-50%, -2px) scale(1.5);
    }

    .timeline-marker.active {
      background: #38bdf8;
      transform: translate(-50%, -2px) scale(1.3);
    }

    .timeline-marker.graft {
      background: #a78bfa;
    }

    .timeline-marker.prune {
      background: #ef4444;
    }

    .timeline-marker.flag {
      background: #eab308;
    }

    .timeline-info {
      display: flex;
      justify-content: space-between;
      margin-top: 8px;
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.5);
    }

    .playback-controls {
      display: flex;
      gap: 8px;
      justify-content: center;
      margin: 16px 0;
    }

    .control-btn {
      padding: 8px 16px;
      background: rgba(56, 189, 248, 0.1);
      border: 1px solid rgba(56, 189, 248, 0.3);
      border-radius: 8px;
      color: #38bdf8;
      font-size: 0.75rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .control-btn:hover:not(:disabled) {
      background: rgba(56, 189, 248, 0.2);
      border-color: #38bdf8;
    }

    .control-btn.primary {
      background: rgba(56, 189, 248, 0.2);
      border-color: #38bdf8;
    }

    .control-btn:disabled {
      opacity: 0.3;
      cursor: not-allowed;
    }

    .speed-control {
      display: flex;
      align-items: center;
      gap: 8px;
      justify-content: center;
      margin: 12px 0;
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.6);
    }

    .speed-select {
      padding: 4px 8px;
      background: rgba(15, 23, 42, 0.9);
      border: 1px solid rgba(56, 189, 248, 0.3);
      border-radius: 6px;
      color: #38bdf8;
      font-size: 0.7rem;
      cursor: pointer;
    }

    .current-event {
      margin: 16px 0;
      padding: 12px;
      background: rgba(56, 189, 248, 0.05);
      border: 1px solid rgba(56, 189, 248, 0.2);
      border-radius: 8px;
    }

    .event-header {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-bottom: 6px;
    }

    .event-type {
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.65rem;
      font-weight: 700;
      font-family: monospace;
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

    .event-type.type-user_join,
    .event-type.type-user_leave {
      background: rgba(59, 130, 246, 0.2);
      color: #3b82f6;
    }

    .event-user {
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.5);
    }

    .event-description {
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.7);
      line-height: 1.4;
    }

    .export-controls {
      display: flex;
      gap: 8px;
      justify-content: center;
      margin-top: 16px;
      padding-top: 16px;
      border-top: 1px solid rgba(255, 255, 255, 0.1);
    }

    .export-btn {
      padding: 6px 14px;
      background: rgba(34, 197, 94, 0.1);
      border: 1px solid rgba(34, 197, 94, 0.3);
      border-radius: 8px;
      color: #22c55e;
      font-size: 0.7rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }

    .export-btn:hover {
      background: rgba(34, 197, 94, 0.2);
      border-color: #22c55e;
    }
  `]
})
export class TimeTravelControlsComponent implements OnInit, OnDestroy {
  @Input() runId!: string;
  @Output() snapshotChanged = new EventEmitter<TimeTravelSnapshot>();

  snapshots: TimeTravelSnapshot[] = [];
  currentIndex = 0;
  isPlaying = false;
  playbackSpeed = 1;

  private playbackSubscription?: Subscription;

  constructor(private timeTravelService: TimeTravelService) {}

  ngOnInit(): void {
    this.loadHistory();
  }

  ngOnDestroy(): void {
    this.stopPlayback();
  }

  loadHistory(): void {
    this.timeTravelService.getEventHistory(this.runId).subscribe({
      next: (snapshots) => {
        this.snapshots = snapshots;
        if (snapshots.length > 0) {
          this.emitCurrentSnapshot();
        }
      },
      error: (err) => console.error('Failed to load event history:', err)
    });
  }

  get currentSnapshot(): TimeTravelSnapshot | null {
    return this.snapshots[this.currentIndex] || null;
  }

  onSliderChange(): void {
    this.emitCurrentSnapshot();
  }

  jumpToEvent(index: number): void {
    if (index >= 0 && index < this.snapshots.length) {
      this.currentIndex = index;
      this.emitCurrentSnapshot();
    }
  }

  jumpToStart(): void {
    this.currentIndex = 0;
    this.emitCurrentSnapshot();
  }

  jumpToEnd(): void {
    this.currentIndex = this.snapshots.length - 1;
    this.emitCurrentSnapshot();
  }

  stepForward(): void {
    if (this.currentIndex < this.snapshots.length - 1) {
      this.currentIndex++;
      this.emitCurrentSnapshot();
    } else {
      this.stopPlayback();
    }
  }

  stepBackward(): void {
    if (this.currentIndex > 0) {
      this.currentIndex--;
      this.emitCurrentSnapshot();
    }
  }

  togglePlayback(): void {
    if (this.isPlaying) {
      this.stopPlayback();
    } else {
      this.startPlayback();
    }
  }

  startPlayback(): void {
    if (this.currentIndex >= this.snapshots.length - 1) {
      this.currentIndex = 0;
    }
    
    this.isPlaying = true;
    const intervalMs = 1000 / this.playbackSpeed;
    
    this.playbackSubscription = interval(intervalMs).subscribe(() => {
      this.stepForward();
    });
  }

  stopPlayback(): void {
    this.isPlaying = false;
    if (this.playbackSubscription) {
      this.playbackSubscription.unsubscribe();
      this.playbackSubscription = undefined;
    }
  }

  exportJson(): void {
    this.timeTravelService.exportAsJson(this.runId).subscribe({
      next: (blob) => {
        this.timeTravelService.downloadFile(blob, `collaboration-${this.runId}.json`);
      },
      error: (err) => console.error('Failed to export JSON:', err)
    });
  }

  exportCsv(): void {
    this.timeTravelService.exportAsCsv(this.runId).subscribe({
      next: (blob) => {
        this.timeTravelService.downloadFile(blob, `collaboration-${this.runId}.csv`);
      },
      error: (err) => console.error('Failed to export CSV:', err)
    });
  }

  formatTimestamp(timestamp?: string): string {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    return date.toLocaleTimeString();
  }

  private emitCurrentSnapshot(): void {
    const snapshot = this.currentSnapshot;
    if (snapshot) {
      this.snapshotChanged.emit(snapshot);
    }
  }
}
