import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { ToastService } from '../services/toast.service';
import { PasswordStrengthComponent } from './shared/password-strength.component';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PasswordStrengthComponent],
  template: `
    <div class="reset-password-container">
      <div class="reset-password-card">
        <div class="reset-password-header">
          <h1>Set New Password</h1>
          <p>Choose a strong password for your account</p>
        </div>

        <form (ngSubmit)="onSubmit()" class="reset-password-form">
          <div class="form-group">
            <label for="newPassword">New Password</label>
            <input
              id="newPassword"
              type="password"
              [(ngModel)]="newPassword"
              name="newPassword"
              class="input-field"
              placeholder="Enter new password"
              [disabled]="loading()"
              required
            />
            <app-password-strength [password]="newPassword"></app-password-strength>
          </div>

          <div class="form-group">
            <label for="confirmPassword">Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              [(ngModel)]="confirmPassword"
              name="confirmPassword"
              class="input-field"
              placeholder="Re-enter new password"
              [disabled]="loading()"
              required
            />
            @if (confirmPassword && newPassword !== confirmPassword) {
              <div class="validation-error">
                Passwords do not match
              </div>
            }
          </div>

          @if (error()) {
            <div class="error-message" aria-live="polite">
              {{ error() }}
            </div>
          }

          <button
            type="submit"
            class="btn-primary"
            [disabled]="loading() || !isFormValid()"
          >
            @if (loading()) {
              <span class="spinner-small"></span>
              <span>Resetting password...</span>
            } @else {
              <span>Reset Password</span>
            }
          </button>
        </form>

        <div class="back-link">
          <a routerLink="/auth/login">← Back to Sign In</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .reset-password-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: var(--background);
      padding: 20px;
    }

    .reset-password-card {
      width: 100%;
      max-width: 440px;
      background: var(--surface);
      border-radius: 16px;
      padding: 48px 40px;
      border: 1px solid var(--border);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    .reset-password-header {
      text-align: center;
      margin-bottom: 32px;
    }

    .reset-password-header h1 {
      margin: 0 0 8px 0;
      font-size: 1.75rem;
      color: var(--text-primary);
    }

    .reset-password-header p {
      margin: 0;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
    }

    .reset-password-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .validation-error {
      margin-top: 6px;
      font-size: 0.8rem;
      color: #ef4444;
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
      .reset-password-card {
        padding: 32px 24px;
      }
    }
  `]
})
export class ResetPasswordComponent implements OnInit {
  token = '';
  newPassword = '';
  confirmPassword = '';
  loading = signal(false);
  error = signal<string | null>(null);

  constructor(
    private orchestratorService: OrchestratorService,
    private router: Router,
    private route: ActivatedRoute,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.token = params['token'] || '';
      if (!this.token) {
        this.error.set('Invalid or missing reset token');
        this.toastService.show('Invalid or missing reset token', 'error');
      }
    });
  }

  isFormValid(): boolean {
    return !!(
      this.token &&
      this.newPassword &&
      this.confirmPassword &&
      this.newPassword === this.confirmPassword &&
      this.newPassword.length >= 8
    );
  }

  onSubmit(): void {
    if (!this.isFormValid()) {
      return;
    }

    if (this.newPassword !== this.confirmPassword) {
      this.error.set('Passwords do not match');
      this.toastService.show('Passwords do not match', 'error');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.orchestratorService.completePasswordReset(this.token, this.newPassword).subscribe({
      next: () => {
        this.loading.set(false);
        this.toastService.show('Password reset successful! Redirecting to login...', 'success');
        setTimeout(() => {
          this.router.navigate(['/auth/login']);
        }, 2000);
      },
      error: (err) => {
        this.loading.set(false);
        const errorMessage = err.error?.message || 'Failed to reset password. Please try again or request a new reset link.';
        this.error.set(errorMessage);
        this.toastService.show(errorMessage, 'error');
      }
    });
  }
}
