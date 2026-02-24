import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="toast-container">
      @for (toast of toastService.toasts(); track toast.id) {
        <div class="toast toast-{{ toast.type }}" [@slideIn]>
          <div class="toast-content">
            <span class="toast-icon">{{ getIcon(toast.type) }}</span>
            <span class="toast-message">{{ toast.message }}</span>
          </div>
          <button class="toast-dismiss" (click)="toastService.dismiss(toast.id)" aria-label="Dismiss notification">
            ✕
          </button>
        </div>
      }
    </div>
  `,
  styles: [`
    .toast-container {
      position: fixed;
      top: 24px;
      right: 24px;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 12px;
      max-width: 420px;
      pointer-events: none;
    }

    .toast {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 14px 16px;
      background: var(--surface, #1e293b);
      border-radius: 8px;
      border-left: 4px solid;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
      pointer-events: auto;
      animation: slideIn 0.3s ease-out;
      min-width: 320px;
    }

    @keyframes slideIn {
      from {
        transform: translateX(400px);
        opacity: 0;
      }
      to {
        transform: translateX(0);
        opacity: 1;
      }
    }

    .toast-success {
      border-left-color: #22c55e;
    }

    .toast-error {
      border-left-color: #ef4444;
    }

    .toast-info {
      border-left-color: #38bdf8;
    }

    .toast-content {
      display: flex;
      align-items: center;
      gap: 10px;
      flex: 1;
    }

    .toast-icon {
      font-size: 1.2rem;
      flex-shrink: 0;
    }

    .toast-message {
      color: white;
      font-size: 0.9rem;
      line-height: 1.4;
      word-break: break-word;
    }

    .toast-dismiss {
      background: transparent;
      border: none;
      color: rgba(255, 255, 255, 0.6);
      font-size: 1.1rem;
      cursor: pointer;
      padding: 4px;
      width: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px;
      transition: all 0.2s;
      flex-shrink: 0;
    }

    .toast-dismiss:hover {
      background: rgba(255, 255, 255, 0.1);
      color: white;
    }

    @media (max-width: 640px) {
      .toast-container {
        top: 16px;
        right: 16px;
        left: 16px;
        max-width: none;
      }

      .toast {
        min-width: 0;
      }
    }
  `]
})
export class ToastContainerComponent {
  constructor(public toastService: ToastService) {}

  getIcon(type: 'success' | 'error' | 'info'): string {
    switch (type) {
      case 'success': return '✓';
      case 'error': return '✕';
      case 'info': return 'ℹ';
      default: return 'ℹ';
    }
  }
}
