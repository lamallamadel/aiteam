import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../services/auth.service';

interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

interface RegisterResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="register-container">
      <div class="register-card">
        <div class="register-header">
          <h1>Create Account</h1>
          <p>Join Atlasia Orchestrator</p>
        </div>

        <form (ngSubmit)="onSubmit()" class="register-form">
          <div class="form-row">
            <div class="form-group">
              <label for="firstName">First Name</label>
              <input
                id="firstName"
                type="text"
                [(ngModel)]="firstName"
                name="firstName"
                class="input-field"
                placeholder="John"
                [disabled]="loading()"
              />
            </div>
            <div class="form-group">
              <label for="lastName">Last Name</label>
              <input
                id="lastName"
                type="text"
                [(ngModel)]="lastName"
                name="lastName"
                class="input-field"
                placeholder="Doe"
                [disabled]="loading()"
              />
            </div>
          </div>

          <div class="form-group">
            <label for="username">Username *</label>
            <input
              id="username"
              type="text"
              [(ngModel)]="username"
              name="username"
              class="input-field"
              placeholder="johndoe"
              [disabled]="loading()"
              required
            />
          </div>

          <div class="form-group">
            <label for="email">Email *</label>
            <input
              id="email"
              type="email"
              [(ngModel)]="email"
              name="email"
              class="input-field"
              placeholder="john@example.com"
              [disabled]="loading()"
              required
            />
          </div>

          <div class="form-group">
            <label for="password">Password *</label>
            <input
              id="password"
              type="password"
              [(ngModel)]="password"
              name="password"
              class="input-field"
              placeholder="Enter a strong password"
              [disabled]="loading()"
              required
            />
          </div>

          <div class="form-group">
            <label for="confirmPassword">Confirm Password *</label>
            <input
              id="confirmPassword"
              type="password"
              [(ngModel)]="confirmPassword"
              name="confirmPassword"
              class="input-field"
              placeholder="Re-enter your password"
              [disabled]="loading()"
              required
            />
          </div>

          @if (error()) {
            <div class="error-message">
              {{ error() }}
            </div>
          }

          @if (success()) {
            <div class="success-message">
              {{ success() }}
            </div>
          }

          <button
            type="submit"
            class="btn-primary"
            [disabled]="loading() || !isFormValid()"
          >
            @if (loading()) {
              <span class="spinner-small"></span>
              <span>Creating account...</span>
            } @else {
              <span>Create Account</span>
            }
          </button>
        </form>

        <div class="login-link">
          Already have an account? <a href="/auth/login">Sign in</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .register-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: var(--background);
      padding: 20px;
    }

    .register-card {
      width: 100%;
      max-width: 520px;
      background: var(--surface);
      border-radius: 16px;
      padding: 48px 40px;
      border: 1px solid var(--border);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    .register-header {
      text-align: center;
      margin-bottom: 32px;
    }

    .register-header h1 {
      margin: 0 0 8px 0;
      font-size: 2rem;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .register-header p {
      margin: 0;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
    }

    .register-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .form-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .form-group label {
      font-size: 0.9rem;
      font-weight: 600;
      color: rgba(255, 255, 255, 0.8);
    }

    .input-field {
      padding: 12px 16px;
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: white;
      font-size: 0.95rem;
      outline: none;
      transition: border-color 0.2s;
    }

    .input-field:focus {
      border-color: #38bdf8;
    }

    .input-field::placeholder {
      color: #64748b;
    }

    .input-field:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .error-message {
      padding: 12px 16px;
      background: rgba(239, 68, 68, 0.1);
      border: 1px solid rgba(239, 68, 68, 0.3);
      border-radius: 8px;
      color: #ef4444;
      font-size: 0.85rem;
    }

    .success-message {
      padding: 12px 16px;
      background: rgba(34, 197, 94, 0.1);
      border: 1px solid rgba(34, 197, 94, 0.3);
      border-radius: 8px;
      color: #22c55e;
      font-size: 0.85rem;
    }

    .btn-primary {
      padding: 14px 24px;
      background: var(--accent-active);
      color: white;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      font-size: 1rem;
      cursor: pointer;
      transition: opacity 0.2s;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
    }

    .btn-primary:hover:not(:disabled) {
      opacity: 0.9;
    }

    .btn-primary:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .spinner-small {
      width: 16px;
      height: 16px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: white;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .login-link {
      text-align: center;
      margin-top: 24px;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
    }

    .login-link a {
      color: #38bdf8;
      text-decoration: none;
      font-weight: 600;
    }

    .login-link a:hover {
      text-decoration: underline;
    }
  `]
})
export class RegisterComponent {
  username = '';
  email = '';
  password = '';
  confirmPassword = '';
  firstName = '';
  lastName = '';
  loading = signal(false);
  error = signal<string | null>(null);
  success = signal<string | null>(null);

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private router: Router
  ) {}

  isFormValid(): boolean {
    return !!(
      this.username &&
      this.email &&
      this.password &&
      this.confirmPassword &&
      this.password === this.confirmPassword
    );
  }

  onSubmit(): void {
    if (!this.isFormValid()) {
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.error.set('Passwords do not match');
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);

    const request: RegisterRequest = {
      username: this.username,
      email: this.email,
      password: this.password,
      firstName: this.firstName || undefined,
      lastName: this.lastName || undefined
    };

    this.http.post<RegisterResponse>('/api/auth/register', request).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.success.set('Account created successfully! Redirecting...');
        this.authService.storeTokens(response.accessToken, response.refreshToken);
        setTimeout(() => {
          this.router.navigate(['/onboarding']);
        }, 1500);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Registration failed. Please try again.');
      }
    });
  }
}
