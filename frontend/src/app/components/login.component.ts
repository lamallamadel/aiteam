import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="login-header">
          <h1>Atlasia Orchestrator</h1>
          <p>AI-Powered Development Automation</p>
        </div>

        <form (ngSubmit)="onSubmit()" class="login-form">
          <div class="form-group">
            <label for="username">Username</label>
            <input
              id="username"
              type="text"
              [(ngModel)]="username"
              name="username"
              class="input-field"
              placeholder="Enter your username"
              [disabled]="loading()"
              required
            />
          </div>

          <div class="form-group">
            <label for="password">Password</label>
            <input
              id="password"
              type="password"
              [(ngModel)]="password"
              name="password"
              class="input-field"
              placeholder="Enter your password"
              [disabled]="loading()"
              required
            />
          </div>

          @if (error()) {
            <div class="error-message">
              {{ error() }}
            </div>
          }

          <button
            type="submit"
            class="btn-primary"
            [disabled]="loading() || !username || !password"
          >
            @if (loading()) {
              <span class="spinner-small"></span>
              <span>Signing in...</span>
            } @else {
              <span>Sign In</span>
            }
          </button>
        </form>

        <div class="oauth-divider">
          <span>or continue with</span>
        </div>

        <div class="oauth-buttons">
          <button class="oauth-btn github" (click)="loginWithOAuth2('github')">
            <span class="oauth-icon">🐙</span>
            <span>GitHub</span>
          </button>
          <button class="oauth-btn google" (click)="loginWithOAuth2('google')">
            <span class="oauth-icon">📧</span>
            <span>Google</span>
          </button>
          <button class="oauth-btn gitlab" (click)="loginWithOAuth2('gitlab')">
            <span class="oauth-icon">🦊</span>
            <span>GitLab</span>
          </button>
        </div>

        <div class="register-link">
          Don't have an account? <a href="/auth/register">Sign up</a>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: var(--background);
      padding: 20px;
    }

    .login-card {
      width: 100%;
      max-width: 440px;
      background: var(--surface);
      border-radius: 16px;
      padding: 48px 40px;
      border: 1px solid var(--border);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    .login-header {
      text-align: center;
      margin-bottom: 40px;
    }

    .login-header h1 {
      margin: 0 0 8px 0;
      font-size: 2rem;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .login-header p {
      margin: 0;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 20px;
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

    .oauth-divider {
      display: flex;
      align-items: center;
      margin: 32px 0 24px 0;
      text-align: center;
      color: rgba(255, 255, 255, 0.4);
      font-size: 0.85rem;
    }

    .oauth-divider::before,
    .oauth-divider::after {
      content: '';
      flex: 1;
      border-bottom: 1px solid var(--border);
    }

    .oauth-divider span {
      padding: 0 16px;
    }

    .oauth-buttons {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
    }

    .oauth-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      padding: 16px 12px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: white;
      font-size: 0.8rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
    }

    .oauth-btn:hover {
      background: rgba(255, 255, 255, 0.06);
      transform: translateY(-2px);
    }

    .oauth-btn.github:hover {
      border-color: #8b949e;
    }

    .oauth-btn.google:hover {
      border-color: #4285f4;
    }

    .oauth-btn.gitlab:hover {
      border-color: #fc6d26;
    }

    .oauth-icon {
      font-size: 1.5rem;
    }

    .register-link {
      text-align: center;
      margin-top: 24px;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
    }

    .register-link a {
      color: #38bdf8;
      text-decoration: none;
      font-weight: 600;
    }

    .register-link a:hover {
      text-decoration: underline;
    }
  `]
})
export class LoginComponent {
  username = '';
  password = '';
  loading = signal(false);
  error = signal<string | null>(null);

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  onSubmit(): void {
    if (!this.username || !this.password) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.authService.login(this.username, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set('Invalid username or password');
      }
    });
  }

  loginWithOAuth2(provider: 'github' | 'google' | 'gitlab'): void {
    const width = 600;
    const height = 700;
    const left = (window.screen.width / 2) - (width / 2);
    const top = (window.screen.height / 2) - (height / 2);
    
    const authUrl = `/api/oauth2/authorize/${provider}`;
    const popup = window.open(
      authUrl,
      'oauth2_authorization',
      `width=${width},height=${height},top=${top},left=${left},scrollbars=yes,resizable=yes`
    );

    if (popup) {
      const messageHandler = (event: MessageEvent) => {
        if (event.origin !== window.location.origin) {
          return;
        }
        
        if (event.data && event.data.type === 'oauth2-success') {
          window.removeEventListener('message', messageHandler);
          this.router.navigate(['/dashboard']);
        }
      };

      window.addEventListener('message', messageHandler);

      const checkPopup = setInterval(() => {
        if (popup.closed) {
          clearInterval(checkPopup);
          window.removeEventListener('message', messageHandler);
        }
      }, 1000);
    } else {
      this.error.set('Popup blocked. Please allow popups for this site.');
    }
  }
}
