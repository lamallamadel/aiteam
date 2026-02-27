import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { OrchestratorService } from '../services/orchestrator.service';
import { AuthService } from '../services/auth.service';
import { CurrentUserDto } from '../models/orchestrator.model';

@Component({
  selector: 'app-user-profile',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="profile-container">
      <div class="header">
        <h2>User Profile</h2>
        <p class="subtitle">Manage your account information and preferences</p>
      </div>

      @if (loading()) {
        <div class="loading-state">
          <div class="spinner"></div>
          <p>Loading profile...</p>
        </div>
      } @else if (error()) {
        <div class="error-state">
          <div class="error-icon">⚠️</div>
          <h3>Error Loading Profile</h3>
          <p>{{ error() }}</p>
          <button class="btn-primary" (click)="loadUserProfile()">Retry</button>
        </div>
      } @else if (user()) {
        <div class="profile-content">
          <div class="profile-card">
            <div class="profile-header">
              <div class="avatar">{{ getUserInitials() }}</div>
              <div class="user-info">
                <div class="username">{{ user()?.username }}</div>
                <div class="user-id">ID: {{ user()?.id }}</div>
              </div>
            </div>

            <div class="profile-section">
              <h3>Contact Information</h3>
              <div class="info-grid">
                <div class="info-item">
                  <span class="label">Email</span>
                  <span class="value">{{ user()?.email }}</span>
                </div>
                <div class="info-item">
                  <span class="label">Username</span>
                  <span class="value monospace">{{ user()?.username }}</span>
                </div>
              </div>
            </div>

            <div class="profile-section">
              <h3>Account Status</h3>
              <div class="status-grid">
                <div class="status-item">
                  <span class="label">Account Status</span>
                  <span class="status-badge" [class.enabled]="user()?.enabled" [class.disabled]="!user()?.enabled">
                    {{ user()?.enabled ? 'Enabled' : 'Disabled' }}
                  </span>
                </div>
                <div class="status-item">
                  <span class="label">Lock Status</span>
                  <span class="status-badge" [class.unlocked]="!user()?.locked" [class.locked]="user()?.locked">
                    {{ user()?.locked ? 'Locked' : 'Unlocked' }}
                  </span>
                </div>
              </div>
            </div>

            <div class="profile-section">
              <h3>Roles</h3>
              @if (user()?.roles && user()!.roles.length > 0) {
                <div class="badges-container">
                  @for (role of user()?.roles; track role) {
                    <span class="role-badge">{{ role }}</span>
                  }
                </div>
              } @else {
                <p class="empty-message">No roles assigned</p>
              }
            </div>

            <div class="profile-section">
              <h3>Permissions</h3>
              @if (user()?.permissions && user()!.permissions.length > 0) {
                <div class="permissions-list">
                  @for (permission of user()?.permissions; track permission) {
                    <div class="permission-item">
                      <span class="permission-icon">✓</span>
                      <span class="permission-name">{{ permission }}</span>
                    </div>
                  }
                </div>
              } @else {
                <p class="empty-message">No permissions assigned</p>
              }
            </div>

            <div class="profile-section">
              <h3>Security</h3>
              <div class="security-card" (click)="navigateToMfaSettings()">
                <div class="security-card-content">
                  <div class="security-icon">🔐</div>
                  <div class="security-info">
                    <h4>Two-Factor Authentication</h4>
                    <p>
                      @if (user()?.mfaEnabled) {
                        <span class="status-badge enabled">Enabled</span>
                      } @else {
                        <span class="status-badge disabled">Not Enabled</span>
                      }
                    </p>
                    <p class="security-description">
                      Add an extra layer of security to your account
                    </p>
                  </div>
                  <div class="security-action">
                    <span class="arrow">→</span>
                  </div>
                </div>
              </div>
            </div>

            <div class="profile-actions">
              <button class="btn-logout" (click)="logout()">
                <span class="logout-icon">🚪</span>
                Logout
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .profile-container {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 24px;
      height: 100%;
      overflow-y: auto;
    }

    .header h2 {
      font-size: 1.8rem;
      margin-bottom: 4px;
    }

    .subtitle {
      color: rgba(255,255,255,0.5);
      font-size: 0.9rem;
      margin: 0;
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
      border: 3px solid rgba(255,255,255,0.1);
      border-top-color: var(--accent-active);
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .error-state {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
    }

    .error-icon {
      font-size: 3rem;
    }

    .error-state h3 {
      color: #ef4444;
      margin: 0;
    }

    .error-state p {
      color: rgba(255,255,255,0.6);
      margin: 8px 0 16px 0;
    }

    .profile-content {
      flex: 1;
    }

    .profile-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 32px;
    }

    .profile-header {
      display: flex;
      align-items: center;
      gap: 20px;
      padding-bottom: 24px;
      border-bottom: 1px solid var(--border);
    }

    .avatar {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--accent-active), #8b5cf6);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 2rem;
      font-weight: 700;
      color: white;
      flex-shrink: 0;
    }

    .user-info {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .username {
      font-size: 1.5rem;
      font-weight: 700;
      font-family: var(--font-mono);
      color: white;
    }

    .user-id {
      font-size: 0.8rem;
      color: rgba(255,255,255,0.4);
      font-family: var(--font-mono);
    }

    .profile-section {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .profile-section h3 {
      font-size: 1rem;
      margin: 0;
      color: rgba(255,255,255,0.8);
    }

    .info-grid, .status-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 16px;
    }

    .info-item, .status-item {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding: 16px;
      background: rgba(255,255,255,0.03);
      border: 1px solid var(--border);
      border-radius: 8px;
    }

    .label {
      font-size: 0.75rem;
      color: rgba(255,255,255,0.5);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      font-weight: 600;
    }

    .value {
      font-size: 1rem;
      color: white;
      word-break: break-all;
    }

    .value.monospace {
      font-family: var(--font-mono);
    }

    .monospace {
      font-family: var(--font-mono);
    }

    .status-badge {
      padding: 6px 12px;
      border-radius: 8px;
      font-size: 0.8rem;
      font-weight: 700;
      text-transform: uppercase;
      width: fit-content;
    }

    .status-badge.enabled {
      background: rgba(34,197,94,0.15);
      color: #22c55e;
    }

    .status-badge.disabled {
      background: rgba(239,68,68,0.15);
      color: #ef4444;
    }

    .status-badge.unlocked {
      background: rgba(34,197,94,0.15);
      color: #22c55e;
    }

    .status-badge.locked {
      background: rgba(239,68,68,0.15);
      color: #ef4444;
    }

    .badges-container {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .role-badge {
      padding: 8px 16px;
      background: var(--accent-active);
      color: white;
      border-radius: 8px;
      font-size: 0.85rem;
      font-weight: 600;
      text-transform: uppercase;
    }

    .permissions-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .permission-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 14px;
      background: rgba(255,255,255,0.03);
      border: 1px solid var(--border);
      border-radius: 8px;
      transition: background 0.2s;
    }

    .permission-item:hover {
      background: rgba(255,255,255,0.06);
    }

    .permission-icon {
      color: var(--accent-active);
      font-weight: 700;
    }

    .permission-name {
      font-size: 0.9rem;
      color: rgba(255,255,255,0.9);
    }

    .empty-message {
      color: rgba(255,255,255,0.4);
      font-size: 0.9rem;
      margin: 0;
      padding: 16px;
      background: rgba(255,255,255,0.02);
      border: 1px solid var(--border);
      border-radius: 8px;
      text-align: center;
    }

    .profile-actions {
      display: flex;
      justify-content: flex-end;
      padding-top: 16px;
      border-top: 1px solid var(--border);
    }

    .btn-logout {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 24px;
      background: rgba(239,68,68,0.1);
      border: 1px solid rgba(239,68,68,0.3);
      border-radius: 8px;
      color: #ef4444;
      font-weight: 600;
      font-size: 0.9rem;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-logout:hover {
      background: rgba(239,68,68,0.2);
      border-color: #ef4444;
    }

    .logout-icon {
      font-size: 1.1rem;
    }

    .btn-primary {
      padding: 10px 20px;
      background: var(--accent-active);
      border: none;
      border-radius: 8px;
      color: white;
      font-weight: 600;
      font-size: 0.85rem;
      cursor: pointer;
      transition: opacity 0.2s;
    }

    .btn-primary:hover {
      opacity: 0.85;
    }

    .security-card {
      padding: 20px;
      background: rgba(255, 255, 255, 0.03);
      border: 1px solid var(--border);
      border-radius: 12px;
      cursor: pointer;
      transition: all 0.2s;
    }

    .security-card:hover {
      background: rgba(255, 255, 255, 0.06);
      border-color: var(--accent-active);
      transform: translateY(-2px);
    }

    .security-card-content {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .security-icon {
      font-size: 2.5rem;
      flex-shrink: 0;
    }

    .security-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .security-info h4 {
      margin: 0;
      font-size: 1rem;
      color: white;
    }

    .security-info p {
      margin: 0;
    }

    .security-description {
      font-size: 0.85rem;
      color: rgba(255, 255, 255, 0.5);
    }

    .security-action {
      flex-shrink: 0;
    }

    .arrow {
      font-size: 1.5rem;
      color: var(--accent-active);
      transition: transform 0.2s;
    }

    .security-card:hover .arrow {
      transform: translateX(4px);
    }
  `]
})
export class UserProfileComponent implements OnInit {
  user = signal<CurrentUserDto | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(
    private orchestratorService: OrchestratorService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadUserProfile();
  }

  loadUserProfile(): void {
    this.loading.set(true);
    this.error.set(null);

    this.orchestratorService.getCurrentUser().subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.message || 'Failed to load user profile');
        this.loading.set(false);
      }
    });
  }

  getUserInitials(): string {
    const username = this.user()?.username || '';
    if (username.length === 0) return '?';
    if (username.length === 1) return username.toUpperCase();
    return username.substring(0, 2).toUpperCase();
  }

  navigateToMfaSettings(): void {
    this.router.navigate(['/settings/mfa']);
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/auth/login']);
      },
      error: () => {
        this.router.navigate(['/auth/login']);
      }
    });
  }
}
