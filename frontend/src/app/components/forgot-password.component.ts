import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { ToastService } from '../services/toast.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="forgot-password-container">
      <div class="forgot-password-card">
        <div class="forgot-password-header">
          <h1>Reset Password</h1>
          <p>Enter your email address and we'll send you a reset link</p>
        </div>

        @if (!submitted()) {
          <form (ngSubmit)="onSubmit()" class="forgot-password-form">
            <div class="form-group">
              <label for="email">Email Address</label>
              <input
                id="email"
                type="email"
                [(ngModel)]="email"
                name="email"
                class="input-field"
                placeholder="your.email@example.com"
                [disabled]="loading()"
                required
              />
            </div>

            @if (error()) {
              <div class="error-message" aria-live="polite">
                {{ error() }}
              </div>
            }

            <button
              type="submit"
              class="btn-primary"
              [disabled]="loading() || !email"
            >
              @if (loading()) {
                <span class="spinner-small"></span>
                <span>Sending...</span>
              } @else {
                <span>Send Reset Link</span>
              }
            </button>
          </form>
        } @else {
          <div class="success-state">
            <div class="success-icon">✓</div>
            <h2>Check your email</h2>
            <p>
              We've sent a password reset link to <strong>{{ email }}</strong>.
              Please check your inbox and follow the instructions.
            </p>
            <p class="help-text">
              Didn't receive the email? Check your spam folder or 
              <a href="#" (click)="resetForm(); $event.preventDefault()" class="link">try again</a>.
            </p>
          </div>
        }

        <div class="back-link">
          <a routerLink="/auth/login">← Back to Sign In</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .forgot-password-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: var(--background);
      padding: 20px;
    }

    .forgot-password-card {
      width: 100%;
      max-width: 440px;
      background: var(--surface);
      border-radius: 16px;
      padding: 48px 40px;
      border: 1px solid var(--border);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    .forgot-password-header {
      text-align: center;
      margin-bottom: 32px;
    }

    .forgot-password-header h1 {
      margin: 0 0 8px 0;
      font-size: 1.75rem;
      color: var(--text-primary);
    }

    .forgot-password-header p {
      margin: 0;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
    }

    .forgot-password-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .success-state {
      text-align: center;
      padding: 20px 0;
    }

    .success-icon {
      width: 64px;
      height: 64px;
      margin: 0 auto 20px;
      background: rgba(34, 197, 94, 0.1);
      border: 2px solid #22c55e;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 2rem;
      color: #22c55e;
      font-weight: bold;
    }

    .success-state h2 {
      margin: 0 0 16px 0;
      font-size: 1.5rem;
      color: var(--text-primary);
    }

    .success-state p {
      margin: 0 0 12px 0;
      color: rgba(255, 255, 255, 0.8);
      line-height: 1.6;
    }

    .success-state strong {
      color: var(--text-primary);
    }

    .help-text {
      font-size: 0.85rem;
      color: rgba(255, 255, 255, 0.6);
    }

    .link {
      color: #38bdf8;
      text-decoration: none;
      font-weight: 600;
    }

    .link:hover {
      text-decoration: underline;
    }

    .back-link {
      text-align: center;
      margin-top: 24px;
    }

    .back-link a {
      color: rgba(255, 255, 255, 0.6);
      text-decoration: none;
      font-size: 0.9rem;
      transition: color 0.2s;
    }

    .back-link a:hover {
      color: #38bdf8;
    }

    @media (max-width: 480px) {
      .forgot-password-card {
        padding: 32px 24px;
      }
    }
  `]
})
export class ForgotPasswordComponent {
  email = '';
  loading = signal(false);
  error = signal<string | null>(null);
  submitted = signal(false);

  constructor(
    private orchestratorService: OrchestratorService,
    private toastService: ToastService
  ) {}

  onSubmit(): void {
    if (!this.email) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.orchestratorService.initiatePasswordReset(this.email).subscribe({
      next: () => {
        this.loading.set(false);
        this.submitted.set(true);
        this.toastService.show('Password reset email sent', 'success');
      },
      error: (err) => {
        this.loading.set(false);
        const errorMessage = err.error?.message || 'Failed to send reset email. Please try again.';
        this.error.set(errorMessage);
        this.toastService.show(errorMessage, 'error');
      }
    });
  }

  resetForm(): void {
    this.submitted.set(false);
    this.email = '';
    this.error.set(null);
  }
}
