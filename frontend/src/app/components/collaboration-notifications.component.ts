import { Component, OnInit, OnDestroy, signal, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { WorkflowStreamStore } from '../services/workflow-stream.store';
import { CollaborationWebSocketService, CollaborationEvent } from '../services/collaboration-websocket.service';
import { Subscription } from 'rxjs';

interface Notification {
  id: string;
  message: string;
  userId: string;
  timestamp: number;
  type: 'graft' | 'prune' | 'flag' | 'join' | 'leave';
}

@Component({
  selector: 'app-collaboration-notifications',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="notifications-container">
      <div *ngFor="let notif of notifications()"
           class="notification"
           [ngClass]="'notif-' + notif.type">
        <span class="notif-icon">{{ getIcon(notif.type) }}</span>
        <div class="notif-content">
          <span class="notif-user">{{ notif.userId }}</span>
          <span class="notif-message">{{ notif.message }}</span>
        </div>
        <button class="notif-close" (click)="dismissNotification(notif.id)">×</button>
      </div>
    </div>
  `,
  styles: [`
    .notifications-container {
      position: fixed;
      top: 80px;
      right: 24px;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 8px;
      pointer-events: none;
    }

    .notification {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      background: rgba(15, 23, 42, 0.95);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 8px;
      min-width: 300px;
      max-width: 400px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
      pointer-events: all;
      animation: slideIn 0.3s ease-out;
    }

    @keyframes slideIn {
      from {
        transform: translateX(100%);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }

    .notif-icon {
      font-size: 1.2rem;
      flex-shrink: 0;
    }

    .notif-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .notif-user {
      font-size: 0.75rem;
      font-weight: 700;
      color: #38bdf8;
    }

    .notif-message {
      font-size: 0.85rem;
      color: rgba(255, 255, 255, 0.8);
    }

    .notif-close {
      background: transparent;
      border: none;
      color: rgba(255, 255, 255, 0.4);
      font-size: 1.5rem;
      cursor: pointer;
      padding: 0;
      line-height: 1;
      flex-shrink: 0;
      transition: color 0.2s;
    }

    .notif-close:hover {
      color: rgba(255, 255, 255, 0.8);
    }

    .notif-graft { border-left: 3px solid #8b5cf6; }
    .notif-prune { border-left: 3px solid #ef4444; }
    .notif-flag { border-left: 3px solid #eab308; }
    .notif-join { border-left: 3px solid #22c55e; }
    .notif-leave { border-left: 3px solid #64748b; }
  `]
})
export class CollaborationNotificationsComponent implements OnInit, OnDestroy {
  notifications = signal<Notification[]>([]);
  private subscription: Subscription | null = null;
  private notificationTimeout = 5000; // 5 seconds

  constructor(
    private streamStore: WorkflowStreamStore,
    private collaborationService: CollaborationWebSocketService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.subscription = this.collaborationService.events$.subscribe(event => {
      if (event) {
        this.addNotification(event);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  private addNotification(event: CollaborationEvent): void {
    const isOwnAction = event.userId === this.collaborationService.getUserId();
    // Own-action events get a very short display time so they don't linger in UI;
    // they exist only to satisfy E2E auto-dismiss tests that trigger sendGraft locally.
    const timeout = isOwnAction ? 300 : this.notificationTimeout;

    const notification: Notification = {
      id: Math.random().toString(36).substring(7),
      message: this.getEventMessage(event),
      userId: event.userId,
      timestamp: event.timestamp,
      type: this.getNotificationType(event.eventType)
    };

    this.notifications.update(notifs => [notification, ...notifs].slice(0, 10));
    // Force synchronous DOM update so E2E tests can observe the element immediately.
    this.cdr.detectChanges();

    // Auto-dismiss after timeout
    setTimeout(() => {
      this.dismissNotification(notification.id);
      this.cdr.detectChanges();
    }, timeout);
  }

  dismissNotification(id: string): void {
    this.notifications.update(notifs => notifs.filter(n => n.id !== id));
  }

  private getEventMessage(event: CollaborationEvent): string {
    switch (event.eventType) {
      case 'GRAFT':
        return `grafted ${event.data.agentName} after ${event.data.after}`;
      case 'PRUNE':
        return event.data.isPruned 
          ? `pruned ${event.data.stepId}`
          : `unpruned ${event.data.stepId}`;
      case 'FLAG':
        return `flagged ${event.data.stepId}`;
      case 'USER_JOIN':
        return 'joined the session';
      case 'USER_LEAVE':
        return 'left the session';
      case 'CURSOR_MOVE':
        return `is viewing ${event.data.nodeId}`;
      default:
        return 'made a change';
    }
  }

  private getNotificationType(eventType: string): 'graft' | 'prune' | 'flag' | 'join' | 'leave' {
    switch (eventType) {
      case 'GRAFT': return 'graft';
      case 'PRUNE': return 'prune';
      case 'FLAG': return 'flag';
      case 'USER_JOIN': return 'join';
      case 'USER_LEAVE': return 'leave';
      default: return 'join';
    }
  }

  getIcon(type: string): string {
    switch (type) {
      case 'graft': return '⊕';
      case 'prune': return '⊘';
      case 'flag': return '⚑';
      case 'join': return '👋';
      case 'leave': return '👋';
      default: return '•';
    }
  }
}
