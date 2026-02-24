import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../services/toast.service';

interface MfaSetupResponse {
  secret: string;
  otpAuthUrl: string;
}

@Component({
  selector: 'app-mfa-setup',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="mfa-setup-container">
      <div class="mfa-setup-card">
        <div class="header">
          <h1>Enable Two-Factor Authentication</h1>
          <p>Secure your account with an additional layer of protection</p>
        </div>

        @if (loading()) {
          <div class="loading-state">
            <div class="spinner"></div>
            <p>Initializing MFA setup...</p>
          </div>
        } @else if (error()) {
          <div class="error-state">
            <div class="error-icon">⚠️</div>
            <p>{{ error() }}</p>
            <button class="btn-primary" (click)="initializeSetup()">Retry</button>
          </div>
        } @else if (setupData()) {
          <div class="setup-content">
            @if (!verified()) {
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <h3>Scan QR Code</h3>
                  <p>Use your authenticator app (Google Authenticator, Authy, etc.) to scan this QR code:</p>
                  <div class="qr-code-container">
                    <img 
                      [src]="getQRCodeUrl()" 
                      alt="QR Code for 2FA Setup" 
                      class="qr-code"
                    />
                  </div>
                </div>
              </div>

              <div class="divider">
                <span>OR</span>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <h3>Manual Entry</h3>
                  <p>If you can't scan the QR code, enter this secret key manually:</p>
                  <div class="secret-display">
                    <code class="secret-code">{{ setupData()!.secret }}</code>
                    <button class="btn-copy" (click)="copySecret()" aria-label="Copy secret">
                      {{ copied() ? '✓ Copied' : '📋 Copy' }}
                    </button>
                  </div>
                </div>
              </div>

              <div class="divider"></div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <h3>Verify Setup</h3>
                  <p>Enter the 6-digit code from your authenticator app:</p>
                  
                  <form (ngSubmit)="verifySetup()" class="verify-form">
                    <div class="form-group">
                      <input
                        type="text"
                        [(ngModel)]="verificationCode"
                        name="verificationCode"
                        class="input-field code-input"
                        placeholder="000000"
                        maxlength="6"
                        pattern="[0-9]{6}"
                        [disabled]="verifying()"
                        required
                        autocomplete="off"
                      />
                    </div>

                    @if (verifyError()) {
                      <div class="error-message">
                        {{ verifyError() }}
                      </div>
                    }

                    <button
                      type="submit"
                      class="btn-primary"
                      [disabled]="verifying() || verificationCode.length !== 6"
                    >
                      @if (verifying()) {
                        <span class="spinner-small"></span>
                        <span>Verifying...</span>
                      } @else {
                        <span>Verify & Enable</span>
                      }
                    </button>
                  </form>
                </div>
              </div>
            } @else {
              <div class="success-state">
                <div class="success-icon">✓</div>
                <h3>Two-Factor Authentication Enabled!</h3>
                <p>Your account is now protected with 2FA</p>
                <button class="btn-primary" routerLink="/profile">
                  Back to Profile
                </button>
              </div>
            }

            <div class="back-link">
              <a routerLink="/profile">← Back to Profile</a>
            </div>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .mfa-setup-container {
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      background: var(--background);
      padding: 20px;
    }

    .mfa-setup-card {
      width: 100%;
      max-width: 600px;
      background: var(--surface);
      border-radius: 16px;
      padding: 48px 40px;
      border: 1px solid var(--border);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    .header {
      text-align: center;
      margin-bottom: 32px;
    }

    .header h1 {
      margin: 0 0 8px 0;
      font-size: 1.75rem;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .header p {
      margin: 0;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
    }

    .loading-state, .error-state, .success-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 40px 20px;
      gap: 16px;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(255,255,255,0.1);
      border-top-color: var(--accent-active);
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .error-state {
      background: rgba(239, 68, 68, 0.05);
      border: 1px solid rgba(239, 68, 68, 0.2);
      border-radius: 12px;
    }

    .error-icon {
      font-size: 3rem;
    }

    .error-state p {
      color: #ef4444;
      margin: 0;
      text-align: center;
    }

    .success-state {
      background: rgba(34, 197, 94, 0.05);
      border: 1px solid rgba(34, 197, 94, 0.2);
      border-radius: 12px;
    }

    .success-icon {
      font-size: 4rem;
      color: #22c55e;
      background: rgba(34, 197, 94, 0.1);
      width: 80px;
      height: 80px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .success-state h3 {
      color: #22c55e;
      margin: 0;
      font-size: 1.5rem;
    }

    .success-state p {
      color: rgba(255, 255, 255, 0.6);
      margin: 0;
    }

    .setup-content {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .step {
      display: flex;
      gap: 20px;
    }

    .step-number {
      flex-shrink: 0;
      width: 32px;
      height: 32px;
      border-radius: 50%;
      background: var(--accent-active);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 700;
      font-size: 1rem;
    }

    .step-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .step-content h3 {
      font-size: 1.1rem;
      margin: 0;
      color: white;
    }

    .step-content p {
      margin: 0;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
      line-height: 1.5;
    }

    .qr-code-container {
      display: flex;
      justify-content: center;
      padding: 20px;
      background: white;
      border-radius: 12px;
      margin-top: 8px;
    }

    .qr-code {
      max-width: 250px;
      width: 100%;
      height: auto;
    }

    .divider {
      display: flex;
      align-items: center;
      margin: 8px 0;
      text-align: center;
      color: rgba(255, 255, 255, 0.3);
      font-size: 0.85rem;
    }

    .divider::before,
    .divider::after {
      content: '';
      flex: 1;
      border-bottom: 1px solid var(--border);
    }

    .divider span {
      padding: 0 16px;
    }

    .secret-display {
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 16px;
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid var(--border);
      border-radius: 8px;
      margin-top: 8px;
    }

    .secret-code {
      flex: 1;
      font-family: var(--font-mono);
      font-size: 1rem;
      color: var(--accent-active);
      word-break: break-all;
    }

    .btn-copy {
      padding: 8px 16px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--border);
      border-radius: 6px;
      color: white;
      font-size: 0.85rem;
      cursor: pointer;
      transition: all 0.2s;
      white-space: nowrap;
    }

    .btn-copy:hover {
      background: rgba(255, 255, 255, 0.1);
    }

    .verify-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
      margin-top: 8px;
    }

    .code-input {
      text-align: center;
      font-size: 1.5rem;
      font-family: var(--font-mono);
      letter-spacing: 0.5em;
      padding: 16px;
    }

    .back-link {
      text-align: center;
      margin-top: 16px;
    }

    .back-link a {
      color: var(--accent-active);
      text-decoration: none;
      font-size: 0.9rem;
      font-weight: 500;
    }

    .back-link a:hover {
      text-decoration: underline;
    }

    @media (max-width: 480px) {
      .mfa-setup-card {
        padding: 32px 24px;
      }

      .step {
        flex-direction: column;
        gap: 12px;
      }

      .secret-display {
        flex-direction: column;
        align-items: stretch;
      }
    }
  `]
})
export class MfaSetupComponent implements OnInit {
  setupData = signal<MfaSetupResponse | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);
  verificationCode = '';
  verifying = signal(false);
  verifyError = signal<string | null>(null);
  verified = signal(false);
  copied = signal(false);

  constructor(
    private http: HttpClient,
    private router: Router,
    private toastService: ToastService
  ) {}

  ngOnInit(): void {
    this.initializeSetup();
  }

  initializeSetup(): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.post<MfaSetupResponse>('/api/auth/mfa/setup', {}).subscribe({
      next: (response) => {
        this.setupData.set(response);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Failed to initialize MFA setup');
        this.loading.set(false);
        this.toastService.show('Failed to initialize MFA setup', 'error');
      }
    });
  }

  getQRCodeUrl(): string {
    const otpAuthUrl = this.setupData()?.otpAuthUrl;
    if (!otpAuthUrl) return '';
    return `https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${encodeURIComponent(otpAuthUrl)}`;
  }

  copySecret(): void {
    const secret = this.setupData()?.secret;
    if (secret) {
      navigator.clipboard.writeText(secret).then(() => {
        this.copied.set(true);
        this.toastService.show('Secret copied to clipboard', 'success');
        setTimeout(() => this.copied.set(false), 2000);
      });
    }
  }

  verifySetup(): void {
    if (this.verificationCode.length !== 6) {
      return;
    }

    this.verifying.set(true);
    this.verifyError.set(null);

    this.http.post('/api/auth/mfa/verify-setup', {
      code: this.verificationCode
    }).subscribe({
      next: () => {
        this.verifying.set(false);
        this.verified.set(true);
        this.toastService.show('Two-factor authentication enabled successfully!', 'success');
      },
      error: (err) => {
        this.verifying.set(false);
        const errorMsg = err.error?.message || 'Invalid verification code';
        this.verifyError.set(errorMsg);
        this.toastService.show(errorMsg, 'error');
        this.verificationCode = '';
      }
    });
  }
}
