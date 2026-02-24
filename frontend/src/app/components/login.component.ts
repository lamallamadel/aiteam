import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';
import { MfaChallengeComponent } from './mfa-challenge.component';

interface LoginResponse {
  mfaRequired?: boolean;
  mfaToken?: string;
  accessToken?: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, MfaChallengeComponent],
  template: `
    <div class="login-container">
      @if (!showMfaChallenge()) {
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
              <div class="forgot-password-link">
                <a routerLink="/auth/forgot-password">Forgot password?</a>
              </div>
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
              <svg class="oauth-icon" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.17 6.839 9.49.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.603-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.463-1.11-1.463-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.831.092-.646.35-1.086.636-1.336-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0112 6.836c.85.004 1.705.114 2.504.336 1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.137 20.167 22 16.418 22 12c0-5.523-4.477-10-10-10z"/>
              </svg>
              <span>GitHub</span>
            </button>
            <button class="oauth-btn google" (click)="loginWithOAuth2('google')" aria-label="Sign in with Google">
              <svg class="oauth-icon" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
              </svg>
              <span>Google</span>
            </button>
            <button class="oauth-btn gitlab" (click)="loginWithOAuth2('gitlab')" aria-label="Sign in with GitLab">
              <svg class="oauth-icon" viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
                <path d="M23.546 10.93L13.067.452a1.06 1.06 0 00-1.497 0L.545 10.93c-.728.727-.728 1.908 0 2.636l10.48 10.48a1.06 1.06 0 001.497 0l10.48-10.48a1.868 1.868 0 00-.456-2.636zm-11.05 10.648L2.182 11.266l3.614-3.614 6.7 6.7 6.7-6.7 3.614 3.614-10.315 10.312z" fill="#FC6D26"/>
              </svg>
              <span>GitLab</span>
            </button>
          </div>

          <div class="register-link">
            Don't have an account? <a routerLink="/auth/register">Sign up</a>
          </div>
        </div>
      } @else {
        <app-mfa-challenge 
          [mfaToken]="mfaToken()" 
          (backToLogin)="onBackToLogin()"
        />
      }
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
      width: 24px;
      height: 24px;
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

    .forgot-password-link {
      text-align: right;
      margin-top: 6px;
    }

    .forgot-password-link a {
      color: #38bdf8;
      text-decoration: none;
      font-size: 0.85rem;
      font-weight: 500;
    }

    .forgot-password-link a:hover {
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
  showMfaChallenge = signal(false);
  mfaToken = signal('');

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private router: Router,
    private toastService: ToastService
  ) {}

  onSubmit(): void {
    if (!this.username || !this.password) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.http.post<LoginResponse>('/api/auth/login', {
      username: this.username,
      password: this.password
    }).subscribe({
      next: (response) => {
        this.loading.set(false);
        
        if (response.mfaRequired && response.mfaToken) {
          this.mfaToken.set(response.mfaToken);
          this.showMfaChallenge.set(true);
        } else if (response.accessToken && response.refreshToken) {
          this.authService.storeTokens(response.accessToken, response.refreshToken);
          this.toastService.show('Login successful', 'success');
          this.router.navigate(['/dashboard']);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set('Invalid username or password');
        this.toastService.show('Invalid username or password', 'error');
      }
    });
  }

  onBackToLogin(): void {
    this.showMfaChallenge.set(false);
    this.mfaToken.set('');
    this.password = '';
    this.error.set(null);
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
      this.toastService.show('Popup blocked. Please allow popups for this site.', 'error');
    }
  }
}
