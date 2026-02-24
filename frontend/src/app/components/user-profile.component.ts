import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService, UserProfile } from '../services/auth.service';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="profile-container">
      <div class="profile-header">
        <h2>User Profile</h2>
      </div>

      @if (loading()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <p>Loading profile...</p>
        </div>
      }

      @if (error()) {
        <div class="error-state">
          <div class="error-icon">⚠️</div>
          <p>{{ error() }}</p>
          <button class="btn-primary" (click)="loadProfile()">Retry</button>
        </div>
      }

      @if (user()) {
        <div class="profile-content">
          <div class="profile-avatar">
            @if (user()?.avatar) {
              <img [src]="user()?.avatar" [alt]="user()?.username" />
            } @else {
              <div class="avatar-placeholder">
                {{ getInitials(user()!) }}
              </div>
            }
          </div>

          <div class="profile-details">
            <div class="detail-row">
              <label>Username</label>
              <span class="value">{{ user()?.username }}</span>
            </div>

            <div class="detail-row">
              <label>Email</label>
              <span class="value">{{ user()?.email }}</span>
            </div>

            @if (user()?.firstName || user()?.lastName) {
              <div class="detail-row">
                <label>Name</label>
                <span class="value">{{ user()?.firstName }} {{ user()?.lastName }}</span>
              </div>
            }

            <div class="detail-row">
              <label>User ID</label>
              <span class="value code">{{ user()?.id }}</span>
            </div>

            <div class="detail-row">
              <label>Roles</label>
              <div class="roles-list">
                @for (role of user()?.roles; track role) {
                  <span class="role-badge" [class]="getRoleClass(role)">
                    {{ role }}
                  </span>
                }
              </div>
            </div>
          </div>

          <div class="profile-actions">
            <button class="btn-secondary" (click)="logout()">Logout</button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .profile-container {
      padding: 24px;
      max-width: 800px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .profile-header h2 {
      margin: 0;
      font-size: 1.8rem;
      color: white;
    }

    .loading-state, .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 60px 20px;
      gap: 16px;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 3px solid rgba(255, 255, 255, 0.1);
      border-top-color: var(--accent-active);
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .loading-state p, .error-state p {
      color: rgba(255, 255, 255, 0.6);
      margin: 0;
    }

    .error-icon {
      font-size: 3rem;
    }

    .profile-content {
      background: var(--surface);
      border-radius: 12px;
      padding: 32px;
      display: flex;
      flex-direction: column;
      gap: 32px;
      border: 1px solid var(--border);
    }

    .profile-avatar {
      display: flex;
      justify-content: center;
    }

    .profile-avatar img {
      width: 120px;
      height: 120px;
      border-radius: 50%;
      object-fit: cover;
      border: 3px solid var(--accent-active);
    }

    .avatar-placeholder {
      width: 120px;
      height: 120px;
      border-radius: 50%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 3rem;
      font-weight: 700;
      color: white;
      border: 3px solid var(--accent-active);
    }

    .profile-details {
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    .detail-row {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .detail-row label {
      font-size: 0.85rem;
      font-weight: 600;
      color: #94a3b8;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .detail-row .value {
      font-size: 1rem;
      color: white;
    }

    .detail-row .value.code {
      font-family: var(--font-mono);
      font-size: 0.9rem;
      color: #38bdf8;
    }

    .roles-list {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .role-badge {
      padding: 6px 14px;
      border-radius: 16px;
      font-size: 0.8rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .role-badge.admin {
      background: rgba(239, 68, 68, 0.15);
      color: #ef4444;
      border: 1px solid rgba(239, 68, 68, 0.3);
    }

    .role-badge.user {
      background: rgba(56, 189, 248, 0.15);
      color: #38bdf8;
      border: 1px solid rgba(56, 189, 248, 0.3);
    }

    .role-badge.developer {
      background: rgba(34, 197, 94, 0.15);
      color: #22c55e;
      border: 1px solid rgba(34, 197, 94, 0.3);
    }

    .role-badge.default {
      background: rgba(148, 163, 184, 0.15);
      color: #94a3b8;
      border: 1px solid rgba(148, 163, 184, 0.3);
    }

    .profile-actions {
      display: flex;
      gap: 12px;
      justify-content: flex-end;
      padding-top: 16px;
      border-top: 1px solid var(--border);
    }

    .btn-primary, .btn-secondary {
      padding: 10px 24px;
      border: none;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.9rem;
      cursor: pointer;
      transition: opacity 0.2s;
    }

    .btn-primary {
      background: var(--accent-active);
      color: white;
    }

    .btn-secondary {
      background: rgba(255, 255, 255, 0.08);
      color: white;
      border: 1px solid var(--border);
    }

    .btn-primary:hover, .btn-secondary:hover {
      opacity: 0.85;
    }
  `]
})
export class UserProfileComponent implements OnInit {
  user = signal<UserProfile | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(private authService: AuthService) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {
    this.loading.set(true);
    this.error.set(null);

    this.authService.getUserProfile().subscribe({
      next: (profile) => {
        this.user.set(profile);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load user profile');
        this.loading.set(false);
      }
    });
  }

  getInitials(user: UserProfile): string {
    if (user.firstName && user.lastName) {
      return `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase();
    }
    return user.username.substring(0, 2).toUpperCase();
  }

  getRoleClass(role: string): string {
    const roleLower = role.toLowerCase();
    if (roleLower.includes('admin')) return 'admin';
    if (roleLower.includes('developer') || roleLower.includes('dev')) return 'developer';
    if (roleLower.includes('user')) return 'user';
    return 'default';
  }

  logout(): void {
    this.authService.logout().subscribe();
  }
}
