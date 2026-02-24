import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
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
  imports: [CommonModule, FormsModule, RouterLink],
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
            <div class="error-message" aria-live="polite">
              {{ error() }}
            </div>
          }

          @if (success()) {
            <div class="success-message" aria-live="polite">
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
          Already have an account? <a routerLink="/auth/login">Sign in</a>
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

    @media (max-width: 480px) {
      .register-card {
        padding: 32px 24px;
      }

      .form-row {
        grid-template-columns: 1fr;
      }
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
