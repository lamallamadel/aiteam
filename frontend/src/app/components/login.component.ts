import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
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
            <div class="error-message" aria-live="polite">
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
          <button class="oauth-btn github" (click)="loginWithOAuth2('github')" aria-label="Sign in with GitHub">
            <span class="oauth-icon">🐙</span>
            <span>GitHub</span>
          </button>
          <button class="oauth-btn google" (click)="loginWithOAuth2('google')" aria-label="Sign in with Google">
            <span class="oauth-icon">📧</span>
            <span>Google</span>
          </button>
          <button class="oauth-btn gitlab" (click)="loginWithOAuth2('gitlab')" aria-label="Sign in with GitLab">
            <span class="oauth-icon">🦊</span>
            <span>GitLab</span>
          </button>
        </div>

        <div class="register-link">
          Don't have an account? <a routerLink="/auth/register">Sign up</a>
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

    @media (max-width: 480px) {
      .login-card {
        padding: 32px 24px;
      }
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
