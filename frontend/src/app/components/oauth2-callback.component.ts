import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-oauth2-callback',
  standalone: true,
  template: `
    <div class="callback-container">
      <div class="spinner-wrapper">
        <div class="spinner"></div>
        <p>{{ message }}</p>
      </div>
    </div>
  `,
  styles: [`
    .callback-container {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100vh;
      background: var(--background);
    }

    .spinner-wrapper {
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 20px;
    }

    .spinner {
      width: 50px;
      height: 50px;
      border: 4px solid rgba(255, 255, 255, 0.1);
      border-top-color: var(--accent-active);
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    p {
      color: rgba(255, 255, 255, 0.7);
      font-size: 0.9rem;
    }
  `]
})
export class OAuth2CallbackComponent implements OnInit {
  message = 'Processing OAuth2 callback...';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      const token = params['token'];
      const refreshToken = params['refreshToken'];
      const error = params['error'];
      const errorDescription = params['error_description'];

      if (error) {
        this.message = `Authentication failed: ${errorDescription || error}`;
        setTimeout(() => {
          this.router.navigate(['/settings']);
        }, 2000);
        return;
      }

      if (token && refreshToken) {
        this.authService.storeTokens(token, refreshToken);
        this.message = 'Authentication successful! Redirecting...';
        
        if (window.opener) {
          window.opener.postMessage(
            { type: 'oauth2-success', accessToken: token },
            window.location.origin
          );
          window.close();
        } else {
          this.router.navigate(['/dashboard']);
        }
      } else {
        this.message = 'Invalid OAuth2 response';
        setTimeout(() => {
          this.router.navigate(['/settings']);
        }, 2000);
      }
    });
  }
}
