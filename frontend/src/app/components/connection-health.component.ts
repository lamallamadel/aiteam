import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ConnectionHealth } from '../services/collaboration-websocket.service';

@Component({
  selector: 'app-connection-health',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="connection-health" [ngClass]="getHealthClass()">
      <div class="health-indicator">
        <span class="status-dot" [ngClass]="getHealthClass()"></span>
        <span class="status-text">{{ getHealthText() }}</span>
      </div>
      
      <div class="health-metrics">
        <div class="metric">
          <span class="metric-label">Latency</span>
          <span class="metric-value">{{ health?.latency || 0 | number:'1.0-0' }}ms</span>
        </div>
        
        <div class="metric">
          <span class="metric-label">Delivery</span>
          <span class="metric-value">{{ (health?.messageDeliveryRate || 1) * 100 | number:'1.0-1' }}%</span>
        </div>
        
        <div class="metric" *ngIf="(health?.reconnectionCount ?? 0) > 0">
          <span class="metric-label">Reconnections</span>
          <span class="metric-value">{{ health?.reconnectionCount }}</span>
        </div>
        
        <div class="metric" *ngIf="health?.qualityScore !== undefined">
          <span class="metric-label">Quality</span>
          <span class="metric-value">{{ health?.qualityScore | number:'1.0-0' }}/100</span>
        </div>
        
        <div class="metric" *ngIf="queuedMessages > 0">
          <span class="metric-label">Queued</span>
          <span class="metric-value warning">{{ queuedMessages }}</span>
        </div>
        
        <div class="metric" *ngIf="usingFallback">
          <span class="metric-label">Mode</span>
          <span class="metric-value warning">HTTP Polling</span>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .connection-health {
      background: #1e1e1e;
      border-left: 4px solid;
      padding: 12px 16px;
      margin-bottom: 16px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-radius: 4px;
    }
    
    .connection-health.healthy {
      border-left-color: #4caf50;
      background: rgba(76, 175, 80, 0.05);
    }
    
    .connection-health.degraded {
      border-left-color: #ff9800;
      background: rgba(255, 152, 0, 0.05);
    }
    
    .connection-health.unhealthy {
      border-left-color: #f44336;
      background: rgba(244, 67, 54, 0.05);
    }
    
    .health-indicator {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    
    .status-dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      animation: pulse 2s infinite;
    }
    
    .status-dot.healthy {
      background: #4caf50;
    }
    
    .status-dot.degraded {
      background: #ff9800;
    }
    
    .status-dot.unhealthy {
      background: #f44336;
    }
    
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.5; }
    }
    
    .status-text {
      font-weight: 600;
      font-size: 14px;
      color: #e0e0e0;
    }
    
    .health-metrics {
      display: flex;
      gap: 24px;
    }
    
    .metric {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    
    .metric-label {
      font-size: 11px;
      color: #888;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    
    .metric-value {
      font-size: 14px;
      font-weight: 600;
      color: #e0e0e0;
    }
    
    .metric-value.warning {
      color: #ff9800;
    }
  `]
})
export class ConnectionHealthComponent {
  @Input() health: ConnectionHealth | null = null;
  @Input() queuedMessages: number = 0;
  @Input() usingFallback: boolean = false;

  getHealthClass(): string {
    if (!this.health) return 'unknown';
    
    if (this.health.healthStatus) {
      return this.health.healthStatus.toLowerCase();
    }
    
    if (!this.health.isHealthy) return 'unhealthy';
    if (this.health.reconnectionCount > 2) return 'degraded';
    if (this.health.messageDeliveryRate < 0.95) return 'degraded';
    if (this.health.latency > 200) return 'degraded';
    
    return 'healthy';
  }

  getHealthText(): string {
    if (this.usingFallback) {
      return 'Connection Fallback Active';
    }
    
    const healthClass = this.getHealthClass();
    switch (healthClass) {
      case 'healthy':
        return 'Connection Healthy';
      case 'degraded':
        return 'Connection Degraded';
      case 'unhealthy':
        return 'Connection Unhealthy';
      default:
        return 'Connection Status Unknown';
    }
  }
}
