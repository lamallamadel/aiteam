import { inject } from '@angular/core';
import { Router, CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { map, of, take } from 'rxjs';

export const authGuard: CanActivateFn = () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (authService.isAuthenticated()) {
        return true;
    }

    if (authService.hasToken()) {
        return authService.loadUserProfile().pipe(
            take(1),
            map(user => {
                if (user) {
                    return true;
                } else {
                    router.navigate(['/auth/login']);
                    return false;
                }
            })
        );
    }

    if (authService.getRefreshToken()) {
        router.navigate(['/auth/login']);
    } else {
        router.navigate(['/onboarding']);
    }
    return false;
};
