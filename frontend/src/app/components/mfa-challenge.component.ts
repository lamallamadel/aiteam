import { Component, Input, Output, EventEmitter, signal, ViewChildren, QueryList, ElementRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ToastService } from '../services/toast.service';
import { AuthService } from '../services/auth.service';
import { Router } from '@angular/router';

interface MfaVerifyRequest {
  mfaToken: string;
  code: string;
}

interface MfaVerifyResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

@Component({
  selector: 'app-mfa-challenge',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="mfa-challenge-container">
      <div class="mfa-challenge-card">
        <div class="header">
          <div class="icon">🔐</div>
          <h2>Two-Factor Authentication</h2>
          <p>Enter the 6-digit code from your authenticator app</p>
        </div>

        <div class="code-inputs">
          @for (digit of digits; track $index; let i = $index) {
            <input
              #codeInput
              type="text"
              class="code-digit"
              [class.filled]="digit"
              [class.error]="error()"
              maxlength="1"
              pattern="[0-9]"
              [(ngModel)]="digits[i]"
              [name]="'digit' + i"
              (input)="onDigitInput($event, i)"
              (keydown)="onKeyDown($event, i)"
              (paste)="onPaste($event, i)"
              [disabled]="verifying()"
              autocomplete="off"
              inputmode="numeric"
            />
          }
        </div>

        @if (error()) {
          <div class="error-message">
            {{ error() }}
          </div>
        }

        @if (verifying()) {
          <div class="verifying-state">
            <span class="spinner-small"></span>
            <span>Verifying...</span>
          </div>
        }

        <button class="btn-back" (click)="onBack()" [disabled]="verifying()">
          ← Back to login
        </button>
      </div>
    </div>
  `,
  styles: [`
    .mfa-challenge-container {
      width: 100%;
      display: flex;
      justify-content: center;
      align-items: center;
    }

    .mfa-challenge-card {
      width: 100%;
      max-width: 440px;
      background: var(--surface);
      border-radius: 16px;
      padding: 40px 32px;
      border: 1px solid var(--border);
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }

    .header {
      text-align: center;
      margin-bottom: 32px;
    }

    .icon {
      font-size: 3rem;
      margin-bottom: 16px;
    }

    .header h2 {
      margin: 0 0 8px 0;
      font-size: 1.5rem;
      color: white;
    }

    .header p {
      margin: 0;
      color: rgba(255, 255, 255, 0.6);
      font-size: 0.9rem;
    }

    .code-inputs {
      display: grid;
      grid-template-columns: repeat(6, 1fr);
      gap: 12px;
      margin-bottom: 24px;
    }

    .code-digit {
      width: 100%;
      aspect-ratio: 1;
      padding: 0;
      background: rgba(0, 0, 0, 0.3);
      border: 2px solid var(--border);
      border-radius: 12px;
      color: white;
      font-size: 1.75rem;
      font-family: var(--font-mono);
      font-weight: 700;
      text-align: center;
      outline: none;
      transition: all 0.2s;
    }

    .code-digit:focus {
      border-color: var(--accent-active);
      background: rgba(0, 0, 0, 0.5);
      box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.1);
    }

    .code-digit.filled {
      border-color: var(--accent-active);
      background: rgba(56, 189, 248, 0.05);
    }

    .code-digit.error {
      border-color: #ef4444;
      animation: shake 0.3s;
    }

    @keyframes shake {
      0%, 100% { transform: translateX(0); }
      25% { transform: translateX(-4px); }
      75% { transform: translateX(4px); }
    }

    .code-digit:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .verifying-state {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      padding: 16px;
      background: rgba(56, 189, 248, 0.05);
      border: 1px solid rgba(56, 189, 248, 0.2);
      border-radius: 8px;
      color: var(--accent-active);
      font-size: 0.9rem;
      margin-bottom: 16px;
    }

    .btn-back {
      width: 100%;
      padding: 12px 20px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: rgba(255, 255, 255, 0.7);
      font-size: 0.9rem;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-back:hover:not(:disabled) {
      background: rgba(255, 255, 255, 0.06);
      color: white;
    }

    .btn-back:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    @media (max-width: 480px) {
      .mfa-challenge-card {
        padding: 32px 24px;
      }

      .code-inputs {
        gap: 8px;
      }

      .code-digit {
        font-size: 1.5rem;
      }
    }
  `]
})
export class MfaChallengeComponent implements AfterViewInit {
  @Input() mfaToken = '';
  @Output() backToLogin = new EventEmitter<void>();
  @ViewChildren('codeInput') codeInputs!: QueryList<ElementRef<HTMLInputElement>>;

  digits: string[] = ['', '', '', '', '', ''];
  verifying = signal(false);
  error = signal<string | null>(null);

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private router: Router,
    private toastService: ToastService
  ) {}

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.codeInputs.first?.nativeElement.focus();
    }, 100);
  }

  onDigitInput(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const value = input.value;

    if (!/^\d*$/.test(value)) {
      input.value = '';
      this.digits[index] = '';
      return;
    }

    if (value.length > 1) {
      input.value = value[0];
      this.digits[index] = value[0];
    }

    if (value && index < 5) {
      const inputs = this.codeInputs.toArray();
      inputs[index + 1]?.nativeElement.focus();
    }

    if (this.isCodeComplete()) {
      this.verifyCode();
    }

    this.error.set(null);
  }

  onKeyDown(event: KeyboardEvent, index: number): void {
    if (event.key === 'Backspace' && !this.digits[index] && index > 0) {
      const inputs = this.codeInputs.toArray();
      inputs[index - 1]?.nativeElement.focus();
    }

    if (event.key === 'ArrowLeft' && index > 0) {
      const inputs = this.codeInputs.toArray();
      inputs[index - 1]?.nativeElement.focus();
    }

    if (event.key === 'ArrowRight' && index < 5) {
      const inputs = this.codeInputs.toArray();
      inputs[index + 1]?.nativeElement.focus();
    }
  }

  onPaste(event: ClipboardEvent, index: number): void {
    event.preventDefault();
    const pastedData = event.clipboardData?.getData('text') || '';
    const digits = pastedData.replace(/\D/g, '').split('').slice(0, 6);

    digits.forEach((digit, i) => {
      if (index + i < 6) {
        this.digits[index + i] = digit;
      }
    });

    const inputs = this.codeInputs.toArray();
    const lastFilledIndex = Math.min(index + digits.length - 1, 5);
    inputs[lastFilledIndex]?.nativeElement.focus();

    if (this.isCodeComplete()) {
      this.verifyCode();
    }
  }

  isCodeComplete(): boolean {
    return this.digits.every(d => d.length === 1);
  }

  verifyCode(): void {
    if (!this.isCodeComplete() || !this.mfaToken) {
      return;
    }

    this.verifying.set(true);
    this.error.set(null);

    const code = this.digits.join('');
    const request: MfaVerifyRequest = {
      mfaToken: this.mfaToken,
      code: code
    };

    this.http.post<MfaVerifyResponse>('/api/auth/mfa/verify', request).subscribe({
      next: (response) => {
        this.verifying.set(false);
        this.authService.storeTokens(response.accessToken, response.refreshToken);
        this.toastService.show('Login successful', 'success');
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.verifying.set(false);
        const errorMsg = err.error?.message || 'Invalid verification code';
        this.error.set(errorMsg);
        this.toastService.show(errorMsg, 'error');
        this.clearCode();
        const inputs = this.codeInputs.toArray();
        inputs[0]?.nativeElement.focus();
      }
    });
  }

  clearCode(): void {
    this.digits = ['', '', '', '', '', ''];
  }

  onBack(): void {
    this.backToLogin.emit();
  }
}
