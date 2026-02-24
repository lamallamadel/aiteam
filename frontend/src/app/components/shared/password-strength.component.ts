import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface PasswordStrength {
  score: number;
  label: string;
  color: string;
  feedback: string[];
}

@Component({
  selector: 'app-password-strength',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (password) {
      <div class="password-strength">
        <div class="strength-bar">
          <div class="strength-fill" [style.width.%]="strength.score * 25" [style.background-color]="strength.color"></div>
        </div>
        <div class="strength-label" [style.color]="strength.color">
          {{ strength.label }}
        </div>
        @if (strength.feedback.length > 0) {
          <ul class="strength-feedback">
            @for (item of strength.feedback; track item) {
              <li>{{ item }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
  styles: [`
    .password-strength {
      margin-top: 8px;
    }

    .strength-bar {
      height: 4px;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 2px;
      overflow: hidden;
      margin-bottom: 8px;
    }

    .strength-fill {
      height: 100%;
      transition: width 0.3s ease, background-color 0.3s ease;
      border-radius: 2px;
    }

    .strength-label {
      font-size: 0.8rem;
      font-weight: 600;
      margin-bottom: 4px;
    }

    .strength-feedback {
      margin: 8px 0 0 0;
      padding: 0 0 0 20px;
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.6);
    }

    .strength-feedback li {
      margin-bottom: 4px;
    }
  `]
})
export class PasswordStrengthComponent implements OnChanges {
  @Input() password: string = '';

  strength: PasswordStrength = {
    score: 0,
    label: '',
    color: '',
    feedback: []
  };

  ngOnChanges(): void {
    this.strength = this.calculateStrength(this.password);
  }

  private calculateStrength(password: string): PasswordStrength {
    if (!password) {
      return { score: 0, label: '', color: '', feedback: [] };
    }

    let score = 0;
    const feedback: string[] = [];

    if (password.length >= 8) {
      score++;
    } else {
      feedback.push('Use at least 8 characters');
    }

    if (password.length >= 12) {
      score++;
    }

    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) {
      score++;
    } else {
      feedback.push('Mix uppercase and lowercase letters');
    }

    if (/\d/.test(password)) {
      score++;
    } else {
      feedback.push('Include at least one number');
    }

    if (/[^A-Za-z0-9]/.test(password)) {
      score++;
    } else {
      feedback.push('Include at least one special character');
    }

    const commonPatterns = ['password', '12345', 'qwerty', 'abc123', 'letmein'];
    if (commonPatterns.some(pattern => password.toLowerCase().includes(pattern))) {
      score = Math.max(0, score - 2);
      feedback.push('Avoid common passwords');
    }

    let label = '';
    let color = '';

    if (score <= 1) {
      label = 'Weak';
      color = '#ef4444';
    } else if (score === 2) {
      label = 'Fair';
      color = '#f97316';
    } else if (score === 3) {
      label = 'Good';
      color = '#eab308';
    } else if (score === 4) {
      label = 'Strong';
      color = '#22c55e';
    } else {
      label = 'Very Strong';
      color = '#10b981';
    }

    return { score, label, color, feedback };
  }
}
