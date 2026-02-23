import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CollaborationAnalytics } from '../models/collaboration.model';
import { TimeTravelService } from '../services/time-travel.service';

@Component({
  selector: 'app-collaboration-analytics-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="analytics-container" *ngIf="analytics">
      <div class="dashboard-header">
        <span class="header-label">📊 COLLABORATION ANALYTICS</span>
      </div>

      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-icon">📈</div>
          <div class="metric-content">
            <div class="metric-value">{{ analytics.totalEvents }}</div>
            <div class="metric-label">Total Events</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon">👥</div>
          <div class="metric-content">
            <div class="metric-value">{{ analytics.uniqueUsers }}</div>
            <div class="metric-label">Unique Users</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon">⏱️</div>
          <div class="metric-content">
            <div class="metric-value">{{ formatDuration(analytics.averageSessionDurationMinutes) }}</div>
            <div class="metric-label">Session Duration</div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon">⚡</div>
          <div class="metric-content">
            <div class="metric-value">{{ analytics.eventsPerMinute.toFixed(1) }}</div>
            <div class="metric-label">Events/Minute</div>
          </div>
        </div>
      </div>

      <div class="section event-breakdown">
        <div class="section-header">
          <span class="icon">📋</span>
          <span class="title">Event Type Breakdown</span>
        </div>
        <div class="event-type-list">
          <div *ngFor="let item of getEventTypeCounts()" class="event-type-item">
            <div class="type-info">
              <span class="type-badge" [ngClass]="'type-' + item.type.toLowerCase()">
                {{ item.type }}
              </span>
              <span class="type-count">{{ item.count }}</span>
            </div>
            <div class="type-bar">
              <div class="bar-fill" [style.width.%]="item.percentage"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="section graft-checkpoints" *ngIf="analytics.mostGraftedCheckpoints.length > 0">
        <div class="section-header">
          <span class="icon">🔀</span>
          <span class="title">Most Grafted Checkpoints</span>
        </div>
        <div class="checkpoint-list">
          <div *ngFor="let checkpoint of analytics.mostGraftedCheckpoints" class="checkpoint-item">
            <div class="checkpoint-name">{{ checkpoint.checkpointName }}</div>
            <div class="checkpoint-stats">
              <span class="graft-count">{{ checkpoint.graftCount }} grafts</span>
              <div class="agent-tags">
                <span *ngFor="let agent of checkpoint.agentNames" class="agent-tag">
                  {{ agent }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="section user-activity">
        <div class="section-header">
          <span class="icon">👤</span>
          <span class="title">User Activity</span>
        </div>
        <div class="user-list">
          <div *ngFor="let item of getUserActivity()" class="user-item">
            <div class="user-info">
              <span class="user-avatar" [style.background]="getUserColor(item.userId)">
                {{ getUserInitial(item.userId) }}
              </span>
              <span class="user-id">{{ item.userId }}</span>
            </div>
            <div class="user-stats">
              <span class="action-count">{{ item.count }} actions</span>
            </div>
          </div>
        </div>
      </div>

      <div class="section heatmap-section" *ngIf="hasHeatmaps()">
        <div class="section-header">
          <span class="icon">🔥</span>
          <span class="title">Activity Heatmap</span>
        </div>
        <div class="heatmap-container">
          <div *ngFor="let item of getHeatmapData()" class="user-heatmap">
            <div class="heatmap-user">{{ item.userId }}</div>
            <div class="heatmap-grid">
              <div *ngFor="let hour of hours" 
                   class="heatmap-cell"
                   [class.active]="item.hourlyActivity[hour] > 0"
                   [style.opacity]="getHeatmapOpacity(item.hourlyActivity[hour], item.maxActivity)"
                   [title]="'Hour ' + hour + ': ' + (item.hourlyActivity[hour] || 0) + ' actions'">
              </div>
            </div>
          </div>
          <div class="heatmap-labels">
            <div *ngFor="let hour of [0, 6, 12, 18]" class="hour-label" [style.left.%]="(hour / 24) * 100">
              {{ hour }}h
            </div>
          </div>
        </div>
      </div>

      <div class="section conflicts" *ngIf="analytics.conflictResolutions.length > 0">
        <div class="section-header">
          <span class="icon">⚠️</span>
          <span class="title">Conflict Resolution Audit Trail</span>
        </div>
        <div class="conflict-list">
          <div *ngFor="let conflict of analytics.conflictResolutions" class="conflict-item">
            <div class="conflict-header">
              <span class="conflict-type">{{ conflict.conflictType }}</span>
              <span class="conflict-time">{{ formatTimestamp(conflict.timestamp) }}</span>
            </div>
            <div class="conflict-details">
              <div class="conflict-users">
                <span class="user">{{ conflict.userId1 }}</span>
                <span class="vs">vs</span>
                <span class="user">{{ conflict.userId2 }}</span>
              </div>
              <div class="conflict-target">Target: {{ conflict.targetNode }}</div>
              <div class="conflict-resolution">
                <span class="label">Resolution:</span>
                <span class="value">{{ conflict.resolution }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .analytics-container {
      background: rgba(15, 23, 42, 0.95);
      border: 1px solid rgba(34, 197, 94, 0.3);
      border-radius: 12px;
      padding: 16px;
      margin: 16px 0;
    }

    .dashboard-header {
      margin-bottom: 20px;
      padding-bottom: 12px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }

    .header-label {
      font-size: 0.7rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: #22c55e;
    }

    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 12px;
      margin-bottom: 20px;
    }

    .metric-card {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px;
      background: rgba(34, 197, 94, 0.05);
      border: 1px solid rgba(34, 197, 94, 0.2);
      border-radius: 8px;
    }

    .metric-icon {
      font-size: 1.5rem;
    }

    .metric-content {
      flex: 1;
    }

    .metric-value {
      font-size: 1.2rem;
      font-weight: 700;
      color: #22c55e;
      line-height: 1.2;
    }

    .metric-label {
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.5);
      margin-top: 2px;
    }

    .section {
      margin: 20px 0;
      padding: 16px;
      background: rgba(0, 0, 0, 0.2);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 8px;
    }

    .section-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 16px;
      padding-bottom: 8px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }

    .section-header .icon {
      font-size: 1rem;
    }

    .section-header .title {
      font-size: 0.75rem;
      font-weight: 700;
      color: rgba(255, 255, 255, 0.9);
    }

    .event-type-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .event-type-item {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .type-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .type-badge {
      padding: 3px 10px;
      border-radius: 6px;
      font-size: 0.7rem;
      font-weight: 700;
      font-family: monospace;
    }

    .type-badge.type-graft {
      background: rgba(139, 92, 246, 0.2);
      color: #a78bfa;
    }

    .type-badge.type-prune {
      background: rgba(239, 68, 68, 0.2);
      color: #ef4444;
    }

    .type-badge.type-flag {
      background: rgba(234, 179, 8, 0.2);
      color: #eab308;
    }

    .type-badge.type-user_join,
    .type-badge.type-user_leave {
      background: rgba(59, 130, 246, 0.2);
      color: #3b82f6;
    }

    .type-badge.type-cursor_move {
      background: rgba(100, 116, 139, 0.2);
      color: #64748b;
    }

    .type-count {
      font-size: 0.75rem;
      font-weight: 700;
      color: rgba(255, 255, 255, 0.7);
    }

    .type-bar {
      height: 6px;
      background: rgba(255, 255, 255, 0.05);
      border-radius: 3px;
      overflow: hidden;
    }

    .bar-fill {
      height: 100%;
      background: linear-gradient(90deg, #22c55e, #10b981);
      border-radius: 3px;
      transition: width 0.3s ease;
    }

    .checkpoint-list,
    .user-list,
    .conflict-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .checkpoint-item {
      padding: 12px;
      background: rgba(139, 92, 246, 0.05);
      border: 1px solid rgba(139, 92, 246, 0.2);
      border-radius: 6px;
    }

    .checkpoint-name {
      font-size: 0.75rem;
      font-weight: 700;
      font-family: monospace;
      color: #a78bfa;
      margin-bottom: 8px;
    }

    .checkpoint-stats {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .graft-count {
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.7);
    }

    .agent-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
    }

    .agent-tag {
      padding: 2px 8px;
      background: rgba(139, 92, 246, 0.2);
      border: 1px solid rgba(139, 92, 246, 0.3);
      border-radius: 4px;
      font-size: 0.65rem;
      font-family: monospace;
      color: #a78bfa;
    }

    .user-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px;
      background: rgba(59, 130, 246, 0.05);
      border: 1px solid rgba(59, 130, 246, 0.2);
      border-radius: 6px;
    }

    .user-info {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .user-avatar {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.7rem;
      font-weight: 700;
      color: white;
    }

    .user-id {
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.8);
    }

    .action-count {
      font-size: 0.7rem;
      font-weight: 600;
      color: #3b82f6;
    }

    .heatmap-container {
      position: relative;
    }

    .user-heatmap {
      display: grid;
      grid-template-columns: 100px 1fr;
      gap: 12px;
      align-items: center;
      margin-bottom: 12px;
    }

    .heatmap-user {
      font-size: 0.7rem;
      font-weight: 600;
      color: rgba(255, 255, 255, 0.7);
    }

    .heatmap-grid {
      display: grid;
      grid-template-columns: repeat(24, 1fr);
      gap: 2px;
      height: 24px;
    }

    .heatmap-cell {
      background: rgba(34, 197, 94, 0.1);
      border-radius: 2px;
      transition: opacity 0.2s;
    }

    .heatmap-cell.active {
      background: #22c55e;
    }

    .heatmap-labels {
      position: relative;
      height: 20px;
      margin-top: 8px;
      margin-left: 112px;
    }

    .hour-label {
      position: absolute;
      font-size: 0.65rem;
      color: rgba(255, 255, 255, 0.4);
      transform: translateX(-50%);
    }

    .conflict-item {
      padding: 12px;
      background: rgba(234, 179, 8, 0.05);
      border: 1px solid rgba(234, 179, 8, 0.2);
      border-radius: 6px;
    }

    .conflict-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
    }

    .conflict-type {
      font-size: 0.7rem;
      font-weight: 700;
      color: #eab308;
    }

    .conflict-time {
      font-size: 0.65rem;
      color: rgba(255, 255, 255, 0.5);
    }

    .conflict-details {
      display: flex;
      flex-direction: column;
      gap: 6px;
      font-size: 0.7rem;
      color: rgba(255, 255, 255, 0.7);
    }

    .conflict-users {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .conflict-users .vs {
      color: rgba(255, 255, 255, 0.4);
      font-weight: 600;
    }

    .conflict-resolution {
      display: flex;
      gap: 6px;
    }

    .conflict-resolution .label {
      color: rgba(255, 255, 255, 0.5);
    }

    .conflict-resolution .value {
      color: #22c55e;
      font-weight: 600;
    }
  `]
})
export class CollaborationAnalyticsDashboardComponent implements OnInit {
  @Input() runId!: string;
  
  analytics: CollaborationAnalytics | null = null;
  hours = Array.from({ length: 24 }, (_, i) => i);

  constructor(private timeTravelService: TimeTravelService) {}

  ngOnInit(): void {
    this.loadAnalytics();
  }

  loadAnalytics(): void {
    this.timeTravelService.getAnalytics(this.runId).subscribe({
      next: (analytics) => {
        this.analytics = analytics;
      },
      error: (err) => console.error('Failed to load analytics:', err)
    });
  }

  getEventTypeCounts(): Array<{ type: string; count: number; percentage: number }> {
    if (!this.analytics?.eventTypeCounts) return [];
    
    const total = this.analytics.totalEvents;
    return Object.entries(this.analytics.eventTypeCounts)
      .map(([type, count]) => ({
        type,
        count,
        percentage: (count / total) * 100
      }))
      .sort((a, b) => b.count - a.count);
  }

  getUserActivity(): Array<{ userId: string; count: number }> {
    if (!this.analytics?.userActivityCounts) return [];
    
    return Object.entries(this.analytics.userActivityCounts)
      .map(([userId, count]) => ({ userId, count }))
      .sort((a, b) => b.count - a.count);
  }

  hasHeatmaps(): boolean {
    return this.analytics?.userActivityHeatmaps && 
           Object.keys(this.analytics.userActivityHeatmaps).length > 0;
  }

  getHeatmapData(): Array<any> {
    if (!this.analytics?.userActivityHeatmaps) return [];
    
    return Object.values(this.analytics.userActivityHeatmaps).map(heatmap => {
      const maxActivity = Math.max(...Object.values(heatmap.hourlyActivity));
      return {
        userId: heatmap.userId,
        hourlyActivity: heatmap.hourlyActivity,
        maxActivity
      };
    });
  }

  getHeatmapOpacity(activity: number, maxActivity: number): number {
    if (!activity || maxActivity === 0) return 0.1;
    return 0.3 + (activity / maxActivity) * 0.7;
  }

  getUserColor(userId: string): string {
    const colors = [
      '#ef4444', '#f59e0b', '#10b981', '#3b82f6', 
      '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16'
    ];
    const hash = userId.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    return colors[hash % colors.length];
  }

  getUserInitial(userId: string): string {
    return userId.substring(0, 1).toUpperCase();
  }

  formatDuration(minutes: number): string {
    if (minutes < 60) {
      return `${Math.round(minutes)}m`;
    }
    const hours = Math.floor(minutes / 60);
    const mins = Math.round(minutes % 60);
    return `${hours}h ${mins}m`;
  }

  formatTimestamp(timestamp: string): string {
    const date = new Date(timestamp);
    return date.toLocaleString();
  }
}
