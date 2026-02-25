import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
  durationMs: number;
}

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  private _toasts = signal<Toast[]>([]);
  public toasts = this._toasts.asReadonly();
  private idCounter = 0;

  show(message: string, type: 'success' | 'error' | 'info' = 'info', durationMs: number = 5000): string {
    const id = `toast-${++this.idCounter}-${Date.now()}`;
    const toast: Toast = { id, message, type, durationMs };
    
    this._toasts.update(toasts => [...toasts, toast]);

    if (durationMs > 0) {
      setTimeout(() => {
        this.dismiss(id);
      }, durationMs);
    }

    return id;
  }

  dismiss(id: string): void {
    this._toasts.update(toasts => toasts.filter(t => t.id !== id));
  }

  success(message: string, durationMs: number = 5000): string {
    return this.show(message, 'success', durationMs);
  }

  error(message: string, durationMs: number = 7000): string {
    return this.show(message, 'error', durationMs);
  }

  info(message: string, durationMs: number = 5000): string {
    return this.show(message, 'info', durationMs);
  }
}
