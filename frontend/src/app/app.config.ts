import { ApplicationConfig, provideBrowserGlobalErrorListeners, APP_INITIALIZER } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './interceptors/auth.interceptor';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';

export function initializeCsrf(authService: AuthService): () => Promise<void> {
  return () => firstValueFrom(authService.fetchCsrfToken())
    .then(() => {
      console.log('CSRF initialized');
    })
    .catch((err) => {
      console.warn('CSRF initialization failed (this is normal if server is not reachable or first run):', err);
    });
}

export function initializeTheme(themeService: ThemeService): () => void {
  return () => themeService.initialize();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeCsrf,
      deps: [AuthService],
      multi: true
    },
    {
      provide: APP_INITIALIZER,
      useFactory: initializeTheme,
      deps: [ThemeService],
      multi: true
    }
  ]
};
