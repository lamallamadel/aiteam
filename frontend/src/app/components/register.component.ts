import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { PasswordStrengthComponent } from './shared/password-strength.component';

interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

interface RegisterResponse {
  userId: string;
  username: string;
  email: string;
  message: string;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, PasswordStrengthComponent],
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
              [class.field-error]="usernameError()"
              placeholder="johndoe"
              [disabled]="loading()"
              required
            />
            @if (usernameError()) {
              <span class="field-error-message">{{ usernameError() }}</span>
            }
          </div>

          <div class="form-group">
            <label for="email">Email *</label>
            <input
              id="email"
              type="email"
              [(ngModel)]="email"
              name="email"
              class="input-field"
              [class.field-error]="emailError()"
              placeholder="john@example.com"
              [disabled]="loading()"
              required
            />
            @if (emailError()) {
              <span class="field-error-message">{{ emailError() }}</span>
            }
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
            <app-password-strength [password]="password" />
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
              @if (error()?.includes('already exists')) {
                <a routerLink="/auth/login" class="inline-link">Sign in instead</a>
              }
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

    .field-error {
      border-color: #ef4444 !important;
    }

    .error-message {
      background: rgba(239, 68, 68, 0.1);
      color: #ef4444;
      padding: 12px;
      border-radius: 8px;
      font-size: 0.9rem;
      border: 1px solid rgba(239, 68, 68, 0.2);
    }

    .inline-link {
      display: block;
      margin-top: 4px;
      color: #38bdf8;
      text-decoration: underline;
      font-weight: 600;
      cursor: pointer;
    }

    .success-message {
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

  usernameError = computed(() => {
    if (!this.username) return null;
    if (this.username.length < 3) return 'Username must be at least 3 characters';
    return null;
  });

  emailError = computed(() => {
    if (!this.email) return null;
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(this.email)) return 'Please enter a valid email address';
    return null;
  });

  passwordStrength = computed(() => {
    const pwd = this.password;
    if (!pwd) return 0;

    let strength = 0;
    if (pwd.length >= 12) strength++;
    if (/[A-Z]/.test(pwd)) strength++;
    if (/[a-z]/.test(pwd)) strength++;
    if (/\d/.test(pwd)) strength++;
    if (/[^A-Za-z0-9]/.test(pwd)) strength++;

    return Math.min(strength, 4);
  });

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private router: Router,
    private toastService: ToastService
  ) {}

  isFormValid(): boolean {
    return !!(
      this.username &&
      this.email &&
      this.password &&
      this.confirmPassword &&
      this.password === this.confirmPassword &&
      !this.usernameError() &&
      !this.emailError() &&
      this.passwordStrength() >= 2
    );
  }

  onSubmit(): void {
    if (!this.isFormValid()) {
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.error.set('Passwords do not match');
      this.toastService.show('Passwords do not match', 'error');
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
      next: () => {
        this.authService.login(this.username, this.password).subscribe({
          next: () => {
            this.loading.set(false);
            this.success.set('Account created successfully! Redirecting...');
            this.toastService.show('Account created successfully! Redirecting...', 'success');
            setTimeout(() => {
              this.router.navigate(['/onboarding']);
            }, 1500);
          },
          error: () => {
            this.loading.set(false);
            this.toastService.show('Account created, but auto-login failed. Please login manually.', 'info');
            this.router.navigate(['/auth/login']);
          }
        });
      },
      error: (err) => {
        this.loading.set(false);
        const errorMessage = err.error?.message || 'Registration failed. Please try again.';
        
        if (errorMessage.toLowerCase().includes('already exists') || errorMessage.toLowerCase().includes('taken')) {
          this.error.set('Username or Email already exists.');
          this.toastService.show('This account already exists. Please sign in instead.', 'info');
        } else {
          this.error.set(errorMessage);
          this.toastService.show(errorMessage, 'error');
        }
      }
    });
  }
}
