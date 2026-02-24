import { Component, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-password-strength',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="password-strength-container">
      <div class="strength-bar">
        <div 
          *ngFor="let segment of [0, 1, 2, 3]" 
          class="strength-segment"
          [class.active]="segment < score()"
          [class.weak]="segment < score() && score() === 1"
          [class.fair]="segment < score() && score() === 2"
          [class.good]="segment < score() && score() === 3"
          [class.strong]="segment < score() && score() === 4">
        </div>
      </div>
      <div class="strength-label" [class]="labelClass()">
        {{ label() }}
      </div>
    </div>
  `,
  styles: [`
    .password-strength-container {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .strength-bar {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 4px;
      height: 4px;
    }

    .strength-segment {
      background: rgba(255, 255, 255, 0.1);
      border-radius: 2px;
      transition: background-color 0.3s ease;
    }

    .strength-segment.active.weak {
      background: #ef4444;
    }

    .strength-segment.active.fair {
      background: #f97316;
    }

    .strength-segment.active.good {
      background: #eab308;
    }

    .strength-segment.active.strong {
      background: #22c55e;
    }

    .strength-label {
      font-size: 0.75rem;
      font-weight: 600;
      transition: color 0.3s ease;
    }

    .strength-label.weak {
      color: #ef4444;
    }

    .strength-label.fair {
      color: #f97316;
    }

    .strength-label.good {
      color: #eab308;
    }

    .strength-label.strong {
      color: #22c55e;
    }

    .strength-label.empty {
      color: rgba(255, 255, 255, 0.3);
    }
  `]
})
export class PasswordStrengthComponent {
  password = input.required<string>();

  score = computed(() => {
    const pwd = this.password();
    if (!pwd) return 0;

    let strength = 0;

    if (pwd.length >= 12) strength++;
    if (/[A-Z]/.test(pwd)) strength++;
    if (/[a-z]/.test(pwd)) strength++;
    if (/\d/.test(pwd)) strength++;
    if (/[^A-Za-z0-9]/.test(pwd)) strength++;

    return Math.min(strength, 4);
  });

  label = computed(() => {
    const s = this.score();
    if (s === 0) return 'Enter password';
    if (s === 1) return 'Weak';
    if (s === 2) return 'Fair';
    if (s === 3) return 'Good';
    return 'Strong';
  });

  labelClass = computed(() => {
    const s = this.score();
    if (s === 0) return 'empty';
    if (s === 1) return 'weak';
    if (s === 2) return 'fair';
    if (s === 3) return 'good';
    return 'strong';
  });
}
